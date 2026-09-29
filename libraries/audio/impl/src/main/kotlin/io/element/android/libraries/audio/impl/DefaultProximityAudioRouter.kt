/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.libraries.audio.impl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.getSystemService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.audio.api.ProximityAudioRouter
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber

/**
 * VaVsta: держит звук на разговорном динамике, пока телефон у уха.
 *
 * Логика повторяет подход из звонков ([io.element.android.features.call.impl.utils.WebViewAudioManager]),
 * но без блокировки экрана: для голосового сообщения гасить экран не надо.
 *
 * Активные Bluetooth/проводные/USB-гарнитуры не трогаем — если пользователь слушает
 * в наушниках, отбирать у них звук нельзя.
 */
@ContributesBinding(AppScope::class)
class DefaultProximityAudioRouter(
    @ApplicationContext private val context: Context,
) : ProximityAudioRouter {
    private val audioManager = requireNotNull(context.getSystemService<AudioManager>())
    private val sensorManager = context.getSystemService<SensorManager>()
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var sensorListener: SensorEventListener? = null

    /** Сейчас звук идёт через разговорный динамик (переключили мы его). */
    private var isEarpieceEngaged = false

    /** Состояние до переключения — его и вернём в [releaseEarpiece]. */
    private var previousMode: Int? = null

    @Suppress("DEPRECATION")
    private var previousCommunicationDevice: AudioDeviceInfo? = null

    @Suppress("DEPRECATION")
    private var previousSpeakerphoneOn: Boolean? = null

    @Suppress("DEPRECATION")
    private var previousBluetoothScoOn: Boolean? = null

    override fun start() {
        if (sensorListener != null) {
            Timber.d("Proximity: already listening, ignoring start()")
            return
        }
        val manager = sensorManager
        val sensor = proximitySensor
        if (manager == null || sensor == null) {
            Timber.i("Proximity: no proximity sensor on this device, audio stays as is")
            return
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
                val distance = event.values.firstOrNull() ?: return
                val isNear = distance < sensor.maximumRange
                Timber.d("Proximity: distance %s (max %s), near=%s", distance, sensor.maximumRange, isNear)
                if (isNear) engageEarpiece() else releaseEarpiece()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorListener = listener
        runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }.onFailure {
            sensorListener = null
            Timber.e(it, "Proximity: could not register sensor listener")
        }
    }

    override fun stop() {
        sensorListener?.let { listener ->
            runCatching { sensorManager?.unregisterListener(listener) }
                .onFailure { Timber.e(it, "Proximity: could not unregister sensor listener") }
        }
        sensorListener = null
        releaseEarpiece()
    }

    private fun engageEarpiece() {
        if (isEarpieceEngaged) return
        if (isExternalDeviceActive()) {
            Timber.d("Proximity: external audio device is active, not switching to the earpiece")
            return
        }
        val earpiece = findEarpiece()
        if (earpiece == null) {
            Timber.w("Proximity: no built-in earpiece found, not switching audio")
            return
        }

        isEarpieceEngaged = true
        runCatching {
            previousMode = audioManager.mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // «Голосовой режим»: кнопки громкости тоже будут управлять этим звуком.
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                previousCommunicationDevice = audioManager.communicationDevice
                audioManager.setCommunicationDevice(earpiece)
            } else {
                audioManager.mode = AudioManager.MODE_IN_CALL
                previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
                previousBluetoothScoOn = audioManager.isBluetoothScoOn
                audioManager.isSpeakerphoneOn = false
            }
        }.onFailure {
            Timber.e(it, "Proximity: could not switch to the earpiece")
            // Не оставляем «полусломанное» состояние.
            restorePreviousState()
        }
        Timber.d("Proximity: audio switched to the earpiece")
    }

    private fun releaseEarpiece() {
        if (!isEarpieceEngaged) return
        isEarpieceEngaged = false
        runCatching { restorePreviousState() }
            .onFailure { Timber.e(it, "Proximity: could not restore the previous audio state") }
        Timber.d("Proximity: audio back to normal")
    }

    private fun restorePreviousState() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val previousDevice = previousCommunicationDevice
                if (previousDevice != null) {
                    audioManager.setCommunicationDevice(previousDevice)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                previousSpeakerphoneOn?.let { audioManager.isSpeakerphoneOn = it }
                previousBluetoothScoOn?.let { audioManager.isBluetoothScoOn = it }
            }
        }.onFailure { Timber.e(it, "Proximity: could not restore the previous audio device") }

        previousMode?.let { mode -> runCatching { audioManager.mode = mode } }
        previousMode = null
        previousCommunicationDevice = null
        previousSpeakerphoneOn = null
        previousBluetoothScoOn = null
    }

    /** Bluetooth/проводная гарнитура уже забрала звук — не отбираем. */
    @Suppress("DEPRECATION")
    private fun isExternalDeviceActive(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.any { it.type in externalDeviceTypes }
        } else {
            audioManager.isBluetoothScoOn || audioManager.isWiredHeadsetOn
        }
    }

    @Suppress("DEPRECATION")
    private fun findEarpiece(): AudioDeviceInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .find { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
        }
    }

    private companion object {
        val externalDeviceTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
        )
    }
}

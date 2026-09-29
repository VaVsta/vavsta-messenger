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
import android.os.PowerManager
import androidx.core.content.getSystemService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.audio.api.ProximityAudioRouter
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber

/**
 * VaVsta: держит звук на разговорном динамике, пока телефон у уха, и гасит экран.
 *
 * Логика повторяет подход из звонков ([io.element.android.features.call.impl.utils.WebViewAudioManager]):
 * переключение на разговорный динамик + `PROXIMITY_SCREEN_OFF_WAKE_LOCK`, который
 * просит систему погасить экран, пока телефон прижат к голове. Экран возвращается
 * системой сама, когда датчик отпускает.
 *
 * Важно про скорость: перевод приложения в голосовой режим (`MODE_IN_COMMUNICATION`)
 * заставляет аудиоплеер пересоздать выходной поток — на некоторых устройствах это
 * занимает секунды. Поэтому голосовой режим включается ОДИН раз в [start] (до начала
 * воспроизведения), а по датчику приближения дальше только переключается устройство
 * вывода, что происходит без пауз.
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

    /**
     * Гасит экран, пока телефон у уха. Без этого wake lock система не погасит экран:
     * приложение само выключить его не может (для этого нужен `DEVICE_POWER`).
     */
    private val screenOffWakeLock by lazy {
        context.getSystemService<PowerManager>()
            ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "${context.packageName}:ProximityVoiceMessageWakeLock",
            )
    }

    private var sensorListener: SensorEventListener? = null

    /** [start] уже отработал и голосовой режим включён. */
    private var isStarted = false

    /** Состояние до переключения — его и вернём в [restorePreviousState]. */
    private var previousMode: Int? = null

    @Suppress("DEPRECATION")
    private var previousCommunicationDevice: AudioDeviceInfo? = null

    @Suppress("DEPRECATION")
    private var previousSpeakerphoneOn: Boolean? = null

    @Suppress("DEPRECATION")
    private var previousBluetoothScoOn: Boolean? = null

    override fun start() {
        if (isStarted) {
            Timber.d("Proximity: already started, ignoring start()")
            return
        }
        val manager = sensorManager
        val sensor = proximitySensor
        if (manager == null || sensor == null) {
            Timber.i("Proximity: no proximity sensor on this device, audio stays as is")
            return
        }
        if (isExternalDeviceActive()) {
            Timber.d("Proximity: external audio device is active, not touching audio")
            return
        }
        engageVoiceMode()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
                val distance = event.values.firstOrNull() ?: return
                onProximityChanged(isNear = distance < sensor.maximumRange)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorListener = listener
        isStarted = true
        runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }.onFailure {
            sensorListener = null
            isStarted = false
            restorePreviousState()
            Timber.e(it, "Proximity: could not register sensor listener")
        }
    }

    override fun stop() {
        if (!isStarted) return
        sensorListener?.let { listener ->
            runCatching { sensorManager?.unregisterListener(listener) }
                .onFailure { Timber.e(it, "Proximity: could not unregister sensor listener") }
        }
        sensorListener = null
        isStarted = false
        releaseScreenOffWakeLock()
        restorePreviousState()
    }

    /**
     * Датчик сработал: телефон у уха — уводим звук в разговорный динамик и гасим экран,
     * телефон далеко — возвращаем на громкую связь и отпускаем экран.
     */
    private fun onProximityChanged(isNear: Boolean) {
        if (isExternalDeviceActive()) {
            Timber.d("Proximity: external audio device is active, not switching")
            return
        }
        if (isNear) {
            Timber.d("Proximity: phone at the ear, switching to the earpiece")
            selectEarpiece()
            acquireScreenOffWakeLock()
        } else {
            Timber.d("Proximity: phone away from the ear, switching to the loudspeaker")
            selectLoudspeaker()
            releaseScreenOffWakeLock()
        }
    }

    /** Один раз переводим звук в голосовой режим — до старта воспроизведения. */
    private fun engageVoiceMode() {
        runCatching {
            previousMode = audioManager.mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                previousCommunicationDevice = audioManager.communicationDevice
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                // На старте телефон обычно не у уха — сразу ставим громкую связь.
                selectLoudspeaker()
            } else {
                previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
                previousBluetoothScoOn = audioManager.isBluetoothScoOn
                audioManager.mode = AudioManager.MODE_IN_CALL
                audioManager.isSpeakerphoneOn = true
            }
        }.onFailure {
            Timber.e(it, "Proximity: could not engage the voice mode")
            restorePreviousState()
        }
    }

    private fun selectEarpiece() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val earpiece = findEarpiece()
                if (earpiece == null) {
                    Timber.w("Proximity: no built-in earpiece found, not switching audio")
                    return
                }
                audioManager.setCommunicationDevice(earpiece)
            } else {
                audioManager.isSpeakerphoneOn = false
            }
        }.onFailure { Timber.e(it, "Proximity: could not switch to the earpiece") }
    }

    private fun selectLoudspeaker() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = audioManager.availableCommunicationDevices
                    .find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                audioManager.isSpeakerphoneOn = true
            }
        }.onFailure { Timber.e(it, "Proximity: could not switch to the loudspeaker") }
    }

    private fun acquireScreenOffWakeLock() {
        val wakeLock = screenOffWakeLock ?: run {
            Timber.d("Proximity: no PROXIMITY_SCREEN_OFF wake lock on this device, screen stays as is")
            return
        }
        runCatching {
            if (wakeLock.isHeld) return
            @Suppress("WakeLock")
            wakeLock.acquire()
        }.onFailure {
            Timber.e(it, "Proximity: could not acquire the screen off wake lock")
        }
    }

    private fun releaseScreenOffWakeLock() {
        val wakeLock = screenOffWakeLock ?: return
        runCatching {
            if (wakeLock.isHeld) wakeLock.release()
        }.onFailure {
            Timber.e(it, "Proximity: could not release the screen off wake lock")
        }
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

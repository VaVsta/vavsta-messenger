/*
 * VaVsta Messenger (форк Element X).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.audio.impl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import org.robolectric.util.ReflectionHelpers

class DefaultProximityAudioRouterTest : RobolectricTest() {
    private companion object {
        /** Радиус срабатывания датчика приближения, как на настоящих телефонах. */
        private const val NEAR_THRESHOLD_CM = 5f
    }

    @Before
    fun setUp() {
        ShadowPowerManager.clearWakeLocks()
        val powerManager = requireNotNull(application().getSystemService<PowerManager>())
        shadowOf(powerManager).setIsWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, true)

        // Стоковый Robolectric — «пустое» устройство: ни датчика приближения, ни разговорного
        // динамика. Роутер на таком устройстве по замыслу ничего не делает, поэтому для теста
        // изображаем минимальный телефон.
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_PROXIMITY)
        // У стокового сенсора maxRange = 0, а роутер считает «у уха» через «дистанция < maxRange».
        shadowOf(sensor).setMaximumRange(NEAR_THRESHOLD_CM)
        shadowOf(requireNotNull(application().getSystemService<SensorManager>())).addSensor(sensor)

        val earpiece = mockk<AudioDeviceInfo> { every { type } returns AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
        val audioManager = shadowOf(requireNotNull(application().getSystemService<AudioManager>()))
        audioManager.addOutputDevice(earpiece, true)
        // Именно этот список роутер использует, чтобы найти динамик.
        audioManager.addAvailableCommunicationDevice(earpiece, true)
    }

    @Test
    fun `the router holds the screen off wake lock while the phone is at the ear`() {
        val router = DefaultProximityAudioRouter(application())

        router.start()
        sendProximityEvent(isNear = true)

        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        assertThat(wakeLock).isNotNull()
        assertThat(wakeLock!!.isHeld).isTrue()
    }

    @Test
    fun `the router releases the screen off wake lock when the phone leaves the ear`() {
        val router = DefaultProximityAudioRouter(application())

        router.start()
        sendProximityEvent(isNear = true)
        sendProximityEvent(isNear = false)

        assertThat(ShadowPowerManager.getLatestWakeLock()?.isHeld).isFalse()
    }

    @Test
    fun `the router releases the screen off wake lock when playback stops`() {
        val router = DefaultProximityAudioRouter(application())

        router.start()
        sendProximityEvent(isNear = true)
        // Голосовое кончилось или пользователь нажал паузу.
        router.stop()

        assertThat(ShadowPowerManager.getLatestWakeLock()?.isHeld).isFalse()
    }

    @Test
    fun `the router does not hold the wake lock while the phone is away from the ear`() {
        val router = DefaultProximityAudioRouter(application())

        router.start()
        sendProximityEvent(isNear = false)

        // Экран живёт своей жизнью, пока телефон не у уха.
        assertThat(ShadowPowerManager.getLatestWakeLock()?.isHeld == true).isFalse()
    }

    @Test
    fun `the router stays a no-op on a device without a proximity sensor`() {
        val sensorManager = requireNotNull(application().getSystemService<SensorManager>())
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { shadowOf(sensorManager).removeSensor(it) }
        val router = DefaultProximityAudioRouter(application())

        router.start()
        sendProximityEvent(isNear = true)

        // Приложение должно работать и без датчика: не падаем и экран не трогаем.
        assertThat(ShadowPowerManager.getLatestWakeLock()).isNull()
    }

    private fun application(): Context = RuntimeEnvironment.getApplication()

    /** Robolectric не эмитит датчик сам, поэтому шлём событие зарегистрированным слушателям. */
    private fun sendProximityEvent(isNear: Boolean) {
        val sensorManager = requireNotNull(application().getSystemService<SensorManager>())
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
        val event = ShadowSensorManager.createSensorEvent(1)
        // createSensorEvent не привязывает событие к сенсору, а роутер фильтрует события по типу.
        ReflectionHelpers.setField(event, "sensor", sensor)
        event.values[0] = if (isNear) 0f else sensor.maximumRange
        shadowOf(sensorManager).sendSensorEventToListeners(event, sensor)
    }
}

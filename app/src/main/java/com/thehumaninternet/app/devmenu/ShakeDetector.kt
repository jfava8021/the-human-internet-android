package com.thehumaninternet.app.devmenu

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.sqrt

private const val SHAKE_THRESHOLD_G = 2.7f
private const val SHAKE_COOLDOWN_MS = 1_000L

/**
 * Calls [onShake] when the phone is shaken. Android has no system shake
 * gesture (iOS bridges `motionEnded`), so this reads the accelerometer, and
 * only while [enabled] and the app is in the foreground.
 */
@Composable
fun ShakeDetector(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)

    LifecycleResumeEffect(enabled) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || sensorManager == null || accelerometer == null) {
            return@LifecycleResumeEffect onPauseOrDispose {}
        }

        var lastShakeAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (gForce < SHAKE_THRESHOLD_G) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastShakeAt < SHAKE_COOLDOWN_MS) return
                lastShakeAt = now
                currentOnShake()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { sensorManager.unregisterListener(listener) }
    }
}

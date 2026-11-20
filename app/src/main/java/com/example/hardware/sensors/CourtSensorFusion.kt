package com.example.hardware.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

data class SensorTelemetry(
    val accelerationMagnitude: Float = 0f,
    val gyroMagnitude: Float = 0f,
    val isImpactDetected: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

@Singleton
class CourtSensorFusion @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _sensorData = MutableStateFlow(SensorTelemetry())
    val sensorData: StateFlow<SensorTelemetry> = _sensorData.asStateFlow()

    private var lastAccMagnitude = 9.8f

    fun startListening() {
        sensorManager?.let { sm ->
            accelerometer?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            gyroscope?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val now = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val mag = sqrt(ax * ax + ay * ay + az * az)
                val isImpact = kotlin.math.abs(mag - lastAccMagnitude) > 15f
                lastAccMagnitude = mag

                _sensorData.value = _sensorData.value.copy(
                    accelerationMagnitude = mag,
                    isImpactDetected = isImpact,
                    timestampMs = now
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                val mag = sqrt(gx * gx + gy * gy + gz * gz)

                _sensorData.value = _sensorData.value.copy(
                    gyroMagnitude = mag,
                    timestampMs = now
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}


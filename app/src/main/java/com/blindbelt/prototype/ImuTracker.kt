package com.blindbelt.prototype

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

class ImuTracker(private val sensorManager: SensorManager) : SensorEventListener {
    private var pitchDegrees = 0f
    private var rollDegrees = 0f
    
    // Exposed properties
    val pitch: Float
        get() = this.pitchDegrees
    
    val roll: Float
        get() = this.rollDegrees
    
    private var rotationVectorValues = FloatArray(4)
    private var rotationMatrix = FloatArray(9)
    private var orientationAngles = FloatArray(3)
    
    fun startTracking() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            // Fallback to accelerometer and magnetic field sensors
            val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accelSensor != null) {
                sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (magSensor != null) {
                sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }
    
    fun stopTracking() {
        sensorManager.unregisterListener(this)
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                // Directly use rotation vector
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                
                // Convert from radians to degrees and calculate pitch/roll
                pitchDegrees = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                rollDegrees = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
            }
            
            Sensor.TYPE_ACCELEROMETER -> {
                // Store accelerometer values for fallback calculation
                System.arraycopy(event.values, 0, rotationVectorValues, 0, 3)
            }
            
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Store magnetic field values for fallback calculation
                System.arraycopy(event.values, 0, rotationVectorValues, 3, 3)
                
                // Calculate orientation from accelerometer and magnetic field
                if (SensorManager.getRotationMatrix(rotationMatrix, null, 
                    rotationVectorValues.sliceArray(0..2), 
                    rotationVectorValues.sliceArray(3..5))) {
                    
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    
                    pitchDegrees = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                    rollDegrees = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // No implementation needed
    }
}
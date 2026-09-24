package net.nobu0707.busnav.map

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import net.nobu0707.busnav.domain.navigation.normalizeHeading
import net.nobu0707.busnav.domain.navigation.shortestHeadingDelta
import net.nobu0707.busnav.location.LocationState

enum class DisplayAxis { X, Y, MINUS_X, MINUS_Y }
data class DisplayAxes(val x: DisplayAxis, val y: DisplayAxis)

/** Pure sensor and map angle policy. Rotation values are Surface.ROTATION_*. */
object DeviceHeadingMath {
    fun axes(rotation: Int): DisplayAxes = when (rotation) {
        1 -> DisplayAxes(DisplayAxis.Y, DisplayAxis.MINUS_X)
        2 -> DisplayAxes(DisplayAxis.MINUS_X, DisplayAxis.MINUS_Y)
        3 -> DisplayAxes(DisplayAxis.MINUS_Y, DisplayAxis.X)
        else -> DisplayAxes(DisplayAxis.X, DisplayAxis.Y)
    }

    fun trueHeading(magneticDegrees: Double, declinationDegrees: Double): Double =
        normalizeHeading(magneticDegrees + declinationDegrees)

    fun smooth(previous: Double?, incoming: Double, weight: Double = 0.35): Double =
        if (previous == null) normalizeHeading(incoming)
        else normalizeHeading(previous + shortestHeadingDelta(previous, incoming) * weight)

    fun screenRotation(trueHeading: Double, cameraBearing: Double): Double =
        normalizeHeading(trueHeading - cameraBearing)
}

/** Registers only while an inactive navigation map is on screen and resumed. */
class DeviceHeadingSensor(
    context: Context,
    private val location: () -> LocationState?,
    private val displayRotation: () -> Int,
    private val onHeading: (Double) -> Unit,
) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var registered = false
    private var lastDeclinationLocation: LocationState? = null
    private var declination = 0.0
    private var previousHeading: Double? = null

    fun start() {
        if (!registered && sensor != null)
            registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (registered) manager.unregisterListener(this)
        registered = false
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val matrix = FloatArray(9)
        val adjusted = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val axes = DeviceHeadingMath.axes(displayRotation())
        SensorManager.remapCoordinateSystem(matrix, axes.x.sensorAxis(), axes.y.sensorAxis(), adjusted)
        val magnetic = Math.toDegrees(SensorManager.getOrientation(adjusted, FloatArray(3))[0].toDouble())
        val fix = location()
        if (fix != null && fix !== lastDeclinationLocation) {
            declination = GeomagneticField(fix.point.latitude.toFloat(), fix.point.longitude.toFloat(),
                0f, System.currentTimeMillis()).declination.toDouble()
            lastDeclinationLocation = fix
        }
        val heading = DeviceHeadingMath.smooth(previousHeading,
            DeviceHeadingMath.trueHeading(magnetic, declination))
        previousHeading = heading
        onHeading(heading)
    }

    private fun DisplayAxis.sensorAxis(): Int = when (this) {
        DisplayAxis.X -> SensorManager.AXIS_X
        DisplayAxis.Y -> SensorManager.AXIS_Y
        DisplayAxis.MINUS_X -> SensorManager.AXIS_MINUS_X
        DisplayAxis.MINUS_Y -> SensorManager.AXIS_MINUS_Y
    }
}

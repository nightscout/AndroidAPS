package app.aaps.core.interfaces.source

/**
 * The sensor age limits when the CGM source knows how long its sensor lasts. The master's source writes the lifetime
 * to `IntNonKey.SensorLifetimeHours`, which reaches the clients, so every phone uses this one rule and shows the same.
 *
 * - critical (red): the sensor has ended;
 * - warning (orange): a day before the end for a sensor of up to 30 days, 30 days before the end for a longer one.
 *
 * With no lifetime known, the user's own limits apply.
 */
object SensorLifetime {

    /** Sensor age in hours from which the sensor age is a warning. */
    fun warningHours(lifetimeHours: Int): Int =
        lifetimeHours - if (lifetimeHours <= SHORT_SENSOR_HOURS) DAY_HOURS else MONTH_HOURS

    /** Sensor age in hours from which the sensor age is critical: the sensor has ended. */
    fun criticalHours(lifetimeHours: Int): Int = lifetimeHours

    private const val DAY_HOURS = 24
    private const val MONTH_HOURS = 30 * 24

    /** Up to this, a sensor is a short one (days, not months): warned a day before its end */
    private const val SHORT_SENSOR_HOURS = 30 * 24
}

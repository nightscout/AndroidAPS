package app.aaps.plugins.source.notificationreader

import app.aaps.core.data.model.SourceSensor
import app.aaps.plugins.source.cgmapps.CgmApps
import kotlin.math.abs

/**
 * The notification reader's view of the CGM app catalog: which packages post readings in their notifications, the
 * sensor each one stands for, and how often it posts.
 */
class PackageConfig(
    val supportedPackages: Set<String>,
    val packageToSensor: Map<String, SourceSensor>,
    val packageToIntervalMs: Map<String, Long> = emptyMap()
) {

    fun isSupportedPackage(packageName: String): Boolean = packageName in supportedPackages

    fun sensorForPackage(packageName: String): SourceSensor =
        packageToSensor[packageName] ?: SourceSensor.UNKNOWN

    /** Configured interval in ms for the package, or [default] if missing/invalid. */
    fun intervalForPackage(packageName: String, default: Long): Long =
        packageToIntervalMs[packageName] ?: default

    companion object {

        /** Known valid CGM intervals in minutes. Unknown integers are snapped to the nearest. */
        private val KNOWN_INTERVALS_MIN = intArrayOf(1, 3, 5, 15)

        /**
         * The packages of every app in [apps] that has a `notification` entry. An interval of 0 or less falls back to
         * the caller's default; any other value is snapped to the nearest of {1, 3, 5, 15}.
         */
        fun fromCatalog(apps: CgmApps): PackageConfig {
            val supportedPackages = mutableSetOf<String>()
            val packageToSensor = mutableMapOf<String, SourceSensor>()
            val packageToIntervalMs = mutableMapOf<String, Long>()
            for (app in apps.apps) {
                val reading = app.notification ?: continue
                val sensor = SourceSensor.fromString(reading.sensor)
                for (pkg in app.packages) {
                    supportedPackages.add(pkg)
                    packageToSensor[pkg] = sensor
                    if (reading.intervalMinutes > 0) packageToIntervalMs[pkg] = snapToKnownIntervalMinutes(reading.intervalMinutes) * 60_000L
                }
            }
            return PackageConfig(supportedPackages, packageToSensor, packageToIntervalMs)
        }

        private fun snapToKnownIntervalMinutes(value: Int): Int =
            KNOWN_INTERVALS_MIN.minBy { abs(it - value) }
    }
}

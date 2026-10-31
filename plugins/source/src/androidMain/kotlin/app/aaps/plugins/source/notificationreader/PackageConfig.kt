package app.aaps.plugins.source.notificationreader

import app.aaps.core.data.model.SourceSensor
import org.json.JSONObject
import kotlin.math.abs

/**
 * Loads package-to-sensor mapping from JSON configuration.
 * The JSON can be bundled as an asset or fetched remotely for future updates.
 */
class PackageConfig(
    val version: Int,
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
         * Parse configuration from JSON string.
         *
         * Expected format:
         * ```json
         * {
         *   "version": 2,
         *   "packages": [
         *     { "package": "com.dexcom.g7", "sensor": "AAPS-DexcomG7", "intervalMinutes": 5 }
         *   ]
         * }
         * ```
         * `intervalMinutes` is optional. Missing/zero/negative values fall back to caller's default.
         * Out-of-set values are snapped to the nearest of {1, 3, 5, 15}.
         */
        fun fromJson(json: String): PackageConfig {
            val root = JSONObject(json)
            val version = root.optInt("version", 0)
            val packagesArray = root.getJSONArray("packages")
            val supportedPackages = mutableSetOf<String>()
            val packageToSensor = mutableMapOf<String, SourceSensor>()
            val packageToIntervalMs = mutableMapOf<String, Long>()

            for (i in 0 until packagesArray.length()) {
                val entry = packagesArray.getJSONObject(i)
                val pkg = entry.getString("package")
                val sensorText = entry.getString("sensor")
                supportedPackages.add(pkg)
                packageToSensor[pkg] = SourceSensor.fromString(sensorText)

                val intervalMin = entry.optInt("intervalMinutes", 0)
                if (intervalMin > 0) {
                    val snapped = snapToKnownIntervalMinutes(intervalMin)
                    packageToIntervalMs[pkg] = snapped * 60_000L
                }
            }

            return PackageConfig(version, supportedPackages, packageToSensor, packageToIntervalMs)
        }

        private fun snapToKnownIntervalMinutes(value: Int): Int =
            KNOWN_INTERVALS_MIN.minBy { abs(it - value) }
    }
}

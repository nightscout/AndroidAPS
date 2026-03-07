package app.aaps.plugins.source.notificationreader

import app.aaps.core.data.model.SourceSensor
import org.json.JSONObject

/**
 * Loads package-to-sensor mapping from JSON configuration.
 * The JSON can be bundled as an asset or fetched remotely for future updates.
 */
class PackageConfig(
    val version: Int,
    val supportedPackages: Set<String>,
    val packageToSensor: Map<String, SourceSensor>
) {

    fun isSupportedPackage(packageName: String): Boolean = packageName in supportedPackages

    fun sensorForPackage(packageName: String): SourceSensor =
        packageToSensor[packageName] ?: SourceSensor.UNKNOWN

    companion object {

        /**
         * Parse configuration from JSON string.
         *
         * Expected format:
         * ```json
         * {
         *   "version": 1,
         *   "packages": [
         *     { "package": "com.dexcom.g7", "sensor": "AAPS-DexcomG7" }
         *   ]
         * }
         * ```
         * The "sensor" value must match [SourceSensor.text].
         */
        fun fromJson(json: String): PackageConfig {
            val root = JSONObject(json)
            val version = root.optInt("version", 0)
            val packagesArray = root.getJSONArray("packages")
            val supportedPackages = mutableSetOf<String>()
            val packageToSensor = mutableMapOf<String, SourceSensor>()

            for (i in 0 until packagesArray.length()) {
                val entry = packagesArray.getJSONObject(i)
                val pkg = entry.getString("package")
                val sensorText = entry.getString("sensor")
                supportedPackages.add(pkg)
                packageToSensor[pkg] = SourceSensor.fromString(sensorText)
            }

            return PackageConfig(version, supportedPackages, packageToSensor)
        }
    }
}

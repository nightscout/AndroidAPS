package app.aaps.plugins.source.cgmapps

import app.aaps.core.interfaces.source.CalibrationMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The CGM vendor apps AAPS knows, and the Android packages each one has.
 *
 * One list for every place that needs a vendor app's package: to open the app, to see that it is installed, to read
 * its notifications. It ships as `cgm_apps.json` and a newer one is fetched from the `versions` branch, so a new
 * package does not wait for a release. That file is read by older AAPS too, so a field may only be added, never
 * renamed or removed; unknown fields are ignored for that reason.
 *
 * @param version newer replaces older; a fetched catalog is kept only while it is newer than the bundled one
 */
@Serializable
data class CgmApps(val version: Int = 0, val apps: List<CgmApp> = emptyList()) {

    fun byId(id: String): CgmApp? = apps.firstOrNull { it.id == id }

    fun byPackage(packageName: String): CgmApp? = apps.firstOrNull { packageName in it.packages }

    /** The packages of the apps with these [ids], in this order. An unknown id adds nothing. */
    fun packagesOf(vararg ids: String): List<String> = ids.flatMap { byId(it)?.packages.orEmpty() }

    companion object {

        // coerceInputValues: a value this version does not know (a newer `calibration`) reads as the default
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /** @throws IllegalArgumentException when [text] is not a catalog */
        fun fromJson(text: String): CgmApps = json.decodeFromString(serializer(), text)

        // The ids code refers to. The other apps are found by their package.
        const val XDRIP = "xdrip"
        const val JUGGLUCO = "juggluco"
        const val GLIMP = "glimp"
        const val MEDTRONIC_600_UPLOADER = "600-series-uploader"
        const val DEXCOM_G6 = "dexcom-g6"
        const val DEXCOM_G7 = "dexcom-g7"
        const val AIDEX = "aidex"
        const val OTTAI = "ottai"
        const val SYAI = "syai"
        const val SIBIONICS = "sibionics"
        const val SINOCARE = "sinocare"
        const val POCTECH = "poctech"
        const val INSTARA = "instara"

        /** Every id the code asks for: the shipped catalog must have each one. */
        val USED_IDS = listOf(
            XDRIP, JUGGLUCO, GLIMP, MEDTRONIC_600_UPLOADER, DEXCOM_G6, DEXCOM_G7, AIDEX, OTTAI, SYAI, SIBIONICS, SINOCARE, POCTECH, INSTARA
        )
    }
}

/**
 * One vendor app.
 *
 * @param id stable name, used in code and never changed
 * @param name the app's name as its users know it
 * @param packages every Android package of this app (regions, units, older versions)
 * @param notification how the notification reader reads its notifications, or null when it does not
 * @param calibration where the user calibrates this app's sensor, or null when it cannot be calibrated at all
 *   (factory calibrated). Only then may AAPS's calibration plugin correct the readings.
 */
@Serializable
data class CgmApp(
    val id: String,
    val name: String,
    val packages: List<String>,
    val notification: NotificationReading? = null,
    val calibration: Calibration? = null
)

/** Where the user calibrates a sensor that is not calibrated by AAPS. */
@Serializable
enum class Calibration {

    /** In the vendor app (or its receiver): the calibrate button opens the app. */
    @SerialName("app") APP,

    /** On the insulin pump the sensor sends to. No phone app takes it. */
    @SerialName("pump") PUMP
}

/** How AAPS handles a calibration for readings from this app. An unknown app is not calibrated elsewhere. */
fun CgmApp?.calibrationMode(): CalibrationMode = when (this?.calibration) {
    Calibration.APP  -> CalibrationMode.OWN_APP
    Calibration.PUMP -> CalibrationMode.PUMP
    null             -> CalibrationMode.AAPS
}

/**
 * @param sensor the sensor name stored with each reading (a `SourceSensor` text)
 * @param intervalMinutes how often the app posts a reading, or 0 to let the reader use its default
 */
@Serializable
data class NotificationReading(val sensor: String, val intervalMinutes: Int = 0)

package app.aaps.core.interfaces.nsclient

import android.text.Spanned
import app.aaps.core.interfaces.aps.APSResult
import dagger.android.HasAndroidInjector
import org.json.JSONObject

interface ProcessedDeviceStatusData {

    enum class Levels(val level: Int) {

        URGENT(2),
        WARN(1),
        INFO(0);
    }

    class PumpData {

        var clock = 0L
        var isPercent = false
        var percent = 0
        var voltage = 0.0
        var status = "N/A"
        var reservoir = 0.0
        var reservoirDisplayOverride = ""
        var extended: Spanned? = null
        var activeProfileName: String? = null
    }

    var pumpData: PumpData?

    data class Device(
        val createdAt: Long,
        val device: String?
    )

    var device: Device?

    class Uploader {

        var clock = 0L
        var battery = 0
        var isCharging: Boolean? = null
    }

    val uploaderMap: HashMap<String, Uploader>

    class OpenAPSData {

        var clockSuggested = 0L
        var clockEnacted = 0L
        var suggested: JSONObject? = null
        var enacted: JSONObject? = null
    }

    var openAPSData: OpenAPSData

    // test warning level // color
    fun pumpStatus(nsSettingsStatus: NSSettingsStatus): Spanned
    val extendedPumpStatus: Spanned
    val extendedOpenApsStatus: Spanned
    val openApsStatus: Spanned
    val openApsTimestamp: Long
    fun getAPSResult(injector: HasAndroidInjector): APSResult
    val uploaderStatus: String
    val uploaderStatusSpanned: Spanned
    val extendedUploaderStatus: Spanned
}
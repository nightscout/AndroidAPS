package info.nightscout.androidaps.plugins.sync.nsclient.data

import android.text.Spanned
import org.json.JSONObject
import java.util.HashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStatusData @Inject constructor() {

    class PumpData {
        var clock = 0L
        var isPercent = false
        var percent = 0
        var voltage = 0.0
        var status = "N/A"
        var reservoir = 0.0
        var extended: Spanned? = null
        var activeProfileName: String? = null
    }

    var pumpData: PumpData? = null

    class Uploader {
        var clock = 0L
        var battery = 0
    }

    val uploaderMap = HashMap<String, Uploader>()

    class OpenAPSData {
        var clockSuggested = 0L
        var clockEnacted = 0L
        var suggested: JSONObject? = null
        var enacted: JSONObject? = null
    }

    var openAPSData = OpenAPSData()
}


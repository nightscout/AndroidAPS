package info.nightscout.androidaps.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class XDripBroadcast @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val sp: SP
) {

    operator fun invoke(glucoseValue: GlucoseValue) {
        if (sp.getBoolean(R.string.key_dexcomg5_xdripupload, false)) {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            try {
                val entriesBody = JSONArray()
                val json = JSONObject()
                json.put("sgv", glucoseValue.value)
                json.put("direction", glucoseValue.trendArrow.text)
                json.put("device", "G5")
                json.put("type", "sgv")
                json.put("date", glucoseValue.timestamp)
                json.put("dateString", format.format(glucoseValue.timestamp))
                entriesBody.put(json)
                val bundle = Bundle()
                bundle.putString("action", "add")
                bundle.putString("collection", "entries")
                bundle.putString("data", entriesBody.toString())
                val intent = Intent(XDRIP_PLUS_NS_EMULATOR)
                intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                context.sendBroadcast(intent)
                val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)
                if (receivers.size < 1) {
                    //NSUpload.log.debug("No xDrip receivers found. ")
                    aapsLogger.debug(LTag.BGSOURCE, "No xDrip receivers found.")
                } else {
                    aapsLogger.debug(LTag.BGSOURCE, "${receivers.size} xDrip receivers")
                }
            } catch (e: JSONException) {
                aapsLogger.error(LTag.BGSOURCE, "Unhandled exception", e)
            }
        }
    }

    companion object {
        const val XDRIP_PLUS_NS_EMULATOR = "com.eveningoutpost.dexdrip.NS_EMULATOR"
    }
}
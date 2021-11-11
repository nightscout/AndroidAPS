package info.nightscout.androidaps.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.services.Intents
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("SpellCheckingInspection")
@Singleton
class XDripBroadcast @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val sp: SP
) {

    // sent in 640G mode
    fun send(glucoseValue: GlucoseValue) {
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
                val intent = Intent(Intents.XDRIP_PLUS_NS_EMULATOR)
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

    // sent in NSClient dbaccess mode
    fun sendProfile(profileStoreJson: JSONObject) {
        if (sp.getBoolean(R.string.key_nsclient_localbroadcasts, false))
            broadcast(
                Intent(Intents.ACTION_NEW_PROFILE).apply {
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    putExtras(Bundle().apply { putString("profile", profileStoreJson.toString()) })
                }
            )

    }

    // sent in NSClient dbaccess mode
    fun sendTreatments(addedOrUpdatedTreatments: JSONArray) {
        if (sp.getBoolean(R.string.key_nsclient_localbroadcasts, false))
            splitArray(addedOrUpdatedTreatments).forEach { part ->
                broadcast(
                    Intent(Intents.ACTION_NEW_TREATMENT).apply {
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        putExtras(Bundle().apply { putString("treatments", part.toString()) })
                    }
                )
            }
    }

    // sent in NSClient dbaccess mode
    fun sendSgvs(sgvs: JSONArray) {
        if (sp.getBoolean(R.string.key_nsclient_localbroadcasts, false))
            splitArray(sgvs).forEach { part ->
                broadcast(
                    Intent(Intents.ACTION_NEW_SGV).apply {
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        putExtras(Bundle().apply { putString("sgvs", part.toString()) })
                    }
                )
            }
    }

    private fun splitArray(array: JSONArray): List<JSONArray> {
        var ret: MutableList<JSONArray> = ArrayList()
        try {
            val size = array.length()
            var count = 0
            var newarr: JSONArray? = null
            for (i in 0 until size) {
                if (count == 0) {
                    if (newarr != null) ret.add(newarr)
                    newarr = JSONArray()
                    count = 20
                }
                newarr?.put(array[i])
                --count
            }
            if (newarr != null && newarr.length() > 0) ret.add(newarr)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
            ret = ArrayList()
            ret.add(array)
        }
        return ret
    }

    private fun broadcast(intent: Intent) {
        context.packageManager.queryBroadcastReceivers(intent, 0).forEach { resolveInfo ->
            resolveInfo.activityInfo.packageName?.let {
                intent.setPackage(it)
                context.sendBroadcast(intent)
                aapsLogger.debug(LTag.CORE, "Sending broadcast " + intent.action + " to: " + it)
            }
        }
    }
}
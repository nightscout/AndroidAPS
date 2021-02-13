package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.BundleLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.NSClientWorker
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin
import info.nightscout.androidaps.plugins.source.*
import info.nightscout.androidaps.services.Intents
import info.nightscout.androidaps.utils.extensions.copyDouble
import info.nightscout.androidaps.utils.extensions.copyInt
import info.nightscout.androidaps.utils.extensions.copyLong
import info.nightscout.androidaps.utils.extensions.copyString
import org.json.JSONObject
import javax.inject.Inject

open class DataReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var bundleStore: BundleStore

    private val jobGroupName = "data"

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val bundle = intent.extras ?: return
        aapsLogger.debug(LTag.DATASERVICE, "onReceive ${intent.action} ${BundleLogger.log(bundle)}")


        when (intent.action) {
            Intents.ACTION_NEW_BG_ESTIMATE ->
                OneTimeWorkRequest.Builder(XdripPlugin.XdripWorker::class.java)
                    .setInputData(bundleInputData(bundle, intent)).build()
            Intents.POCTECH_BG ->
                OneTimeWorkRequest.Builder(PoctechPlugin.PoctechWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("data", bundle)
                    }.build()).build()
            Intents.GLIMP_BG ->
                OneTimeWorkRequest.Builder(GlimpPlugin.GlimpWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyDouble("mySGV", bundle)
                        it.copyString("myTrend", bundle)
                        it.copyLong("myTimestamp", bundle)
                    }.build()).build()
            Intents.TOMATO_BG ->
                OneTimeWorkRequest.Builder(TomatoPlugin.TomatoWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyDouble("com.fanqies.tomatofn.Extras.BgEstimate", bundle)
                        it.copyLong("com.fanqies.tomatofn.Extras.Time", bundle)
                    }.build()).build()
            Intents.ACTION_NEW_PROFILE ->
                OneTimeWorkRequest.Builder(NSProfilePlugin.NSProfileWorker::class.java)
                    .setInputData(bundleInputData(bundle, intent)).build()
            Intents.ACTION_NEW_SGV ->
                OneTimeWorkRequest.Builder(NSClientSourcePlugin.NSClientSourceWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("sgv", bundle, null)
                        it.copyString("sgvs", bundle, null)
                    }.build()).build()
            Intents.NS_EMULATOR ->
                OneTimeWorkRequest.Builder(MM640gPlugin.MM640gWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyDouble(Intents.EXTRA_BG_ESTIMATE, bundle)
                        it.copyString(Intents.EXTRA_BG_SLOPE_NAME, bundle)
                        it.copyLong(Intents.EXTRA_TIMESTAMP, bundle)
                        it.copyDouble(Intents.EXTRA_RAW, bundle)
                        it.copyInt(Intents.EXTRA_SENSOR_BATTERY, bundle, -1)
                        it.copyString(Intents.XDRIP_DATA_SOURCE_DESCRIPTION, bundle)
                    }.build()).build()
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION ->
                OneTimeWorkRequest.Builder(SmsCommunicatorPlugin.SmsCommunicatorWorker::class.java)
                    .setInputData(bundleInputData(bundle, intent)).build()
            Intents.EVERSENSE_BG ->
                OneTimeWorkRequest.Builder(EversensePlugin.EversenseWorker::class.java)
                    .setInputData(bundleInputData(bundle, intent)).build()
            Intents.DEXCOM_BG ->
                OneTimeWorkRequest.Builder(DexcomPlugin.DexcomWorker::class.java)
                    .setInputData(bundleInputData(bundle, intent)).build()
            Intents.ACTION_NEW_TREATMENT,
            Intents.ACTION_CHANGED_TREATMENT,
            Intents.ACTION_REMOVED_TREATMENT,
            Intents.ACTION_NEW_CAL,
            Intents.ACTION_NEW_MBG ->
                OneTimeWorkRequest.Builder(NSClientWorker::class.java)
                    .setInputData(bundleInputData(bundle, intent)).build()
            else                                      -> null
        }?.let { request ->
            WorkManager.getInstance(context)
                .enqueueUniqueWork(jobGroupName, ExistingWorkPolicy.APPEND_OR_REPLACE , request)
        }
    }

    private fun bundleInputData(bundle: Bundle, intent: Intent) =
        Data.Builder().putLong(STORE_KEY, bundleStore.store(bundle)).putString(ACTION_KEY, intent.action).build()

    companion object {
        const val STORE_KEY = "storeKey"
        const val ACTION_KEY = "action"
    }
}
package info.nightscout.androidaps.receivers

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import dagger.android.DaggerBroadcastReceiver
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.plugins.source.*
import info.nightscout.androidaps.utils.extensions.copyDouble
import info.nightscout.androidaps.utils.extensions.copyLong
import info.nightscout.androidaps.utils.extensions.copyString
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.BundleLogger
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

open class DataReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dataWorker: DataWorker

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val bundle = intent.extras ?: return
        aapsLogger.debug(LTag.DATABASE, "onReceive ${intent.action} ${BundleLogger.log(bundle)}")

        when (intent.action) {
            Intents.ACTION_NEW_BG_ESTIMATE            ->
                OneTimeWorkRequest.Builder(XdripPlugin.XdripWorker::class.java)
                    .setInputData(dataWorker.storeInputData(bundle, intent.action)).build()
            Intents.POCTECH_BG                        ->
                OneTimeWorkRequest.Builder(PoctechPlugin.PoctechWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("data", bundle)
                    }.build()).build()
            Intents.GLIMP_BG                          ->
                OneTimeWorkRequest.Builder(GlimpPlugin.GlimpWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyDouble("mySGV", bundle)
                        it.copyString("myTrend", bundle)
                        it.copyLong("myTimestamp", bundle)
                    }.build()).build()
            Intents.TOMATO_BG                         ->
                @Suppress("SpellCheckingInspection")
                OneTimeWorkRequest.Builder(TomatoPlugin.TomatoWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyDouble("com.fanqies.tomatofn.Extras.BgEstimate", bundle)
                        it.copyLong("com.fanqies.tomatofn.Extras.Time", bundle)
                    }.build()).build()
            Intents.NS_EMULATOR                       ->
                OneTimeWorkRequest.Builder(MM640gPlugin.MM640gWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()).build()
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION ->
                OneTimeWorkRequest.Builder(SmsCommunicatorPlugin.SmsCommunicatorWorker::class.java)
                    .setInputData(dataWorker.storeInputData(bundle, intent.action)).build()
            Intents.EVERSENSE_BG                      ->
                OneTimeWorkRequest.Builder(EversensePlugin.EversenseWorker::class.java)
                    .setInputData(dataWorker.storeInputData(bundle, intent.action)).build()
            Intents.DEXCOM_BG                         ->
                OneTimeWorkRequest.Builder(DexcomPlugin.DexcomWorker::class.java)
                    .setInputData(dataWorker.storeInputData(bundle, intent.action)).build()
            Intents.DEXCOM_G7_BG                       ->
                OneTimeWorkRequest.Builder(DexcomPlugin.DexcomWorker::class.java)
                    .setInputData(dataWorker.storeInputData(bundle, intent.action)).build()
            Intents.AIDEX_NEW_BG_ESTIMATE             ->
                OneTimeWorkRequest.Builder(AidexPlugin.AidexWorker::class.java)
                    .setInputData(dataWorker.storeInputData(bundle, intent.action)).build()
            else                                      -> null
        }?.let { request -> dataWorker.enqueue(request) }
    }

}

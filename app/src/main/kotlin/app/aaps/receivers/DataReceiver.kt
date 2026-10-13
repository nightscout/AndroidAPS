package app.aaps.receivers

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.annotation.VisibleForTesting
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.utils.extensions.copyDouble
import app.aaps.core.utils.extensions.copyLong
import app.aaps.core.utils.extensions.copyString
import app.aaps.core.utils.receivers.BundleLogger
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.plugins.main.general.smsCommunicator.SmsCommunicatorPlugin
import app.aaps.plugins.source.DexcomPlugin
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.MM640gPlugin
import app.aaps.plugins.source.PatchedSiAppPlugin
import app.aaps.plugins.source.PatchedSinoAppPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.SyaiPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.source.XdripSourcePlugin
import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject

open class DataReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        processIntent(context, intent)
    }

    @VisibleForTesting
    fun processIntent(context: Context, intent: Intent) {
        val bundle = intent.extras ?: return
        aapsLogger.debug(LTag.CORE, "onReceive ${intent.action} ${BundleLogger.log(bundle)}")
        when (intent.action) {
            Intents.ACTION_NEW_BG_ESTIMATE            ->
                OneTimeWorkRequest.Builder(XdripSourcePlugin.XdripSourceWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(bundle, intent.action)).build()

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

            Intents.OTTAI_APP, Intents.OTTAI_APP_CN,
            Intents.SYAI_APP                          ->
                OneTimeWorkRequest.Builder(SyaiPlugin.SyaiWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()).build()

            Intents.SI_APP                            ->
                OneTimeWorkRequest.Builder(PatchedSiAppPlugin.PatchedSiAppWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()).build()

            Intents.SINO_APP                          ->
                OneTimeWorkRequest.Builder(PatchedSinoAppPlugin.PatchedSinoAppWorker::class.java)
                    .setInputData(Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()).build()

            Telephony.Sms.Intents.SMS_RECEIVED_ACTION ->
                OneTimeWorkRequest.Builder(SmsCommunicatorPlugin.SmsCommunicatorWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(bundle, intent.action)).build()

            Intents.DEXCOM_BG, Intents.DEXCOM_G7_BG   ->
                OneTimeWorkRequest.Builder(DexcomPlugin.DexcomWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(bundle, intent.action)).build()

            else                                      -> null
        }?.let { request -> dataWorkerStorage.enqueue(request) }

        // Verify KeepAlive is running
        // Sometimes the schedule fail
        KeepAliveWorker.scheduleIfNotRunning(context, aapsLogger, fabricPrivacy)
    }
}

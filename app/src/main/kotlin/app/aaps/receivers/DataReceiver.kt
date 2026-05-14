package app.aaps.receivers

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.annotation.VisibleForTesting
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.utils.extensions.copyDouble
import app.aaps.core.utils.extensions.copyLong
import app.aaps.core.utils.extensions.copyString
import app.aaps.core.utils.receivers.BundleLogger
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.plugins.source.DexcomInbox
import app.aaps.plugins.source.GlimpPlugin
import app.aaps.plugins.source.MM640gPlugin
import app.aaps.plugins.source.PatchedSiAppPlugin
import app.aaps.plugins.source.PatchedSinoAppPlugin
import app.aaps.plugins.source.PoctechPlugin
import app.aaps.plugins.source.SyaiPlugin
import app.aaps.plugins.source.TomatoPlugin
import app.aaps.plugins.source.XdripInbox
import app.aaps.plugins.source.instara.InstaraPlugin
import app.aaps.plugins.sync.smsCommunicator.SmsInbox
import dagger.android.DaggerBroadcastReceiver
import javax.inject.Inject

open class DataReceiver : DaggerBroadcastReceiver() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dataInbox: DataInbox
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
                dataInbox.putAndEnqueue(XdripInbox, bundle)

            Intents.POCTECH_BG                        ->
                enqueueInline(
                    context, PoctechPlugin.PoctechWorker::class.java,
                    Data.Builder().also {
                        it.copyString("data", bundle)
                    }.build()
                )

            Intents.GLIMP_BG                          ->
                enqueueInline(
                    context, GlimpPlugin.GlimpWorker::class.java,
                    Data.Builder().also {
                        it.copyDouble("mySGV", bundle)
                        it.copyString("myTrend", bundle)
                        it.copyLong("myTimestamp", bundle)
                    }.build()
                )

            Intents.TOMATO_BG                         ->
                enqueueInline(
                    context, TomatoPlugin.TomatoWorker::class.java,
                    Data.Builder().also {
                        it.copyDouble("com.fanqies.tomatofn.Extras.BgEstimate", bundle)
                        it.copyLong("com.fanqies.tomatofn.Extras.Time", bundle)
                    }.build()
                )

            Intents.NS_EMULATOR                       ->
                enqueueInline(
                    context, MM640gPlugin.MM640gWorker::class.java,
                    Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()
                )

            Intents.OTTAI_APP, Intents.OTTAI_APP_CN,
            Intents.SYAI_APP                          ->
                enqueueInline(
                    context, SyaiPlugin.SyaiWorker::class.java,
                    Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()
                )

            Intents.SI_APP                            ->
                enqueueInline(
                    context, PatchedSiAppPlugin.PatchedSiAppWorker::class.java,
                    Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()
                )

            Intents.SINO_APP                          ->
                enqueueInline(
                    context, PatchedSinoAppPlugin.PatchedSinoAppWorker::class.java,
                    Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()
                )

            Intents.INSTARA_APP                       ->
                enqueueInline(
                    context, InstaraPlugin.InstaraWorker::class.java,
                    Data.Builder().also {
                        it.copyString("collection", bundle)
                        it.copyString("data", bundle)
                    }.build()
                )

            Telephony.Sms.Intents.SMS_RECEIVED_ACTION ->
                dataInbox.putAndEnqueue(SmsInbox, bundle)

            Intents.DEXCOM_BG, Intents.DEXCOM_G7_BG   ->
                dataInbox.putAndEnqueue(DexcomInbox, bundle)
        }

        // Verify KeepAlive is running
        // Sometimes the schedule fail
        KeepAliveWorker.scheduleIfNotRunning(context, aapsLogger, fabricPrivacy)
    }

    private fun enqueueInline(context: Context, worker: Class<out ListenableWorker>, data: Data) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            INLINE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(worker).setInputData(data).build()
        )
    }

    companion object {

        // Shared unique-work name for receivers whose payloads inline into Data;
        // preserves the serialization the original DataWorkerStorage.enqueue used.
        private const val INLINE_WORK_NAME = "data"
    }
}

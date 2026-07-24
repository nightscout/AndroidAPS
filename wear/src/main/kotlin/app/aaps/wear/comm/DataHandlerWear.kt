package app.aaps.wear.comm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearDataToMobile
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.R
import app.aaps.wear.complications.BgGraphComplication
import app.aaps.wear.complications.BrCobIobComplication
import app.aaps.wear.complications.BrCobIobComplicationExt1
import app.aaps.wear.complications.BrCobIobComplicationExt2
import app.aaps.wear.complications.BrComplication
import app.aaps.wear.complications.BrIobComplication
import app.aaps.wear.complications.BrTtComplication
import app.aaps.wear.complications.CobDetailedComplication
import app.aaps.wear.complications.CobIconComplication
import app.aaps.wear.complications.CobIobComplication
import app.aaps.wear.complications.IobDetailedComplication
import app.aaps.wear.complications.IobIconComplication
import app.aaps.wear.complications.LongStatusComplication
import app.aaps.wear.complications.LongStatusFlippedComplication
import app.aaps.wear.complications.SgvComplication
import app.aaps.wear.complications.SgvComplicationExt1
import app.aaps.wear.complications.SgvComplicationExt2
import app.aaps.wear.complications.SgvLargeComplication
import app.aaps.wear.complications.TargetComplication
import app.aaps.wear.complications.UploaderBatteryComplication
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.interaction.WatchfaceConfigurationActivity
import androidx.wear.activity.ConfirmationActivity
import app.aaps.wear.interaction.actions.AcceptActivity
import app.aaps.wear.interaction.actions.ContactingMasterActivity
import app.aaps.wear.interaction.actions.ProfileSwitchActivity
import app.aaps.wear.tile.ActionsTileService
import app.aaps.wear.tile.BgGraphTileService
import app.aaps.wear.tile.QuickWizardTileService
import app.aaps.wear.tile.RunningModeTileService
import app.aaps.wear.tile.SceneTileService
import app.aaps.wear.tile.TempTargetTileService
import app.aaps.wear.tile.UserActionTileService
import com.google.android.gms.wearable.WearableListenerService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataHandlerWear @Inject constructor(
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val sp: SP,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    private val complicationDataRepository: ComplicationDataRepository
) {

    // Coroutine scope for DataStore operations
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val disposable = CompositeDisposable()

    init {
        setupBus()
    }

    /**
     * Registers a [handler] for one [EventData] subtype arriving from the phone.
     *
     * Mirrors the per-type subscription shape used across [setupBus] and emits a uniform
     * "<Type> received from <node>" debug line so call sites stay one-liners. Handlers that want
     * extra diagnostics append them via [detail] (e.g. dataset/sgv/scene state). Only the debounced
     * batch streams (heart-rate / steps) keep a bespoke subscription.
     */
    private inline fun <reified T : EventData> onEvent(
        crossinline detail: (T) -> String = { "" },
        crossinline handler: (T) -> Unit
    ) {
        disposable += rxBus
            .toObservable(T::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { event ->
                aapsLogger.debug(LTag.WEAR, "${T::class.java.simpleName} received from ${event.sourceNodeId}${detail(event)}")
                handler(event)
            }
    }

    private fun setupBus() {
        onEvent<EventData.ActionPing> {
            rxBus.send(EventWearToMobile(EventData.ActionPong(System.currentTimeMillis(), Build.VERSION.SDK_INT)))
        }
        onEvent<EventData.ConfirmAction> {
            // The resolving terminal (master-authored lines, or an error) arrived → dismiss the "contacting master"
            // spinner if one is up (prepare round-trip finished), then show the confirm/error screen.
            ContactingMasterActivity.dismiss()
            context.startActivity(Intent(context, AcceptActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtras(
                    Bundle().also { bundle ->
                        bundle.putString(DataLayerListenerServiceWear.KEY_TITLE, it.title)
                        bundle.putString(DataLayerListenerServiceWear.KEY_MESSAGE, it.message)
                        bundle.putString(DataLayerListenerServiceWear.KEY_ACTION_DATA, it.returnCommand?.serialize())
                        if (it.lines.isNotEmpty()) {
                            bundle.putStringArray(DataLayerListenerServiceWear.KEY_LINE_ROLES, it.lines.map { l -> l.role }.toTypedArray())
                            bundle.putStringArray(DataLayerListenerServiceWear.KEY_LINE_TEXTS, it.lines.map { l -> l.text }.toTypedArray())
                        }
                        bundle.putBoolean(DataLayerListenerServiceWear.KEY_IS_ERROR, it.returnCommand is EventData.Error)
                        // CLIENT relay: the ✓ must NOT flash success — it shows the spinner + waits for the master's
                        // real commit terminal ([RemoteDelivered] / error). False on a master (instant local success).
                        bundle.putBoolean(DataLayerListenerServiceWear.KEY_DEFER_CONFIRM, it.deferConfirm)
                        it.wizardDetail?.let { d ->
                            bundle.putString(DataLayerListenerServiceWear.KEY_WIZARD_DETAIL, Json.encodeToString(EventData.WizardDetail.serializer(), d))
                        }
                    }
                )
            })
        }
        onEvent<EventData.ContactingMaster> {
            ContactingMasterActivity.show(context)
        }
        onEvent<EventData.RemoteDelivered> {
            // A deferred (relayed) commit was APPLIED on the master → dismiss the spinner + show the success animation.
            ContactingMasterActivity.dismiss()
            context.startActivity(
                Intent(context, ConfirmationActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                    putExtra(ConfirmationActivity.EXTRA_MESSAGE, context.getString(R.string.wizard_confirmation_sent))
                }
            )
        }
        onEvent<EventData.CancelNotification> {
            (context.getSystemService(WearableListenerService.NOTIFICATION_SERVICE) as NotificationManager).cancel(DataLayerListenerServiceWear.CHANGE_NOTIF_ID)
        }
        onEvent<EventData.OpenLoopRequest> { handleOpenLoopRequest(it) }
        onEvent<EventData.OpenSettings> {
            context.startActivity(Intent(context, WatchfaceConfigurationActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
        onEvent<EventData.ActionProfileSwitchOpenActivity> { event ->
            context.startActivity(Intent(context, ProfileSwitchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtras(Bundle().also { bundle ->
                    bundle.putInt("percentage", event.percentage)
                    bundle.putInt("timeshift", event.timeShift)
                })
            })
        }
        onEvent<EventData.BolusProgress> { handleBolusProgress(it) }
        onEvent<EventData.Status>(detail = { " dataset=${it.dataset} iob=${it.iobSum} cob=${it.cob}" }) {
            // Store in DataStore - supports all datasets (0, 1, 2)
            dataStoreScope.launch {
                complicationDataRepository.updateStatusData(it)

                // Trigger complications AFTER DataStore write completes
                // This ensures complications showing IOB/COB/BR update immediately
                triggerComplicationUpdates()
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
        }
        onEvent<EventData.SingleBg>(detail = { " dataset=${it.dataset} sgv=${it.sgvString} arrow=${it.slopeArrow}" }) {
            // Store in DataStore - supports all datasets (0, 1, 2)
            dataStoreScope.launch {
                complicationDataRepository.updateBgData(it)

                // Trigger complications AFTER DataStore write completes
                triggerComplicationUpdates()
                TileService.getUpdater(context).requestUpdate(BgGraphTileService::class.java)
            }

            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
        }
        onEvent<EventData.GraphData> {
            // Store in DataStore
            dataStoreScope.launch {
                complicationDataRepository.updateGraphData(it)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
        }
        onEvent<EventData.TreatmentData> {
            // Store in DataStore
            dataStoreScope.launch {
                complicationDataRepository.updateTreatmentData(it)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
        }
        onEvent<EventData.Preferences> {
            if (it.wearControl != preferences.get(BooleanKey.WearControl)) {
                preferences.put(BooleanKey.WearControl, it.wearControl)
                TileService.getUpdater(context).requestUpdate(ActionsTileService::class.java)
                TileService.getUpdater(context).requestUpdate(TempTargetTileService::class.java)
                TileService.getUpdater(context).requestUpdate(QuickWizardTileService::class.java)
            }
            sp.putBoolean(R.string.key_units_mgdl, it.unitsMgdl)
            sp.putInt(R.string.key_bolus_wizard_percentage, it.bolusPercentage)
            sp.putInt(R.string.key_treatments_safety_max_carbs, it.maxCarbs)
            sp.putDouble(R.string.key_treatments_safety_max_bolus, it.maxBolus)
            preferences.put(DoubleKey.OverviewInsulinButtonIncrement1, it.insulinButtonIncrement1)
            preferences.put(DoubleKey.OverviewInsulinButtonIncrement2, it.insulinButtonIncrement2)
            preferences.put(IntKey.OverviewCarbsButtonIncrement1, it.carbsButtonIncrement1)
            preferences.put(IntKey.OverviewCarbsButtonIncrement2, it.carbsButtonIncrement2)
        }
        onEvent<EventData.QuickWizard> {
            val serialized = it.serialize()
            if (serialized != sp.getString(R.string.key_quick_wizard_data, "")) {
                sp.putString(R.string.key_quick_wizard_data, serialized)
                TileService.getUpdater(context).requestUpdate(QuickWizardTileService::class.java)
            }
        }
        onEvent<EventData.UserAction> {
            val serialized = it.serialize()
            if (serialized != sp.getString(R.string.key_user_action_data, "")) {
                sp.putString(R.string.key_user_action_data, serialized)
                TileService.getUpdater(context).requestUpdate(UserActionTileService::class.java)
            }
        }
        onEvent<EventData.SceneList> {
            val serialized = it.serialize()
            if (serialized != sp.getString(R.string.key_scene_data, "")) {
                sp.putString(R.string.key_scene_data, serialized)
                TileService.getUpdater(context).requestUpdate(SceneTileService::class.java)
            }
        }
        onEvent<EventData.ActiveSceneState>(detail = { " active=${it.active}" }) {
            val serialized = it.serialize()
            if (serialized != sp.getString(R.string.key_active_scene_state, "")) {
                sp.putString(R.string.key_active_scene_state, serialized)
                TileService.getUpdater(context).requestUpdate(SceneTileService::class.java)
            }
        }
        onEvent<EventData.RunningModeList> {
            val serialized = it.serialize()
            if (serialized != sp.getString(R.string.key_running_mode_data, "")) {
                sp.putString(R.string.key_running_mode_data, serialized)
                TileService.getUpdater(context).requestUpdate(RunningModeTileService::class.java)
            }
        }
        onEvent<EventData.ActionSetCustomWatchface> {
            dataStoreScope.launch {
                complicationDataRepository.storeCustomWatchface(it.customWatchfaceData)
                complicationDataRepository.getSimplifiedCustomWatchface()?.let { cwf ->
                    rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(cwf), false)))
                }
            }
        }
        onEvent<EventData.ActionUpdateCustomWatchface> {
            dataStoreScope.launch {
                complicationDataRepository.updateCustomWatchfaceMetadata(it.customWatchfaceData.metadata)
                complicationDataRepository.getSimplifiedCustomWatchface()?.let { cwf ->
                    rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(cwf), false)))
                }
            }
        }
        onEvent<EventData.ActionrequestSetDefaultWatchface> {
            dataStoreScope.launch {
                complicationDataRepository.setDefaultWatchface()
                complicationDataRepository.getCustomWatchface()?.let { cwf ->
                    rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(cwf), false)))
                }
            }
        }
        onEvent<EventData.ActionrequestCustomWatchface>(detail = { " export=${it.exportFile}" }) {
            dataStoreScope.launch {
                complicationDataRepository.getSimplifiedCustomWatchface(it.exportFile)?.let { cwf ->
                    rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(cwf), it.exportFile)))
                }
            }
        }
    }

    private fun handleBolusProgress(bolusProgress: EventData.BolusProgress) {
        val vibratePattern: LongArray
        val vibrate = sp.getBoolean("vibrateOnBolus", true)
        vibratePattern = if (vibrate) longArrayOf(0, 50, 1000) else longArrayOf(0, 1, 1000)

        createBolusProgressChannels()
        val cancelIntent = Intent(context, DataLayerListenerServiceWear::class.java)
        cancelIntent.action = DataLayerListenerServiceWear.INTENT_CANCEL_BOLUS
        val cancelPendingIntent = PendingIntent.getService(context, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(
                context,
                if (vibrate) DataLayerListenerServiceWear.AAPS_NOTIFY_CHANNEL_ID_BOLUS_PROGRESS else DataLayerListenerServiceWear.AAPS_NOTIFY_CHANNEL_ID_BOLUS_PROGRESS_SILENT
            )
                .setSmallIcon(R.drawable.ic_icon)
                .setContentTitle(context.getString(R.string.bolus_progress))
                .setContentText("${bolusProgress.percent}% - ${bolusProgress.status}")
                .setSubText(context.getString(R.string.press_to_cancel))
                .setContentIntent(cancelPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVibrate(vibratePattern)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_cancel, context.getString(R.string.cancel_bolus), cancelPendingIntent)
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(DataLayerListenerServiceWear.BOLUS_PROGRESS_NOTIF_ID, notificationBuilder.build())
        notificationManager.cancel(DataLayerListenerServiceWear.CONFIRM_NOTIF_ID) // multiple watch setup
        if (bolusProgress.percent == 100) {
            scheduleDismissBolusProgress(5)
        }
    }

    private fun createBolusProgressChannels() {
        createNotificationChannel(
            longArrayOf(0, 50, 1000),
            DataLayerListenerServiceWear.AAPS_NOTIFY_CHANNEL_ID_BOLUS_PROGRESS,
            context.getString(R.string.bolus_progress_channel_name),
            context.getString(R.string.bolus_progress_channel_description),
            NotificationManager.IMPORTANCE_HIGH
        )
        createNotificationChannel(
            longArrayOf(0),
            DataLayerListenerServiceWear.AAPS_NOTIFY_CHANNEL_ID_BOLUS_PROGRESS_SILENT,
            context.getString(R.string.bolus_progress_silent_channel_name),
            context.getString(R.string.bolus_progress_silent_channel_description),
            NotificationManager.IMPORTANCE_LOW
        )
    }

    private fun createNotificationChannel(vibratePattern: LongArray, channelID: String, name: CharSequence, description: String, importance: Int) {
        val channel = NotificationChannel(channelID, name, importance)
        channel.description = description
        channel.enableVibration(true)
        channel.vibrationPattern = vibratePattern

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun handleOpenLoopRequest(command: EventData.OpenLoopRequest) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        val name: CharSequence = "AAPS Open Loop"
        val description = "Open Loop request notification"
        val channel = NotificationChannel(DataLayerListenerServiceWear.AAPS_NOTIFY_CHANNEL_ID_OPEN_LOOP, name, NotificationManager.IMPORTANCE_HIGH)
        channel.description = description
        channel.enableVibration(true)

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        var builder = NotificationCompat.Builder(context, DataLayerListenerServiceWear.AAPS_NOTIFY_CHANNEL_ID_OPEN_LOOP)
            .setSmallIcon(R.drawable.notif_icon)
            .setContentTitle(command.title)
            .setContentText(command.message)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Creates an explicit intent for an Activity in your app
        val intent = Intent(context, AcceptActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtras(Bundle().also { bundle ->
                bundle.putString(DataLayerListenerServiceWear.KEY_TITLE, command.title)
                bundle.putString(DataLayerListenerServiceWear.KEY_MESSAGE, command.message)
                bundle.putString(DataLayerListenerServiceWear.KEY_ACTION_DATA, command.returnCommand?.serialize())
            })
        }
        val resultPendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder = builder.setContentIntent(resultPendingIntent)
        val mNotificationManager = context.getSystemService(WearableListenerService.NOTIFICATION_SERVICE) as NotificationManager
        // mId allows you to update the notification later on.
        mNotificationManager.notify(DataLayerListenerServiceWear.CHANGE_NOTIF_ID, builder.build())
    }

    @Suppress("SameParameterValue")
    private fun scheduleDismissBolusProgress(seconds: Int) {
        Thread {
            SystemClock.sleep(seconds * 1000L)
            NotificationManagerCompat.from(context).cancel(DataLayerListenerServiceWear.BOLUS_PROGRESS_NOTIF_ID)
        }.start()
    }

    /**
     * Trigger all modern complications to update when new data arrives
     * This ensures complications show fresh data immediately instead of waiting for UPDATE_PERIOD
     *
     * TODO: Migrate to androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
     *       when the full complications migration from old support library to modern AndroidX happens
     */
    private fun triggerComplicationUpdates() {
        val modernComplications = listOf(
            // SGV complications (show glucose values)
            SgvComplication::class.java,
            SgvComplicationExt1::class.java,
            SgvComplicationExt2::class.java,
            SgvLargeComplication::class.java,
            // BG graph image complication (for WFF watchfaces on watches without CWF support)
            BgGraphComplication::class.java,
            // Long status complications (show detailed glucose + status info)
            LongStatusComplication::class.java,
            LongStatusFlippedComplication::class.java,
            // IOB complications
            IobIconComplication::class.java,
            IobDetailedComplication::class.java,
            // COB complications
            CobIconComplication::class.java,
            CobDetailedComplication::class.java,
            CobIobComplication::class.java,
            // Basal rate complications
            BrComplication::class.java,
            BrIobComplication::class.java,
            BrTtComplication::class.java,
            TargetComplication::class.java,
            BrCobIobComplication::class.java,
            BrCobIobComplicationExt1::class.java,
            BrCobIobComplicationExt2::class.java,
            // Battery complication
            UploaderBatteryComplication::class.java
            // Note: WallpaperComplication is abstract, subclasses will auto-update
        )

        for (complicationClass in modernComplications) {
            try {
                val componentName = ComponentName(context, complicationClass)

                val requester = ComplicationDataSourceUpdateRequester.create(context, componentName)
                requester.requestUpdateAll()
            } catch (e: Exception) {
                aapsLogger.error(LTag.WEAR, "Failed to trigger complication update: ${complicationClass.simpleName}", e)
            }
        }
    }
}

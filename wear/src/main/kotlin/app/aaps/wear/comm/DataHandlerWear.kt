package app.aaps.wear.comm

import app.aaps.wear.interaction.actions.WizardResultActivity
import android.app.Notification
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
import app.aaps.wear.complications.BrCobIobComplication
import app.aaps.wear.complications.BrCobIobComplicationExt1
import app.aaps.wear.complications.BrCobIobComplicationExt2
import app.aaps.wear.complications.BrComplication
import app.aaps.wear.complications.BrIobComplication
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
import app.aaps.wear.complications.UploaderBatteryComplication
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.interaction.WatchfaceConfigurationActivity
import app.aaps.wear.interaction.actions.AcceptActivity
import app.aaps.wear.interaction.actions.ProfileSwitchActivity
import app.aaps.wear.tile.ActionsTileService
import app.aaps.wear.tile.LoopStateTileService
import app.aaps.wear.tile.QuickWizardTileService
import app.aaps.wear.tile.TempTargetTileService
import app.aaps.wear.tile.UserActionTileService
import com.google.android.gms.wearable.WearableListenerService
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
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

    private fun setupBus() {
        disposable += rxBus
            .toObservable(EventData.ActionPing::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Ping received from ${it.sourceNodeId}")
                rxBus.send(EventWearToMobile(EventData.ActionPong(System.currentTimeMillis(), Build.VERSION.SDK_INT)))
            }
        disposable += rxBus
            .toObservable(EventData.ConfirmAction::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "ConfirmAction received from ${it.sourceNodeId}")
                context.startActivity(Intent(context, AcceptActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtras(
                        Bundle().also { bundle ->
                            bundle.putString(DataLayerListenerServiceWear.KEY_TITLE, it.title)
                            bundle.putString(DataLayerListenerServiceWear.KEY_MESSAGE, it.message)
                            bundle.putString(DataLayerListenerServiceWear.KEY_ACTION_DATA, it.returnCommand?.serialize())
                        }
                    )
                })
            }
        disposable += rxBus
            .toObservable(EventData.ActionWizardResult::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { event ->
                aapsLogger.debug(LTag.WEAR, "ActionWizardResult received from ${event.sourceNodeId}")

                try {
                    val resultIntent = Intent(context, WizardResultActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("timestamp", event.timestamp)
                        putExtra("total_insulin", event.totalInsulin)
                        putExtra("carbs", event.carbs)
                        putExtra("ic", event.ic)
                        putExtra("sens", event.sens)
                        putExtra("insulin_carbs", event.insulinFromCarbs)
                        putExtra("insulin_bg", event.insulinFromBG ?: Double.NaN)
                        putExtra("insulin_cob", event.insulinFromCOB ?: Double.NaN)
                        putExtra("insulin_bolus_iob", event.insulinFromBolusIOB ?: Double.NaN)
                        putExtra("insulin_basal_iob", event.insulinFromBasalIOB ?: Double.NaN)
                        putExtra("insulin_trend", event.insulinFromTrend ?: Double.NaN)
                        putExtra("insulin_superbolus", event.insulinFromSuperBolus ?: Double.NaN)
                        putExtra("temp_target", event.tempTarget ?: "")
                        putExtra("percentage", event.percentageCorrection ?: 100)
                        putExtra("total_before_percentage", event.totalBeforePercentage ?: Double.NaN)
                        putExtra("cob", event.cob)
                    }

                    context.startActivity(resultIntent)
                    aapsLogger.debug(LTag.WEAR, "WizardResultActivity started successfully")
                } catch (e: Exception) {
                    aapsLogger.error(LTag.WEAR, "Error starting WizardResultActivity", e)
                }
            }
        disposable += rxBus
            .toObservable(EventData.CancelNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "ActionCancelNotification received from ${it.sourceNodeId}")
                (context.getSystemService(WearableListenerService.NOTIFICATION_SERVICE) as NotificationManager).cancel(DataLayerListenerServiceWear.CHANGE_NOTIF_ID)
            }
        disposable += rxBus
            .toObservable(EventData.OpenLoopRequest::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "OpenLoopRequest received from ${it.sourceNodeId}")
                handleOpenLoopRequest(it)
            }
        disposable += rxBus
            .toObservable(EventData.OpenSettings::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "ActionOpenSettings received from ${it.sourceNodeId}")
                context.startActivity(Intent(context, WatchfaceConfigurationActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            }
        disposable += rxBus
            .toObservable(EventData.ActionProfileSwitchOpenActivity::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { event ->
                aapsLogger.debug(LTag.WEAR, "ActionProfileSwitchOpenActivity received from ${event.sourceNodeId}")
                context.startActivity(Intent(context, ProfileSwitchActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtras(Bundle().also { bundle ->
                        bundle.putInt("percentage", event.percentage)
                        bundle.putInt("timeshift", event.timeShift)
                    })
                })
            }
        disposable += rxBus
            .toObservable(EventData.BolusProgress::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Bolus progress received from ${it.sourceNodeId}")
                handleBolusProgress(it)
            }
        disposable += rxBus
            .toObservable(EventData.Status::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Status received: dataset=${it.dataset} iob=${it.iobSum} cob=${it.cob}")
                // Store in DataStore - supports all datasets (0, 1, 2)
                dataStoreScope.launch {
                    complicationDataRepository.updateStatusData(it)

                    // Trigger complications AFTER DataStore write completes
                    // This ensures complications showing IOB/COB/BR update immediately
                    triggerComplicationUpdates()
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
            }
        disposable += rxBus
            .toObservable(EventData.SingleBg::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "BG received: dataset=${it.dataset} sgv=${it.sgvString} arrow=${it.slopeArrow}")

                // Store in DataStore - supports all datasets (0, 1, 2)
                dataStoreScope.launch {
                    complicationDataRepository.updateBgData(it)

                    // Trigger complications AFTER DataStore write completes
                    triggerComplicationUpdates()
                }

                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
            }
        disposable += rxBus
            .toObservable(EventData.GraphData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "GraphData received from ${it.sourceNodeId}")
                // Store in DataStore
                dataStoreScope.launch {
                    complicationDataRepository.updateGraphData(it)
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
            }
        disposable += rxBus
            .toObservable(EventData.TreatmentData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "TreatmentData received from ${it.sourceNodeId}")
                // Store in DataStore
                dataStoreScope.launch {
                    complicationDataRepository.updateTreatmentData(it)
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
            }
        disposable += rxBus
            .toObservable(EventData.Preferences::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Preferences received from ${it.sourceNodeId}")
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
        disposable += rxBus
            .toObservable(EventData.QuickWizard::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "QuickWizard received from ${it.sourceNodeId}")
                val serialized = it.serialize()
                if (serialized != sp.getString(R.string.key_quick_wizard_data, "")) {
                    sp.putString(R.string.key_quick_wizard_data, serialized)
                    TileService.getUpdater(context).requestUpdate(QuickWizardTileService::class.java)
                }
            }
        disposable += rxBus
            .toObservable(EventData.UserAction::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "UserAction received from ${it.sourceNodeId}")
                val serialized = it.serialize()
                if (serialized != sp.getString(R.string.key_user_action_data, "")) {
                    sp.putString(R.string.key_user_action_data, serialized)
                    TileService.getUpdater(context).requestUpdate(UserActionTileService::class.java)
                }
            }
        disposable += rxBus
            .toObservable(EventData.LoopStatesList::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Loop states received from ${it.sourceNodeId}")
                val serialized = it.serialize()
                if (serialized != sp.getString(R.string.key_loop_states_data, "")) {
                    sp.putString(R.string.key_loop_states_data, serialized)
                    TileService.getUpdater(context).requestUpdate(LoopStateTileService::class.java)
                }
            }
        disposable += rxBus
            .toObservable(EventData.ActionSetCustomWatchface::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Custom Watchface received from ${it.sourceNodeId}")
                dataStoreScope.launch {
                    complicationDataRepository.storeCustomWatchface(it.customWatchfaceData)
                    complicationDataRepository.getSimplifiedCustomWatchface()?.let { cwf ->
                        rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(cwf), false)))
                    }
                }
            }
        disposable += rxBus
            .toObservable(EventData.ActionUpdateCustomWatchface::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Custom Watchface metadata update received from ${it.sourceNodeId}")
                dataStoreScope.launch {
                    complicationDataRepository.updateCustomWatchfaceMetadata(it.customWatchfaceData.metadata)
                    complicationDataRepository.getSimplifiedCustomWatchface()?.let { cwf ->
                        rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(cwf), false)))
                    }
                }
            }
        disposable += rxBus
            .toObservable(EventData.ActionrequestSetDefaultWatchface::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Set Default Watchface received from ${it.sourceNodeId}")
                dataStoreScope.launch {
                    complicationDataRepository.setDefaultWatchface()
                    complicationDataRepository.getCustomWatchface()?.let { cwf ->
                        rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(cwf), false)))
                    }
                }
            }
        disposable += rxBus
            .toObservable(EventData.ActionrequestCustomWatchface::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { eventData ->
                aapsLogger.debug(LTag.WEAR, "Custom Watchface requested from ${eventData.sourceNodeId} export ${eventData.exportFile}")
                dataStoreScope.launch {
                    complicationDataRepository.getSimplifiedCustomWatchface(eventData.exportFile)?.let { cwf ->
                        rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(EventData.ActionSetCustomWatchface(cwf), eventData.exportFile)))
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

package info.nightscout.androidaps.comm

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.WearableListenerService
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.WatchfaceConfigurationActivity
import info.nightscout.androidaps.interaction.actions.AcceptActivity
import info.nightscout.androidaps.interaction.actions.ProfileSwitchActivity
import info.nightscout.androidaps.interaction.utils.Persistence
import info.nightscout.androidaps.tile.ActionsTileService
import info.nightscout.androidaps.tile.QuickWizardTileService
import info.nightscout.androidaps.tile.TempTargetTileService
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventWearDataToMobile
import info.nightscout.rx.events.EventWearToMobile
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.rx.weardata.EventData
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataHandlerWear @Inject constructor(
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val persistence: Persistence
) {

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
                aapsLogger.debug(LTag.WEAR, "Status received from ${it.sourceNodeId}")
                persistence.store(it)
                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
            }
        disposable += rxBus
            .toObservable(EventData.SingleBg::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "SingleBg received from ${it.sourceNodeId}")
                persistence.store(it)
            }
        disposable += rxBus
            .toObservable(EventData.GraphData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "GraphData received from ${it.sourceNodeId}")
                persistence.store(it)
            }
        disposable += rxBus
            .toObservable(EventData.TreatmentData::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "TreatmentData received from ${it.sourceNodeId}")
                persistence.store(it)
            }
        disposable += rxBus
            .toObservable(EventData.Preferences::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Preferences received from ${it.sourceNodeId}")
                if (it.wearControl != sp.getBoolean(R.string.key_wear_control, false)) {
                    sp.putBoolean(R.string.key_wear_control, it.wearControl)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        TileService.getUpdater(context).requestUpdate(ActionsTileService::class.java)
                        TileService.getUpdater(context).requestUpdate(TempTargetTileService::class.java)
                        TileService.getUpdater(context).requestUpdate(QuickWizardTileService::class.java)
                    }
                }
                sp.putBoolean(R.string.key_units_mgdl, it.unitsMgdl)
                sp.putInt(R.string.key_bolus_wizard_percentage, it.bolusPercentage)
                sp.putInt(R.string.key_treatments_safety_max_carbs, it.maxCarbs)
                sp.putDouble(R.string.key_treatments_safety_max_bolus, it.maxBolus)
                sp.putDouble(info.nightscout.shared.R.string.key_insulin_button_increment_1, it.insulinButtonIncrement1)
                sp.putDouble(info.nightscout.shared.R.string.key_insulin_button_increment_2, it.insulinButtonIncrement2)
                sp.putInt(R.string.key_carbs_button_increment_1, it.carbsButtonIncrement1)
                sp.putInt(R.string.key_carbs_button_increment_2, it.carbsButtonIncrement2)
            }
        disposable += rxBus
            .toObservable(EventData.QuickWizard::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "QuickWizard received from ${it.sourceNodeId}")
                val serialized = it.serialize()
                if (serialized != sp.getString(R.string.key_quick_wizard_data, "")) {
                    sp.putString(R.string.key_quick_wizard_data, serialized)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        TileService.getUpdater(context).requestUpdate(QuickWizardTileService::class.java)
                }
            }
        disposable += rxBus
            .toObservable(EventData.ActionSetCustomWatchface::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Custom Watchface received from ${it.sourceNodeId}")
                persistence.store(it)
                persistence.readCustomWatchface()?.let {
                    rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(it, false)))
                }
            }
        disposable += rxBus
            .toObservable(EventData.ActionrequestSetDefaultWatchface::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                aapsLogger.debug(LTag.WEAR, "Set Default Watchface received from ${it.sourceNodeId}")
                persistence.setDefaultWatchface()
                persistence.readCustomWatchface()?.let {
                    rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(it, false)))
                }
            }
        disposable += rxBus
            .toObservable(EventData.ActionrequestCustomWatchface::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { eventData ->
                aapsLogger.debug(LTag.WEAR, "Custom Watchface requested from ${eventData.sourceNodeId}")
                persistence.readCustomWatchface()?.let {
                    rxBus.send(EventWearDataToMobile(EventData.ActionGetCustomWatchface(it, eventData.exportFile)))
                }
            }
    }

    private fun handleBolusProgress(bolusProgress: EventData.BolusProgress) {
        val vibratePattern: LongArray
        val vibrate = sp.getBoolean("vibrateOnBolus", true)
        vibratePattern = if (vibrate) longArrayOf(0, 50, 1000) else longArrayOf(0, 1, 1000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createBolusProgressChannels()
        }
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

    @TargetApi(value = 26) private fun createBolusProgressChannels() {
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

    @TargetApi(value = 26) private fun createNotificationChannel(vibratePattern: LongArray, channelID: String, name: CharSequence, description: String, importance: Int) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "AAPS Open Loop"
            val description = "Open Loop request notification"
            val channel = NotificationChannel(DataLayerListenerServiceWear.AAPS_NOTIFY_CHANNEL_ID_OPEN_LOOP, name, NotificationManager.IMPORTANCE_HIGH)
            channel.description = description
            channel.enableVibration(true)

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        @Suppress("DEPRECATION")
        var builder = NotificationCompat.Builder(context, DataLayerListenerServiceWear.AAPS_NOTIFY_CHANNEL_ID_OPEN_LOOP)
            .setSmallIcon(R.drawable.notif_icon)
            .setContentTitle(command.title)
            .setContentText(command.message)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
            .setPriority(Notification.PRIORITY_HIGH) // suppress deprecation, ignored for API >= 26

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
}

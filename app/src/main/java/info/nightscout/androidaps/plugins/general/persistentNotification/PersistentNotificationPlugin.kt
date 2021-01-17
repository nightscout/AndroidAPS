package info.nightscout.androidaps.plugins.general.persistentNotification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.androidNotification.NotificationHolder
import info.nightscout.androidaps.utils.resources.IconsProvider
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class PersistentNotificationPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val fabricPrivacy: FabricPrivacy,
    private val activePlugins: ActivePluginProvider,
    private val iobCobCalculatorPlugin: IobCobCalculatorPlugin,
    private val rxBus: RxBusWrapper,
    private val context: Context,
    private val notificationHolder: NotificationHolder,
    private val dummyServiceHelper: DummyServiceHelper,
    private val iconsProvider: IconsProvider,
    private val databaseHelper: DatabaseHelperInterface
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .neverVisible(true)
    .pluginName(R.string.ongoingnotificaction)
    .enableByDefault(true)
    .alwaysEnabled(true)
    .showInList(false)
    .description(R.string.description_persistent_notification),
    aapsLogger, resourceHelper, injector
) {

    // For Android Auto
    // Intents are not declared in manifest and not consumed, this is intentionally because actually we can't do anything with
    private val PACKAGE = "info.nightscout"
    private val READ_ACTION = "info.nightscout.androidaps.ACTION_MESSAGE_READ"
    private val REPLY_ACTION = "info.nightscout.androidaps.ACTION_MESSAGE_REPLY"
    private val CONVERSATION_ID = "conversation_id"
    private val EXTRA_VOICE_REPLY = "extra_voice_reply"
    // End Android auto

    private val disposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        createNotificationChannel() // make sure channels exist before triggering updates through the bus
        disposable.add(rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ triggerNotificationUpdate() }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ triggerNotificationUpdate() }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ triggerNotificationUpdate() }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ triggerNotificationUpdate() }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ triggerNotificationUpdate() }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventNewBasalProfile::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ triggerNotificationUpdate() }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ triggerNotificationUpdate() }) { fabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ triggerNotificationUpdate() }) { fabricPrivacy.logException(it) })
        triggerNotificationUpdate()
    }

    private fun createNotificationChannel() {
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(notificationHolder.channelID, notificationHolder.channelID as CharSequence, NotificationManager.IMPORTANCE_HIGH)
        mNotificationManager.createNotificationChannel(channel)
    }

    override fun onStop() {
        disposable.clear()
        dummyServiceHelper.stopService(context)
        super.onStop()
    }

    private fun triggerNotificationUpdate() {
        updateNotification()
        dummyServiceHelper.startService(context)
    }

    private fun updateNotification() {
        val pump = activePlugins.activePump
        var line1: String?
        var line2: String? = null
        var line3: String? = null
        var unreadConversationBuilder: NotificationCompat.CarExtender.UnreadConversation.Builder? = null
        if (profileFunction.isProfileValid("Notification")) {
            var line1aa: String
            val units = profileFunction.getUnits()
            val lastBG = iobCobCalculatorPlugin.lastBg()
            val glucoseStatus = GlucoseStatus(injector).glucoseStatusData
            if (lastBG != null) {
                line1aa = lastBG.valueToUnitsToString(units)
                line1 = line1aa
                if (glucoseStatus != null) {
                    line1 += ("  Δ" + Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
                        + " avgΔ" + Profile.toSignedUnitsString(glucoseStatus.avgdelta, glucoseStatus.avgdelta * Constants.MGDL_TO_MMOLL, units))
                    line1aa += "  " + lastBG.directionToSymbol(databaseHelper)
                } else {
                    line1 += " " +
                        resourceHelper.gs(R.string.old_data) +
                        " "
                    line1aa += "$line1."
                }
            } else {
                line1aa = resourceHelper.gs(R.string.missed_bg_readings)
                line1 = line1aa
            }
            val activeTemp = activePlugins.activeTreatments.getTempBasalFromHistory(System.currentTimeMillis())
            if (activeTemp != null) {
                line1 += "  " + activeTemp.toStringShort()
                line1aa += "  " + activeTemp.toStringShort() + "."
            }
            //IOB
            activePlugins.activeTreatments.updateTotalIOBTreatments()
            activePlugins.activeTreatments.updateTotalIOBTempBasals()
            val bolusIob = activePlugins.activeTreatments.lastCalculationTreatments.round()
            val basalIob = activePlugins.activeTreatments.lastCalculationTempBasals.round()
            line2 = resourceHelper.gs(R.string.treatments_iob_label_string) + " " + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U " + resourceHelper.gs(R.string.cob) + ": " + iobCobCalculatorPlugin.getCobInfo(false, "PersistentNotificationPlugin").generateCOBString()
            val line2aa = resourceHelper.gs(R.string.treatments_iob_label_string) + " " + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U. " + resourceHelper.gs(R.string.cob) + ": " + iobCobCalculatorPlugin.getCobInfo(false, "PersistentNotificationPlugin").generateCOBString() + "."
            line3 = DecimalFormatter.to2Decimal(pump.baseBasalRate) + " U/h"
            var line3aa = DecimalFormatter.to2Decimal(pump.baseBasalRate) + " U/h."
            line3 += " - " + profileFunction.getProfileName()
            line3aa += " - " + profileFunction.getProfileName() + "."
            /// For Android Auto
            val msgReadIntent = Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(READ_ACTION)
                .putExtra(CONVERSATION_ID, notificationHolder.notificationID)
                .setPackage(PACKAGE)
            val msgReadPendingIntent = PendingIntent.getBroadcast(context,
                notificationHolder.notificationID,
                msgReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)
            val msgReplyIntent = Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(REPLY_ACTION)
                .putExtra(CONVERSATION_ID, notificationHolder.notificationID)
                .setPackage(PACKAGE)
            val msgReplyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationHolder.notificationID,
                msgReplyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)
            // Build a RemoteInput for receiving voice input from devices
            val remoteInput = RemoteInput.Builder(EXTRA_VOICE_REPLY).build()
            // Create the UnreadConversation
            unreadConversationBuilder = NotificationCompat.CarExtender.UnreadConversation.Builder(line1aa + "\n" + line2aa)
                .setLatestTimestamp(System.currentTimeMillis())
                .setReadPendingIntent(msgReadPendingIntent)
                .setReplyAction(msgReplyPendingIntent, remoteInput)
            /// Add dot to produce a "more natural sounding result"
            unreadConversationBuilder.addMessage(line3aa)
            /// End Android Auto
        } else {
            line1 = resourceHelper.gs(R.string.noprofileset)
        }
        val builder = NotificationCompat.Builder(context, notificationHolder.channelID)
        builder.setOngoing(true)
        builder.setOnlyAlertOnce(true)
        builder.setCategory(NotificationCompat.CATEGORY_STATUS)
        builder.setSmallIcon(iconsProvider.getNotificationIcon())
        builder.setLargeIcon(resourceHelper.decodeResource(iconsProvider.getIcon()))
        if (line1 != null) builder.setContentTitle(line1)
        if (line2 != null) builder.setContentText(line2)
        if (line3 != null) builder.setSubText(line3)
        /// Android Auto
        if (unreadConversationBuilder != null) {
            builder.extend(NotificationCompat.CarExtender()
                .setUnreadConversation(unreadConversationBuilder.build()))
        }
        /// End Android Auto
        val resultIntent = Intent(context, MainActivity::class.java)
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(MainActivity::class.java)
        stackBuilder.addNextIntent(resultIntent)
        val resultPendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(resultPendingIntent)
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = builder.build()
        mNotificationManager.notify(notificationHolder.notificationID, notification)
        notificationHolder.notification = notification
    }
}
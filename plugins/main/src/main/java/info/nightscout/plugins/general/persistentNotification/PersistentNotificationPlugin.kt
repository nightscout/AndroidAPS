package info.nightscout.plugins.general.persistentNotification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import app.aaps.core.main.extensions.toStringShort
import app.aaps.core.main.iob.generateCOBString
import app.aaps.core.main.iob.round
import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.utils.DecimalFormatter
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.R
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName", "DEPRECATION")
@Singleton
class PersistentNotificationPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val fabricPrivacy: FabricPrivacy,
    private val activePlugins: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val rxBus: RxBus,
    private val context: Context,
    private val notificationHolder: NotificationHolder,
    private val dummyServiceHelper: DummyServiceHelper,
    private val iconsProvider: IconsProvider,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val config: Config,
    private val decimalFormatter: DecimalFormatter
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .neverVisible(true)
        .pluginName(R.string.ongoingnotificaction)
        .enableByDefault(true)
        .alwaysEnabled(true)
        .showInList(false)
        .description(R.string.description_persistent_notification),
    aapsLogger, rh, injector
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
        notificationHolder.createNotificationChannel()
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ triggerNotificationUpdate() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ triggerNotificationUpdate() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ triggerNotificationUpdate() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ triggerNotificationUpdate() }, fabricPrivacy::logException)
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
        if (!config.appInitialized) return
        val pump = activePlugins.activePump
        var line1: String?
        var line2: String? = null
        var line3: String? = null
        var unreadConversationBuilder: NotificationCompat.CarExtender.UnreadConversation.Builder? = null
        if (profileFunction.isProfileValid("Notification")) {
            var line1aa: String
            val lastBG = iobCobCalculator.ads.lastBg()
            val glucoseStatus = glucoseStatusProvider.glucoseStatusData
            if (lastBG != null) {
                line1aa = profileUtil.fromMgdlToStringInUnits(lastBG.value)
                line1 = line1aa
                if (glucoseStatus != null) {
                    line1 += ("  Δ" + profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta)
                        + " avgΔ" + profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.shortAvgDelta))
                    line1aa += "  " + lastBG.trendArrow.symbol
                } else {
                    line1 += " " +
                        rh.gs(R.string.old_data) +
                        " "
                    line1aa += "$line1."
                }
            } else {
                line1aa = rh.gs(app.aaps.core.ui.R.string.missed_bg_readings)
                line1 = line1aa
            }
            val activeTemp = iobCobCalculator.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())
            if (activeTemp != null) {
                line1 += "  " + activeTemp.toStringShort(decimalFormatter)
                line1aa += "  " + activeTemp.toStringShort(decimalFormatter) + "."
            }
            //IOB
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            line2 =
                rh.gs(app.aaps.core.ui.R.string.treatments_iob_label_string) + " " + decimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U " + rh.gs(
                    app.aaps.core.ui.R
                        .string.cob
                ) + ": " + iobCobCalculator.getCobInfo(
                    "PersistentNotificationPlugin"
                ).generateCOBString(decimalFormatter)
            val line2aa =
                rh.gs(app.aaps.core.ui.R.string.treatments_iob_label_string) + " " + decimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U. " + rh.gs(
                    app.aaps.core.ui.R
                        .string.cob
                ) + ": " + iobCobCalculator.getCobInfo(
                    "PersistentNotificationPlugin"
                ).generateCOBString(decimalFormatter) + "."
            line3 = decimalFormatter.to2Decimal(pump.baseBasalRate) + " U/h"
            var line3aa = decimalFormatter.to2Decimal(pump.baseBasalRate) + " U/h."
            line3 += " - " + profileFunction.getProfileName()
            line3aa += " - " + profileFunction.getProfileName() + "."
            /// For Android Auto
            val msgReadIntent = Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(READ_ACTION)
                .putExtra(CONVERSATION_ID, notificationHolder.notificationID)
                .setPackage(PACKAGE)
            val msgReadPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationHolder.notificationID,
                msgReadIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val msgReplyIntent = Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(REPLY_ACTION)
                .putExtra(CONVERSATION_ID, notificationHolder.notificationID)
                .setPackage(PACKAGE)
            val msgReplyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationHolder.notificationID,
                msgReplyIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
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
            line1 = rh.gs(app.aaps.core.ui.R.string.no_profile_set)
        }
        val builder = NotificationCompat.Builder(context, notificationHolder.channelID)
        builder.setOngoing(true)
        builder.setOnlyAlertOnce(true)
        builder.setCategory(NotificationCompat.CATEGORY_STATUS)
        builder.setSmallIcon(iconsProvider.getNotificationIcon())
        builder.setLargeIcon(rh.decodeResource(iconsProvider.getIcon()))
        builder.setContentTitle(line1)
        if (line2 != null) builder.setContentText(line2)
        if (line3 != null) builder.setSubText(line3)
        /// Android Auto
        if (unreadConversationBuilder != null) {
            builder.extend(
                NotificationCompat.CarExtender()
                    .setUnreadConversation(unreadConversationBuilder.build())
            )
        }
        /// End Android Auto
        builder.setContentIntent(notificationHolder.openAppIntent(context))
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = builder.build()
        mNotificationManager.notify(notificationHolder.notificationID, notification)
        notificationHolder.notification = notification
    }
}

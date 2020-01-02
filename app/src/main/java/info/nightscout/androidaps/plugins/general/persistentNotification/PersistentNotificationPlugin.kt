package info.nightscout.androidaps.plugins.general.persistentNotification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.TaskStackBuilder
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.DatabaseHelper
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentNotificationPlugin @Inject constructor() : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .neverVisible(true)
    .pluginName(R.string.ongoingnotificaction)
    .enableByDefault(true)
    .alwaysEnabled(true)
    .showInList(false)
    .description(R.string.description_persistent_notification)
) {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    // For Android Auto
    // Intents are not declared in manifest and not consumed, this is intentionally because actually we can't do anything with
    private val PACKAGE = "info.nightscout"
    private val READ_ACTION = "info.nightscout.androidaps.ACTION_MESSAGE_READ"
    private val REPLY_ACTION = "info.nightscout.androidaps.ACTION_MESSAGE_REPLY"
    private val CONVERSATION_ID = "conversation_id"
    private val EXTRA_VOICE_REPLY = "extra_voice_reply"
    // End Android auto

    private val disposable = CompositeDisposable()
    private var notification: Notification? = null

    val CHANNEL_ID = "AndroidAPS-Ongoing"
    val ONGOING_NOTIFICATION_ID = 4711

    override fun onStart() {
        super.onStart()
        createNotificationChannel() // make sure channels exist before triggering updates through the bus
        disposable.add(rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ triggerNotificationUpdate(false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventExtendedBolusChange? -> triggerNotificationUpdate(false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventTempBasalChange? -> triggerNotificationUpdate(false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventTreatmentChange? -> triggerNotificationUpdate(false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventInitializationChanged? -> triggerNotificationUpdate(false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventNewBasalProfile::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventNewBasalProfile? -> triggerNotificationUpdate(false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventAutosensCalculationFinished? -> triggerNotificationUpdate(false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventPreferenceChange? -> triggerNotificationUpdate(false) }) { FabricPrivacy.logException(it) })
        triggerNotificationUpdate(true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mNotificationManager = mainApp.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_ID as CharSequence, NotificationManager.IMPORTANCE_HIGH)
            mNotificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStop() {
        disposable.clear()
        mainApp.stopService(Intent(mainApp, DummyService::class.java))
        super.onStop()
    }

    private fun triggerNotificationUpdate(boot: Boolean) {
        updateNotification(boot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            mainApp.startForegroundService(Intent(mainApp, DummyService::class.java))
        else
            mainApp.startService(Intent(mainApp, DummyService::class.java))
    }

    private fun updateNotification(boot: Boolean) {
        val pump = configBuilderPlugin.activePump ?: return
        var line1: String?
        var line2: String? = null
        var line3: String? = null
        var unreadConversationBuilder: NotificationCompat.CarExtender.UnreadConversation.Builder? = null
        if (boot) {
            line1 = resourceHelper.gs(R.string.loading)
        } else if (profileFunction.isProfileValid("Notification")) {
            var line1_aa: String
            val units = profileFunction.getUnits()
            val lastBG = DatabaseHelper.lastBg()
            val glucoseStatus = GlucoseStatus.getGlucoseStatusData()
            if (lastBG != null) {
                line1_aa = lastBG.valueToUnitsToString(units)
                line1 = line1_aa
                if (glucoseStatus != null) {
                    line1 += ("  Δ" + Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units)
                        + " avgΔ" + Profile.toSignedUnitsString(glucoseStatus.avgdelta, glucoseStatus.avgdelta * Constants.MGDL_TO_MMOLL, units))
                    line1_aa += "  " + lastBG.directionToSymbol()
                } else {
                    line1 += " " +
                        resourceHelper.gs(R.string.old_data) +
                        " "
                    line1_aa += "$line1."
                }
            } else {
                line1_aa = resourceHelper.gs(R.string.missed_bg_readings)
                line1 = line1_aa
            }
            val activeTemp = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis())
            if (activeTemp != null) {
                line1 += "  " + activeTemp.toStringShort()
                line1_aa += "  " + activeTemp.toStringShort() + "."
            }
            //IOB
            treatmentsPlugin.updateTotalIOBTreatments()
            treatmentsPlugin.updateTotalIOBTempBasals()
            val bolusIob = treatmentsPlugin.lastCalculationTreatments.round()
            val basalIob = treatmentsPlugin.lastCalculationTempBasals.round()
            line2 = resourceHelper.gs(R.string.treatments_iob_label_string) + " " + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U " + resourceHelper.gs(R.string.cob) + ": " + iobCobCalculatorPlugin.getCobInfo(false, "PersistentNotificationPlugin").generateCOBString()
            val line2_aa = resourceHelper.gs(R.string.treatments_iob_label_string) + " " + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U. " + resourceHelper.gs(R.string.cob) + ": " + iobCobCalculatorPlugin.getCobInfo(false, "PersistentNotificationPlugin").generateCOBString() + "."
            line3 = DecimalFormatter.to2Decimal(pump.baseBasalRate) + " U/h"
            var line3_aa = DecimalFormatter.to2Decimal(pump.baseBasalRate) + " U/h."
            line3 += " - " + profileFunction.getProfileName()
            line3_aa += " - " + profileFunction.getProfileName() + "."
            /// For Android Auto
            val msgReadIntent = Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(READ_ACTION)
                .putExtra(CONVERSATION_ID, ONGOING_NOTIFICATION_ID)
                .setPackage(PACKAGE)
            val msgReadPendingIntent = PendingIntent.getBroadcast(mainApp,
                ONGOING_NOTIFICATION_ID,
                msgReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)
            val msgReplyIntent = Intent()
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .setAction(REPLY_ACTION)
                .putExtra(CONVERSATION_ID, ONGOING_NOTIFICATION_ID)
                .setPackage(PACKAGE)
            val msgReplyPendingIntent = PendingIntent.getBroadcast(
                mainApp,
                ONGOING_NOTIFICATION_ID,
                msgReplyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)
            // Build a RemoteInput for receiving voice input from devices
            val remoteInput = RemoteInput.Builder(EXTRA_VOICE_REPLY).build()
            // Create the UnreadConversation
            unreadConversationBuilder = NotificationCompat.CarExtender.UnreadConversation.Builder(line1_aa + "\n" + line2_aa)
                .setLatestTimestamp(System.currentTimeMillis())
                .setReadPendingIntent(msgReadPendingIntent)
                .setReplyAction(msgReplyPendingIntent, remoteInput)
            /// Add dot to produce a "more natural sounding result"
            unreadConversationBuilder.addMessage(line3_aa)
            /// End Android Auto
        } else {
            line1 = resourceHelper.gs(R.string.noprofileset)
        }
        val builder = NotificationCompat.Builder(mainApp, CHANNEL_ID)
        builder.setOngoing(true)
        builder.setOnlyAlertOnce(true)
        builder.setCategory(NotificationCompat.CATEGORY_STATUS)
        builder.setSmallIcon(resourceHelper.getNotificationIcon())
        builder.setLargeIcon(resourceHelper.decodeResource(resourceHelper.getIcon()))
        if (line1 != null) builder.setContentTitle(line1)
        if (line2 != null) builder.setContentText(line2)
        if (line3 != null) builder.setSubText(line3)
        /// Android Auto
        if (unreadConversationBuilder != null) {
            builder.extend(NotificationCompat.CarExtender()
                .setUnreadConversation(unreadConversationBuilder.build()))
        }
        /// End Android Auto
        val resultIntent = Intent(mainApp, MainActivity::class.java)
        val stackBuilder = TaskStackBuilder.create(mainApp)
        stackBuilder.addParentStack(MainActivity::class.java)
        stackBuilder.addNextIntent(resultIntent)
        val resultPendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(resultPendingIntent)
        val mNotificationManager = mainApp.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = builder.build()
        mNotificationManager.notify(ONGOING_NOTIFICATION_ID, notification)
        this.notification = notification
    }

    fun getLastNotification(): Notification {
        if (notification == null)
            updateNotification(true)
        return notification!!
    }
}
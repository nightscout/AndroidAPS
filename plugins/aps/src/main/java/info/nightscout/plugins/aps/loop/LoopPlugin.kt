package info.nightscout.plugins.aps.loop

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.core.events.EventNewNotification
import info.nightscout.core.extensions.convertedToAbsolute
import info.nightscout.core.extensions.convertedToPercent
import info.nightscout.core.extensions.plannedRemainingMinutes
import info.nightscout.core.iob.json
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.DeviceStatus
import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertAndCancelCurrentOfflineEventTransaction
import info.nightscout.database.impl.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.ApsMode
import info.nightscout.interfaces.aps.APSResult
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.aps.Loop.LastRun
import info.nightscout.interfaces.configBuilder.RunningConfiguration
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.Pump
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.VirtualPump
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.receivers.ReceiverStatusStore
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.plugins.aps.R
import info.nightscout.plugins.aps.loop.events.EventLoopSetLastRunGui
import info.nightscout.plugins.aps.loop.extensions.json
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAcceptOpenLoopChange
import info.nightscout.rx.events.EventDismissNotification
import info.nightscout.rx.events.EventLoopUpdateGui
import info.nightscout.rx.events.EventMobileToWear
import info.nightscout.rx.events.EventNewOpenLoopNotification
import info.nightscout.rx.events.EventTempTargetChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.rx.weardata.EventData
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@OpenForTesting
@Singleton
class LoopPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    private val sp: SP,
    private val config: Config,
    private val constraintChecker: Constraints,
    rh: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val context: Context,
    private val commandQueue: CommandQueue,
    private val activePlugin: ActivePlugin,
    private val virtualPump: VirtualPump,
    private val iobCobCalculator: IobCobCalculator,
    private val receiverStatusStore: ReceiverStatusStore,
    private val fabricPrivacy: FabricPrivacy,
    private val dateUtil: DateUtil,
    private val uel: UserEntryLogger,
    private val repository: AppRepository,
    private val runningConfiguration: RunningConfiguration,
    private val uiInteraction: UiInteraction
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.LOOP)
        .fragmentClass(LoopFragment::class.java.name)
        .pluginIcon(info.nightscout.core.main.R.drawable.ic_loop_closed_white)
        .pluginName(info.nightscout.core.ui.R.string.loop)
        .shortName(R.string.loop_shortname)
        .preferencesId(R.xml.pref_loop)
        .enableByDefault(config.APS)
        .description(R.string.description_loop),
    aapsLogger, rh, injector
), Loop {

    private val disposable = CompositeDisposable()
    override var lastBgTriggeredRun: Long = 0
    private var carbsSuggestionsSuspendedUntil: Long = 0
    private var prevCarbsreq = 0
    override var lastRun: LastRun? = null
    override var closedLoopEnabled: Constraint<Boolean>? = null

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    override fun onStart() {
        createNotificationChannel()
        super.onStart()
        disposable += rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ invoke("EventTempTargetChange", true) }, fabricPrivacy::logException)
    }

    private fun createNotificationChannel() {
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        @SuppressLint("WrongConstant") val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_HIGH
        )
        mNotificationManager.createNotificationChannel(channel)
    }

    override fun onStop() {
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
        super.onStop()
    }

    override fun specialEnableCondition(): Boolean {
        return try {
            val pump = activePlugin.activePump
            pump.pumpDescription.isTempBasalCapable
        } catch (ignored: Exception) {
            // may fail during initialization
            true
        }
    }

    override fun minutesToEndOfSuspend(): Int {
        val offlineEventWrapped = repository.getOfflineEventActiveAt(dateUtil.now()).blockingGet()
        return if (offlineEventWrapped is ValueWrapper.Existing) T.msecs(offlineEventWrapped.value.timestamp + offlineEventWrapped.value.duration - dateUtil.now()).mins().toInt()
        else 0
    }

    override val isSuspended: Boolean
        get() = repository.getOfflineEventActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing

    override var enabled: Boolean
        get() = isEnabled()
        set(value) {
            setPluginEnabled(PluginType.LOOP, value)
        }

    override val isLGS: Boolean
        get() {
            val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()
            val maxIobAllowed = constraintChecker.getMaxIOBAllowed().value()
            val apsMode = ApsMode.fromString(sp.getString(info.nightscout.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name))
            val pump = activePlugin.activePump
            var isLGS = false
            if (!isSuspended && !pump.isSuspended()) if (closedLoopEnabled.value()) if (maxIobAllowed == HardLimits.MAX_IOB_LGS || apsMode == ApsMode.LGS) isLGS = true
            return isLGS
        }

    override val isSuperBolus: Boolean
        get() {
            val offlineEventWrapped = repository.getOfflineEventActiveAt(dateUtil.now()).blockingGet()
            return offlineEventWrapped is ValueWrapper.Existing && offlineEventWrapped.value.reason == OfflineEvent.Reason.SUPER_BOLUS
        }

    override val isDisconnected: Boolean
        get() {
            val offlineEventWrapped = repository.getOfflineEventActiveAt(dateUtil.now()).blockingGet()
            return offlineEventWrapped is ValueWrapper.Existing && offlineEventWrapped.value.reason == OfflineEvent.Reason.DISCONNECT_PUMP
        }

    @Suppress("SameParameterValue")
    private fun treatmentTimeThreshold(durationMinutes: Int): Boolean {
        val threshold = System.currentTimeMillis() + durationMinutes * 60 * 1000
        var bool = false
        val lastBolusTime = repository.getLastBolusRecord()?.timestamp ?: 0L
        val lastCarbsTime = repository.getLastCarbsRecord()?.timestamp ?: 0L
        if (lastBolusTime > threshold || lastCarbsTime > threshold) bool = true
        return bool
    }

    @Synchronized
    fun isEmptyQueue(): Boolean {
        val maxMinutes = 2L
        val start = dateUtil.now()
        while (start + T.mins(maxMinutes).msecs() > dateUtil.now()) {
            if (commandQueue.size() == 0 && commandQueue.performing() == null) return true
            SystemClock.sleep(100)
        }
        return false
    }

    @Synchronized
    override fun invoke(initiator: String, allowNotification: Boolean, tempBasalFallback: Boolean) {
        try {
            aapsLogger.debug(LTag.APS, "invoke from $initiator")
            val loopEnabled = constraintChecker.isLoopInvocationAllowed()
            if (!loopEnabled.value()) {
                val message = """
                    ${rh.gs(info.nightscout.core.ui.R.string.loop_disabled)}
                    ${loopEnabled.getReasons(aapsLogger)}
                    """.trimIndent()
                aapsLogger.debug(LTag.APS, message)
                rxBus.send(EventLoopSetLastRunGui(message))
                return
            }
            val pump = activePlugin.activePump
            var apsResult: APSResult? = null
            if (!isEnabled(PluginType.LOOP)) return
            val profile = profileFunction.getProfile()
            if (profile == null || !profileFunction.isProfileValid("Loop")) {
                aapsLogger.debug(LTag.APS, rh.gs(info.nightscout.core.ui.R.string.no_profile_set))
                rxBus.send(EventLoopSetLastRunGui(rh.gs(info.nightscout.core.ui.R.string.no_profile_set)))
                return
            }

            // Check if pump info is loaded
            if (pump.baseBasalRate < 0.01) return
            val usedAPS = activePlugin.activeAPS
            if ((usedAPS as PluginBase).isEnabled()) {
                usedAPS.invoke(initiator, tempBasalFallback)
                apsResult = usedAPS.lastAPSResult
            }

            // Check if we have any result
            if (apsResult == null) {
                rxBus.send(EventLoopSetLastRunGui(rh.gs(R.string.no_aps_selected)))
                return
            }

            if (!isEmptyQueue()) {
                aapsLogger.debug(LTag.APS, rh.gs(info.nightscout.core.ui.R.string.pump_busy))
                rxBus.send(EventLoopSetLastRunGui(rh.gs(info.nightscout.core.ui.R.string.pump_busy)))
                return
            }

            // Prepare for pumps using % basals
            if (pump.pumpDescription.tempBasalStyle == PumpDescription.PERCENT && allowPercentage()) {
                apsResult.usePercent = true
            }
            apsResult.percent = (apsResult.rate / profile.getBasal() * 100).toInt()

            // check rate for constraints
            val resultAfterConstraints = apsResult.newAndClone(injector)
            resultAfterConstraints.rateConstraint = Constraint(resultAfterConstraints.rate)
            resultAfterConstraints.rate = constraintChecker.applyBasalConstraints(resultAfterConstraints.rateConstraint!!, profile).value()
            resultAfterConstraints.percentConstraint = Constraint(resultAfterConstraints.percent)
            resultAfterConstraints.percent = constraintChecker.applyBasalPercentConstraints(resultAfterConstraints.percentConstraint!!, profile).value()
            resultAfterConstraints.smbConstraint = Constraint(resultAfterConstraints.smb)
            resultAfterConstraints.smb = constraintChecker.applyBolusConstraints(resultAfterConstraints.smbConstraint!!).value()

            // safety check for multiple SMBs
            val lastBolusTime = repository.getLastBolusRecord()?.timestamp ?: 0L
            if (lastBolusTime != 0L && lastBolusTime + T.mins(3).msecs() > System.currentTimeMillis()) {
                aapsLogger.debug(LTag.APS, "SMB requested but still in 3 min interval")
                resultAfterConstraints.smb = 0.0
            }
            prevCarbsreq = lastRun?.constraintsProcessed?.carbsReq ?: prevCarbsreq
            lastRun = (lastRun ?: LastRun()).also { lastRun ->
                lastRun.request = apsResult
                lastRun.constraintsProcessed = resultAfterConstraints
                lastRun.lastAPSRun = dateUtil.now()
                lastRun.source = (usedAPS as PluginBase).name
                lastRun.tbrSetByPump = null
                lastRun.smbSetByPump = null
                lastRun.lastTBREnact = 0
                lastRun.lastTBRRequest = 0
                lastRun.lastSMBEnact = 0
                lastRun.lastSMBRequest = 0
                buildDeviceStatus(
                    dateUtil, this, iobCobCalculator, profileFunction,
                    activePlugin.activePump, receiverStatusStore, runningConfiguration,
                    config.VERSION_NAME + "-" + config.BUILD_VERSION
                )?.also {
                    repository.insert(it)
                }

                if (isSuspended) {
                    aapsLogger.debug(LTag.APS, rh.gs(info.nightscout.core.ui.R.string.loopsuspended))
                    rxBus.send(EventLoopSetLastRunGui(rh.gs(info.nightscout.core.ui.R.string.loopsuspended)))
                    return
                }
                if (pump.isSuspended()) {
                    aapsLogger.debug(LTag.APS, rh.gs(info.nightscout.core.ui.R.string.pumpsuspended))
                    rxBus.send(EventLoopSetLastRunGui(rh.gs(info.nightscout.core.ui.R.string.pumpsuspended)))
                    return
                }
                closedLoopEnabled = constraintChecker.isClosedLoopAllowed()
                if (closedLoopEnabled?.value() == true) {
                    if (allowNotification) {
                        if (resultAfterConstraints.isCarbsRequired
                            && resultAfterConstraints.carbsReq >= sp.getInt(
                                R.string.key_smb_enable_carbs_suggestions_threshold,
                                0
                            ) && carbsSuggestionsSuspendedUntil < System.currentTimeMillis() && !treatmentTimeThreshold(-15)
                        ) {
                            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_enable_carbs_required_alert_local, true) && !sp.getBoolean(info.nightscout.core.ui.R.string.key_raise_notifications_as_android_notifications, true)) {
                                val carbReqLocal = Notification(Notification.CARBS_REQUIRED, resultAfterConstraints.carbsRequiredText, Notification.NORMAL)
                                rxBus.send(EventNewNotification(carbReqLocal))
                            }
                            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_create_announcements_from_carbs_req, false)) {
                                disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(resultAfterConstraints.carbsRequiredText)).subscribe()
                            }
                            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_enable_carbs_required_alert_local, true) && sp.getBoolean(info.nightscout.core.ui.R.string.key_raise_notifications_as_android_notifications, true)) {
                                val intentAction5m = Intent(context, CarbSuggestionReceiver::class.java)
                                intentAction5m.putExtra("ignoreDuration", 5)
                                val pendingIntent5m = PendingIntent.getBroadcast(context, 1, intentAction5m, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                                val actionIgnore5m = NotificationCompat.Action(info.nightscout.core.main.R.drawable.ic_notif_aaps, rh.gs(R.string.ignore5m, "Ignore 5m"), pendingIntent5m)
                                val intentAction15m = Intent(context, CarbSuggestionReceiver::class.java)
                                intentAction15m.putExtra("ignoreDuration", 15)
                                val pendingIntent15m = PendingIntent.getBroadcast(context, 1, intentAction15m, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                                val actionIgnore15m = NotificationCompat.Action(info.nightscout.core.main.R.drawable.ic_notif_aaps, rh.gs(R.string.ignore15m, "Ignore 15m"), pendingIntent15m)
                                val intentAction30m = Intent(context, CarbSuggestionReceiver::class.java)
                                intentAction30m.putExtra("ignoreDuration", 30)
                                val pendingIntent30m = PendingIntent.getBroadcast(context, 1, intentAction30m, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                                val actionIgnore30m = NotificationCompat.Action(info.nightscout.core.main.R.drawable.ic_notif_aaps, rh.gs(R.string.ignore30m, "Ignore 30m"), pendingIntent30m)
                                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                                builder.setSmallIcon(info.nightscout.core.ui.R.drawable.notif_icon)
                                    .setContentTitle(rh.gs(R.string.carbs_suggestion))
                                    .setContentText(resultAfterConstraints.carbsRequiredText)
                                    .setAutoCancel(true)
                                    .setPriority(Notification.IMPORTANCE_HIGH)
                                    .setCategory(Notification.CATEGORY_ALARM)
                                    .addAction(actionIgnore5m)
                                    .addAction(actionIgnore15m)
                                    .addAction(actionIgnore30m)
                                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                    .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
                                val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                                // mId allows you to update the notification later on.
                                mNotificationManager.notify(Constants.notificationID, builder.build())
                                uel.log(
                                    Action.CAREPORTAL, Sources.Loop, rh.gs(info.nightscout.core.ui.R.string.carbsreq, resultAfterConstraints.carbsReq, resultAfterConstraints.carbsReqWithin),
                                    ValueWithUnit.Gram(resultAfterConstraints.carbsReq),
                                    ValueWithUnit.Minute(resultAfterConstraints.carbsReqWithin)
                                )
                                rxBus.send(EventNewOpenLoopNotification())

                                //only send to wear if Native notifications are turned off
                                if (!sp.getBoolean(info.nightscout.core.ui.R.string.key_raise_notifications_as_android_notifications, true)) {
                                    // Send to Wear
                                    sendToWear()
                                }
                            }
                        } else {
                            //If carbs were required previously, but are no longer needed, dismiss notifications
                            if (prevCarbsreq > 0) {
                                dismissSuggestion()
                                rxBus.send(EventDismissNotification(Notification.CARBS_REQUIRED))
                            }
                        }
                    }
                    if (resultAfterConstraints.isChangeRequested
                        && !commandQueue.bolusInQueue()
                    ) {
                        val waiting = PumpEnactResult(injector)
                        waiting.queued = true
                        if (resultAfterConstraints.isTempBasalRequested) lastRun.tbrSetByPump = waiting
                        if (resultAfterConstraints.isBolusRequested) lastRun.smbSetByPump =
                            waiting
                        rxBus.send(EventLoopUpdateGui())
                        fabricPrivacy.logCustom("APSRequest")
                        // TBR request must be applied first to prevent situation where
                        // SMB was executed and zero TBR afterwards failed
                        applyTBRRequest(resultAfterConstraints, profile, object : Callback() {
                            override fun run() {
                                if (result.enacted || result.success) {
                                    lastRun.tbrSetByPump = result
                                    lastRun.lastTBRRequest = lastRun.lastAPSRun
                                    lastRun.lastTBREnact = dateUtil.now()
                                    // deliverAt is used to prevent executing too old SMB request (older than 1 min)
                                    // executing TBR may take some time thus give more time to SMB
                                    resultAfterConstraints.deliverAt = lastRun.lastTBREnact
                                    rxBus.send(EventLoopUpdateGui())
                                    applySMBRequest(resultAfterConstraints, object : Callback() {
                                        override fun run() {
                                            // Callback is only called if a bolus was actually requested
                                            if (result.enacted || result.success) {
                                                lastRun.smbSetByPump = result
                                                lastRun.lastSMBRequest = lastRun.lastAPSRun
                                                lastRun.lastSMBEnact = dateUtil.now()
                                            } else {
                                                handler.postDelayed({ invoke("tempBasalFallback", allowNotification, true) }, 1000)
                                            }
                                            rxBus.send(EventLoopUpdateGui())
                                        }
                                    })
                                } else {
                                    lastRun.tbrSetByPump = result
                                    lastRun.lastTBRRequest = lastRun.lastAPSRun
                                }
                                rxBus.send(EventLoopUpdateGui())
                            }
                        })
                    } else {
                        lastRun.tbrSetByPump = null
                        lastRun.smbSetByPump = null
                    }
                } else {
                    if (resultAfterConstraints.isChangeRequested && allowNotification) {
                        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                        builder.setSmallIcon(info.nightscout.core.ui.R.drawable.notif_icon)
                            .setContentTitle(rh.gs(R.string.open_loop_new_suggestion))
                            .setContentText(resultAfterConstraints.toString())
                            .setAutoCancel(true)
                            .setPriority(Notification.IMPORTANCE_HIGH)
                            .setCategory(Notification.CATEGORY_ALARM)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_wear_control, false)) {
                            builder.setLocalOnly(true)
                        }
                        presentSuggestion(builder)
                    } else if (allowNotification) {
                        dismissSuggestion()
                    }
                }
                rxBus.send(EventLoopUpdateGui())
            }
        } finally {
            aapsLogger.debug(LTag.APS, "invoke end")
        }
    }

    override fun disableCarbSuggestions(durationMinutes: Int) {
        carbsSuggestionsSuspendedUntil = System.currentTimeMillis() + durationMinutes * 60 * 1000
        dismissSuggestion()
    }

    private fun presentSuggestion(builder: NotificationCompat.Builder) {
        // Creates an explicit intent for an Activity in your app
        val resultIntent = Intent(context, uiInteraction.mainActivity)

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(uiInteraction.mainActivity)
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent)
        val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(resultPendingIntent)
        builder.setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // mId allows you to update the notification later on.
        mNotificationManager.notify(Constants.notificationID, builder.build())
        rxBus.send(EventNewOpenLoopNotification())

        // Send to Wear
        sendToWear()
    }

    private fun dismissSuggestion() {
        // dismiss notifications
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(Constants.notificationID)
        rxBus.send(EventMobileToWear(EventData.CancelNotification(dateUtil.now())))
    }

    private fun sendToWear() {
        lastRun?.let {
            rxBus.send(
                EventMobileToWear(
                    EventData.OpenLoopRequest(
                        rh.gs(R.string.open_loop_new_suggestion),
                        it.constraintsProcessed.toString(),
                        EventData.OpenLoopRequestConfirmed(dateUtil.now())
                    )
                )
            )
        }
    }

    override fun acceptChangeRequest() {
        val profile = profileFunction.getProfile() ?: return
        lastRun?.let { lastRun ->
            lastRun.constraintsProcessed?.let { constraintsProcessed ->
                applyTBRRequest(constraintsProcessed, profile, object : Callback() {
                    override fun run() {
                        if (result.enacted) {
                            lastRun.tbrSetByPump = result
                            lastRun.lastTBRRequest = lastRun.lastAPSRun
                            lastRun.lastTBREnact = dateUtil.now()
                            lastRun.lastOpenModeAccept = dateUtil.now()
                            buildDeviceStatus(
                                dateUtil, this@LoopPlugin, iobCobCalculator, profileFunction,
                                activePlugin.activePump, receiverStatusStore, runningConfiguration,
                                config.VERSION_NAME + "-" + config.BUILD_VERSION
                            )?.also {
                                repository.insert(it)
                            }
                            sp.incInt(info.nightscout.core.utils.R.string.key_ObjectivesmanualEnacts)
                        }
                        rxBus.send(EventAcceptOpenLoopChange())
                    }
                })
            }
        }
        fabricPrivacy.logCustom("AcceptTemp")
    }

    /**
     * expect absolute request and allow both absolute and percent response based on pump capabilities
     * TODO: update pump drivers to support APS request in %
     */
    private fun applyTBRRequest(request: APSResult, profile: Profile, callback: Callback?) {
        if (!request.isTempBasalRequested) {
            callback?.result(PumpEnactResult(injector).enacted(false).success(true).comment(info.nightscout.core.ui.R.string.nochangerequested))?.run()
            return
        }
        val pump = activePlugin.activePump
        if (!pump.isInitialized()) {
            aapsLogger.debug(LTag.APS, "applyAPSRequest: " + rh.gs(R.string.pump_not_initialized))
            callback?.result(PumpEnactResult(injector).comment(R.string.pump_not_initialized).enacted(false).success(false))?.run()
            return
        }
        if (pump.isSuspended()) {
            aapsLogger.debug(LTag.APS, "applyAPSRequest: " + rh.gs(info.nightscout.core.ui.R.string.pumpsuspended))
            callback?.result(PumpEnactResult(injector).comment(info.nightscout.core.ui.R.string.pumpsuspended).enacted(false).success(false))?.run()
            return
        }
        aapsLogger.debug(LTag.APS, "applyAPSRequest: $request")
        val now = System.currentTimeMillis()
        val activeTemp = iobCobCalculator.getTempBasalIncludingConvertedExtended(now)
        if (request.rate == 0.0 && request.duration == 0 || abs(request.rate - pump.baseBasalRate) < pump.pumpDescription.basalStep) {
            if (activeTemp != null) {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: cancelTempBasal()")
                uel.log(Action.CANCEL_TEMP_BASAL, Sources.Loop)
                commandQueue.cancelTempBasal(false, callback)
            } else {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: Basal set correctly")
                callback?.result(
                    PumpEnactResult(injector).absolute(request.rate).duration(0)
                        .enacted(false).success(true).comment(R.string.basal_set_correctly)
                )?.run()
            }
        } else if (request.usePercent && allowPercentage()) {
            if (request.percent == 100 && request.duration == 0) {
                if (activeTemp != null) {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: cancelTempBasal()")
                    uel.log(Action.CANCEL_TEMP_BASAL, Sources.Loop)
                    commandQueue.cancelTempBasal(false, callback)
                } else {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: Basal set correctly")
                    callback?.result(
                        PumpEnactResult(injector).percent(request.percent).duration(0)
                            .enacted(false).success(true).comment(R.string.basal_set_correctly)
                    )?.run()
                }
            } else if (activeTemp != null && activeTemp.plannedRemainingMinutes > 5 && request.duration - activeTemp.plannedRemainingMinutes < 30 && request.percent == activeTemp.convertedToPercent(
                    now,
                    profile
                )
            ) {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: Temp basal set correctly")
                callback?.result(
                    PumpEnactResult(injector).percent(request.percent)
                        .enacted(false).success(true).duration(activeTemp.plannedRemainingMinutes)
                        .comment(info.nightscout.core.ui.R.string.let_temp_basal_run)
                )?.run()
            } else {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: tempBasalPercent()")
                uel.log(
                    Action.TEMP_BASAL, Sources.Loop,
                    ValueWithUnit.Percent(request.percent),
                    ValueWithUnit.Minute(request.duration)
                )
                commandQueue.tempBasalPercent(request.percent, request.duration, false, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
            }
        } else {
            if (activeTemp != null && activeTemp.plannedRemainingMinutes > 5 && request.duration - activeTemp.plannedRemainingMinutes < 30 && abs(
                    request.rate - activeTemp.convertedToAbsolute(
                        now,
                        profile
                    )
                ) < pump.pumpDescription.basalStep
            ) {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: Temp basal set correctly")
                callback?.result(
                    PumpEnactResult(injector).absolute(activeTemp.convertedToAbsolute(now, profile))
                        .enacted(false).success(true).duration(activeTemp.plannedRemainingMinutes)
                        .comment(info.nightscout.core.ui.R.string.let_temp_basal_run)
                )?.run()
            } else {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: setTempBasalAbsolute()")
                uel.log(
                    Action.TEMP_BASAL, Sources.Loop,
                    ValueWithUnit.UnitPerHour(request.rate),
                    ValueWithUnit.Minute(request.duration)
                )
                commandQueue.tempBasalAbsolute(request.rate, request.duration, false, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
            }
        }
    }

    private fun applySMBRequest(request: APSResult, callback: Callback?) {
        if (!request.isBolusRequested) {
            aapsLogger.debug(LTag.APS, "No SMB requested")
            return
        }
        val pump = activePlugin.activePump
        val lastBolusTime = repository.getLastBolusRecord()?.timestamp ?: 0L
        if (lastBolusTime != 0L && lastBolusTime + 3 * 60 * 1000 > System.currentTimeMillis()) {
            aapsLogger.debug(LTag.APS, "SMB requested but still in 3 min interval")
            callback?.result(
                PumpEnactResult(injector)
                    .comment(R.string.smb_frequency_exceeded)
                    .enacted(false).success(false)
            )?.run()
            return
        }
        if (!pump.isInitialized()) {
            aapsLogger.debug(LTag.APS, "applySMBRequest: " + rh.gs(R.string.pump_not_initialized))
            callback?.result(PumpEnactResult(injector).comment(R.string.pump_not_initialized).enacted(false).success(false))?.run()
            return
        }
        if (pump.isSuspended()) {
            aapsLogger.debug(LTag.APS, "applySMBRequest: " + rh.gs(info.nightscout.core.ui.R.string.pumpsuspended))
            callback?.result(PumpEnactResult(injector).comment(info.nightscout.core.ui.R.string.pumpsuspended).enacted(false).success(false))?.run()
            return
        }
        aapsLogger.debug(LTag.APS, "applySMBRequest: $request")

        // deliver SMB
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.lastKnownBolusTime = repository.getLastBolusRecord()?.timestamp ?: 0L
        detailedBolusInfo.eventType = DetailedBolusInfo.EventType.CORRECTION_BOLUS
        detailedBolusInfo.insulin = request.smb
        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.SMB
        detailedBolusInfo.deliverAtTheLatest = request.deliverAt
        aapsLogger.debug(LTag.APS, "applyAPSRequest: bolus()")
        if (request.smb > 0.0)
            uel.log(Action.SMB, Sources.Loop, ValueWithUnit.Insulin(detailedBolusInfo.insulin))
        commandQueue.bolus(detailedBolusInfo, callback)
    }

    private fun allowPercentage(): Boolean {
        return virtualPump.isEnabled()
    }

    override fun goToZeroTemp(durationInMinutes: Int, profile: Profile, reason: OfflineEvent.Reason) {
        val pump = activePlugin.activePump
        disposable += repository.runTransactionForResult(InsertAndCancelCurrentOfflineEventTransaction(dateUtil.now(), T.mins(durationInMinutes.toLong()).msecs(), reason))
            .subscribe({ result ->
                           result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                           result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $it") }
                       }, {
                           aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                       })
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
            commandQueue.tempBasalAbsolute(0.0, durationInMinutes, true, profile, PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND, object : Callback() {
                override fun run() {
                    if (!result.success) {
                        uiInteraction.runAlarm(result.comment, rh.gs(info.nightscout.core.ui.R.string.temp_basal_delivery_error), info.nightscout.core.ui.R.raw.boluserror)
                    }
                }
            })
        } else {
            commandQueue.tempBasalPercent(0, durationInMinutes, true, profile, PumpSync.TemporaryBasalType.EMULATED_PUMP_SUSPEND, object : Callback() {
                override fun run() {
                    if (!result.success) {
                        uiInteraction.runAlarm(result.comment, rh.gs(info.nightscout.core.ui.R.string.temp_basal_delivery_error), info.nightscout.core.ui.R.raw.boluserror)
                    }
                }
            })
        }
        if (pump.pumpDescription.isExtendedBolusCapable && iobCobCalculator.getExtendedBolus(dateUtil.now()) != null) {
            commandQueue.cancelExtended(object : Callback() {
                override fun run() {
                    if (!result.success) {
                        uiInteraction.runAlarm(result.comment, rh.gs(info.nightscout.core.ui.R.string.extendedbolusdeliveryerror), info.nightscout.core.ui.R.raw.boluserror)
                    }
                }
            })
        }
    }

    override fun suspendLoop(durationInMinutes: Int) {
        disposable += repository.runTransactionForResult(InsertAndCancelCurrentOfflineEventTransaction(dateUtil.now(), T.mins(durationInMinutes.toLong()).msecs(), OfflineEvent.Reason.SUSPEND))
            .subscribe({ result ->
                           result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                           result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $it") }
                       }, {
                           aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                       })
        commandQueue.cancelTempBasal(true, object : Callback() {
            override fun run() {
                if (!result.success) {
                    uiInteraction.runAlarm(result.comment, rh.gs(info.nightscout.core.ui.R.string.temp_basal_delivery_error), info.nightscout.core.ui.R.raw.boluserror)
                }
            }
        })
    }

    override fun buildDeviceStatus(
        dateUtil: DateUtil,
        loop: Loop,
        iobCobCalculatorPlugin: IobCobCalculator,
        profileFunction: ProfileFunction,
        pump: Pump,
        receiverStatusStore: ReceiverStatusStore,
        runningConfiguration: RunningConfiguration,
        version: String
    ): DeviceStatus? {
        val profile = profileFunction.getProfile() ?: return null
        val profileName = profileFunction.getProfileName()

        val lastRun = loop.lastRun
        var apsResult: JSONObject? = null
        var iob: JSONObject? = null
        var enacted: JSONObject? = null
        if (lastRun != null && lastRun.lastAPSRun > dateUtil.now() - 300 * 1000L) {
            // do not send if result is older than 1 min
            apsResult = lastRun.request?.json()?.also {
                it.put("timestamp", dateUtil.toISOString(lastRun.lastAPSRun))
            }
            iob = lastRun.request?.iob?.json(dateUtil)?.also {
                it.put("time", dateUtil.toISOString(lastRun.lastAPSRun))
            }
            val requested = JSONObject()
            if (lastRun.tbrSetByPump?.enacted == true) { // enacted
                enacted = lastRun.request?.json()?.also {
                    it.put("rate", lastRun.tbrSetByPump!!.json(profile.getBasal())["rate"])
                    it.put("duration", lastRun.tbrSetByPump!!.json(profile.getBasal())["duration"])
                    it.put("received", true)
                }
                requested.put("duration", lastRun.request?.duration)
                requested.put("rate", lastRun.request?.rate)
                requested.put("temp", "absolute")
                requested.put("smb", lastRun.request?.smb)
                enacted?.put("requested", requested)
                enacted?.put("smb", lastRun.tbrSetByPump?.bolusDelivered)
            }
        } else {
            val calcIob = iobCobCalculatorPlugin.calculateIobArrayInDia(profile)
            if (calcIob.isNotEmpty()) {
                iob = calcIob[0].json(dateUtil)
                iob.put("time", dateUtil.toISOString(dateUtil.now()))
            }
        }
        return DeviceStatus(
            timestamp = dateUtil.now(),
            suggested = apsResult?.toString(),
            iob = iob?.toString(),
            enacted = enacted?.toString(),
            device = "openaps://" + Build.MANUFACTURER + " " + Build.MODEL,
            pump = pump.getJSONStatus(profile, profileName, version).toString(),
            uploaderBattery = receiverStatusStore.batteryLevel,
            isCharging = receiverStatusStore.isCharging,
            configuration = runningConfiguration.configuration().toString()
        )
    }

    companion object {

        private const val CHANNEL_ID = "AAPS-OpenLoop"
    }
}
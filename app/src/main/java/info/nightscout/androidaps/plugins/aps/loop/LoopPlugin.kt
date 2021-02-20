package info.nightscout.androidaps.plugins.aps.loop

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.*
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.events.EventAcceptOpenLoopChange
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.events.EventTempTargetChange
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.interfaces.LoopInterface.LastRun
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopUpdateGui
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.general.wear.events.EventWearDoAction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
open class LoopPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger?,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBusWrapper,
    private val sp: SP,
    config: Config,
    private val constraintChecker: ConstraintChecker,
    resourceHelper: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val context: Context,
    private val commandQueue: CommandQueueProvider,
    private val activePlugin: ActivePluginProvider,
    private val treatmentsPlugin: TreatmentsPlugin,
    private val virtualPumpPlugin: VirtualPumpPlugin,
    private val iobCobCalculatorPlugin: IobCobCalculatorPlugin,
    private val receiverStatusStore: ReceiverStatusStore,
    private val fabricPrivacy: FabricPrivacy,
    private val nsUpload: NSUpload,
    private val hardLimits: HardLimits
) : PluginBase(PluginDescription()
    .mainType(PluginType.LOOP)
    .fragmentClass(LoopFragment::class.java.name)
    .pluginIcon(R.drawable.ic_loop_closed_white)
    .pluginName(R.string.loop)
    .shortName(R.string.loop_shortname)
    .preferencesId(R.xml.pref_loop)
    .enableByDefault(config.APS)
    .description(R.string.description_loop),
    aapsLogger!!, resourceHelper, injector
), LoopInterface {

    private val disposable = CompositeDisposable()
    private var lastBgTriggeredRun: Long = 0
    private var carbsSuggestionsSuspendedUntil: Long = 0
    private var prevCarbsreq = 0
    override var lastRun: LastRun? = null
    override fun onStart() {
        createNotificationChannel()
        super.onStart()
        disposable.add(rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ invoke("EventTempTargetChange", true) }, fabricPrivacy::logException)
        )
        /*
          This method is triggered once autosens calculation has completed, so the LoopPlugin
          has current data to work with. However, autosens calculation can be triggered by multiple
          sources and currently only a new BG should trigger a loop run. Hence we return early if
          the event causing the calculation is not EventNewBg.
          <p>
         */
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventAutosensCalculationFinished ->
                // Autosens calculation not triggered by a new BG
                if (event.cause !is EventNewBG) return@subscribe
                val glucoseValue = iobCobCalculatorPlugin.actualBg() ?: return@subscribe
                // BG outdated
                // already looped with that value
                if (glucoseValue.timestamp <= lastBgTriggeredRun) return@subscribe
                lastBgTriggeredRun = glucoseValue.timestamp
                invoke("AutosenseCalculation for $glucoseValue", true)
            }, fabricPrivacy::logException)
        )
    }

    private fun createNotificationChannel() {
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        @SuppressLint("WrongConstant") val channel = NotificationChannel(CHANNEL_ID,
            CHANNEL_ID,
            NotificationManager.IMPORTANCE_HIGH)
        mNotificationManager.createNotificationChannel(channel)
    }

    override fun onStop() {
        disposable.clear()
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

    fun suspendTo(endTime: Long) {
        sp.putLong("loopSuspendedTill", endTime)
        sp.putBoolean("isSuperBolus", false)
        sp.putBoolean("isDisconnected", false)
    }

    fun superBolusTo(endTime: Long) {
        sp.putLong("loopSuspendedTill", endTime)
        sp.putBoolean("isSuperBolus", true)
        sp.putBoolean("isDisconnected", false)
    }

    private fun disconnectTo(endTime: Long) {
        sp.putLong("loopSuspendedTill", endTime)
        sp.putBoolean("isSuperBolus", false)
        sp.putBoolean("isDisconnected", true)
    }

    fun minutesToEndOfSuspend(): Int {
        val loopSuspendedTill = sp.getLong("loopSuspendedTill", 0L)
        if (loopSuspendedTill == 0L) return 0
        val now = System.currentTimeMillis()
        val millisDiff = loopSuspendedTill - now
        if (loopSuspendedTill <= now) { // time exceeded
            suspendTo(0L)
            return 0
        }
        return (millisDiff / 60.0 / 1000.0).toInt()
    }

    // time exceeded
    val isSuspended: Boolean
        get() {
            val loopSuspendedTill = sp.getLong("loopSuspendedTill", 0L)
            if (loopSuspendedTill == 0L) return false
            val now = System.currentTimeMillis()
            if (loopSuspendedTill <= now) { // time exceeded
                suspendTo(0L)
                return false
            }
            return true
        }
    val isLGS: Boolean
        get() {
            val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()
            val maxIobAllowed = constraintChecker.getMaxIOBAllowed().value()
            val apsMode = sp.getString(R.string.key_aps_mode, "open")
            val pump = activePlugin.activePump
            var isLGS = false
            if (!isSuspended && !pump.isSuspended()) if (closedLoopEnabled.value()) if (maxIobAllowed == hardLimits.MAXIOB_LGS || apsMode == "lgs") isLGS = true
            return isLGS
        }

    // time exceeded
    val isSuperBolus: Boolean
        get() {
            val loopSuspendedTill = sp.getLong("loopSuspendedTill", 0L)
            if (loopSuspendedTill == 0L) return false
            val now = System.currentTimeMillis()
            if (loopSuspendedTill <= now) { // time exceeded
                suspendTo(0L)
                return false
            }
            return sp.getBoolean("isSuperBolus", false)
        }

    // time exceeded
    val isDisconnected: Boolean
        get() {
            val loopSuspendedTill = sp.getLong("loopSuspendedTill", 0L)
            if (loopSuspendedTill == 0L) return false
            val now = System.currentTimeMillis()
            if (loopSuspendedTill <= now) { // time exceeded
                suspendTo(0L)
                return false
            }
            return sp.getBoolean("isDisconnected", false)
        }

    @Suppress("SameParameterValue")
    private fun treatmentTimeThreshold(durationMinutes: Int): Boolean {
        val threshold = System.currentTimeMillis() + durationMinutes * 60 * 1000
        var bool = false
        if (treatmentsPlugin.lastBolusTime > threshold || treatmentsPlugin.lastCarbTime > threshold) bool = true
        return bool
    }

    @Synchronized operator fun invoke(initiator: String, allowNotification: Boolean) {
        invoke(initiator, allowNotification, false)
    }

    @Synchronized
    operator fun invoke(initiator: String, allowNotification: Boolean, tempBasalFallback: Boolean) {
        try {
            aapsLogger.debug(LTag.APS, "invoke from $initiator")
            val loopEnabled = constraintChecker.isLoopInvocationAllowed()
            if (!loopEnabled.value()) {
                val message = """
                    ${resourceHelper.gs(R.string.loopdisabled)}
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
                aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.noprofileselected))
                rxBus.send(EventLoopSetLastRunGui(resourceHelper.gs(R.string.noprofileselected)))
                return
            }

            // Check if pump info is loaded
            if (pump.baseBasalRate < 0.01) return
            val usedAPS = activePlugin.activeAPS
            if ((usedAPS as PluginBase).isEnabled(PluginType.APS)) {
                usedAPS.invoke(initiator, tempBasalFallback)
                apsResult = usedAPS.lastAPSResult
            }

            // Check if we have any result
            if (apsResult == null) {
                rxBus.send(EventLoopSetLastRunGui(resourceHelper.gs(R.string.noapsselected)))
                return
            }

            // Prepare for pumps using % basals
            if (pump.pumpDescription.tempBasalStyle == PumpDescription.PERCENT && allowPercentage()) {
                apsResult.usePercent = true
            }
            apsResult.percent = (apsResult.rate / profile.basal * 100).toInt()

            // check rate for constraints
            val resultAfterConstraints = apsResult.newAndClone(injector)
            resultAfterConstraints.rateConstraint = Constraint(resultAfterConstraints.rate)
            resultAfterConstraints.rate = constraintChecker.applyBasalConstraints(resultAfterConstraints.rateConstraint!!, profile).value()
            resultAfterConstraints.percentConstraint = Constraint(resultAfterConstraints.percent)
            resultAfterConstraints.percent = constraintChecker.applyBasalPercentConstraints(resultAfterConstraints.percentConstraint!!, profile).value()
            resultAfterConstraints.smbConstraint = Constraint(resultAfterConstraints.smb)
            resultAfterConstraints.smb = constraintChecker.applyBolusConstraints(resultAfterConstraints.smbConstraint!!).value()

            // safety check for multiple SMBs
            val lastBolusTime = treatmentsPlugin.lastBolusTime
            if (lastBolusTime != 0L && lastBolusTime + T.mins(3).msecs() > System.currentTimeMillis()) {
                aapsLogger.debug(LTag.APS, "SMB requested but still in 3 min interval")
                resultAfterConstraints.smb = 0.0
            }
            if (lastRun != null && lastRun!!.constraintsProcessed != null) {
                prevCarbsreq = lastRun!!.constraintsProcessed!!.carbsReq
            }
            if (lastRun == null) lastRun = LastRun()
            lastRun!!.request = apsResult
            lastRun!!.constraintsProcessed = resultAfterConstraints
            lastRun!!.lastAPSRun = DateUtil.now()
            lastRun!!.source = (usedAPS as PluginBase).name
            lastRun!!.tbrSetByPump = null
            lastRun!!.smbSetByPump = null
            lastRun!!.lastTBREnact = 0
            lastRun!!.lastTBRRequest = 0
            lastRun!!.lastSMBEnact = 0
            lastRun!!.lastSMBRequest = 0
            nsUpload.uploadDeviceStatus(this, iobCobCalculatorPlugin, profileFunction, activePlugin.activePump, receiverStatusStore, BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION)
            if (isSuspended) {
                aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.loopsuspended))
                rxBus.send(EventLoopSetLastRunGui(resourceHelper.gs(R.string.loopsuspended)))
                return
            }
            if (pump.isSuspended()) {
                aapsLogger.debug(LTag.APS, resourceHelper.gs(R.string.pumpsuspended))
                rxBus.send(EventLoopSetLastRunGui(resourceHelper.gs(R.string.pumpsuspended)))
                return
            }
            val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()
            if (closedLoopEnabled.value()) {
                if (allowNotification) {
                    if (resultAfterConstraints.isCarbsRequired
                        && resultAfterConstraints.carbsReq >= sp.getInt(R.string.key_smb_enable_carbs_suggestions_threshold, 0) && carbsSuggestionsSuspendedUntil < System.currentTimeMillis() && !treatmentTimeThreshold(-15)) {
                        if (sp.getBoolean(R.string.key_enable_carbs_required_alert_local, true) && !sp.getBoolean(R.string.key_raise_notifications_as_android_notifications, true)) {
                            val carbReqLocal = Notification(Notification.CARBS_REQUIRED, resultAfterConstraints.carbsRequiredText, Notification.NORMAL)
                            rxBus.send(EventNewNotification(carbReqLocal))
                        }
                        if (sp.getBoolean(R.string.key_ns_create_announcements_from_carbs_req, false)) {
                            nsUpload.uploadError(resultAfterConstraints.carbsRequiredText)
                        }
                        if (sp.getBoolean(R.string.key_enable_carbs_required_alert_local, true) && sp.getBoolean(R.string.key_raise_notifications_as_android_notifications, true)) {
                            val intentAction5m = Intent(context, CarbSuggestionReceiver::class.java)
                            intentAction5m.putExtra("ignoreDuration", 5)
                            val pendingIntent5m = PendingIntent.getBroadcast(context, 1, intentAction5m, PendingIntent.FLAG_UPDATE_CURRENT)
                            val actionIgnore5m = NotificationCompat.Action(R.drawable.ic_notif_aaps, resourceHelper.gs(R.string.ignore5m, "Ignore 5m"), pendingIntent5m)
                            val intentAction15m = Intent(context, CarbSuggestionReceiver::class.java)
                            intentAction15m.putExtra("ignoreDuration", 15)
                            val pendingIntent15m = PendingIntent.getBroadcast(context, 1, intentAction15m, PendingIntent.FLAG_UPDATE_CURRENT)
                            val actionIgnore15m = NotificationCompat.Action(R.drawable.ic_notif_aaps, resourceHelper.gs(R.string.ignore15m, "Ignore 15m"), pendingIntent15m)
                            val intentAction30m = Intent(context, CarbSuggestionReceiver::class.java)
                            intentAction30m.putExtra("ignoreDuration", 30)
                            val pendingIntent30m = PendingIntent.getBroadcast(context, 1, intentAction30m, PendingIntent.FLAG_UPDATE_CURRENT)
                            val actionIgnore30m = NotificationCompat.Action(R.drawable.ic_notif_aaps, resourceHelper.gs(R.string.ignore30m, "Ignore 30m"), pendingIntent30m)
                            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                            builder.setSmallIcon(R.drawable.notif_icon)
                                .setContentTitle(resourceHelper.gs(R.string.carbssuggestion))
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
                            rxBus.send(EventNewOpenLoopNotification())

                            //only send to wear if Native notifications are turned off
                            if (!sp.getBoolean(R.string.key_raise_notifications_as_android_notifications, true)) {
                                // Send to Wear
                                rxBus.send(EventWearDoAction("changeRequest"))
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
                    && !commandQueue.isRunning(Command.CommandType.BOLUS)) {
                    val waiting = PumpEnactResult(injector)
                    waiting.queued = true
                    if (resultAfterConstraints.tempBasalRequested) lastRun!!.tbrSetByPump = waiting
                    if (resultAfterConstraints.bolusRequested) lastRun!!.smbSetByPump = waiting
                    rxBus.send(EventLoopUpdateGui())
                    fabricPrivacy.logCustom("APSRequest")
                    applyTBRRequest(resultAfterConstraints, profile, object : Callback() {
                        override fun run() {
                            if (result.enacted || result.success) {
                                lastRun!!.tbrSetByPump = result
                                lastRun!!.lastTBRRequest = lastRun!!.lastAPSRun
                                lastRun!!.lastTBREnact = DateUtil.now()
                                rxBus.send(EventLoopUpdateGui())
                                applySMBRequest(resultAfterConstraints, object : Callback() {
                                    override fun run() {
                                        // Callback is only called if a bolus was actually requested
                                        if (result.enacted || result.success) {
                                            lastRun!!.smbSetByPump = result
                                            lastRun!!.lastSMBRequest = lastRun!!.lastAPSRun
                                            lastRun!!.lastSMBEnact = DateUtil.now()
                                        } else {
                                            Thread {
                                                SystemClock.sleep(1000)
                                                invoke("tempBasalFallback", allowNotification, true)
                                            }.start()
                                        }
                                        rxBus.send(EventLoopUpdateGui())
                                    }
                                })
                            } else {
                                lastRun!!.tbrSetByPump = result
                                lastRun!!.lastTBRRequest = lastRun!!.lastAPSRun
                            }
                            rxBus.send(EventLoopUpdateGui())
                        }
                    })
                } else {
                    lastRun!!.tbrSetByPump = null
                    lastRun!!.smbSetByPump = null
                }
            } else {
                if (resultAfterConstraints.isChangeRequested && allowNotification) {
                    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    builder.setSmallIcon(R.drawable.notif_icon)
                        .setContentTitle(resourceHelper.gs(R.string.openloop_newsuggestion))
                        .setContentText(resultAfterConstraints.toString())
                        .setAutoCancel(true)
                        .setPriority(Notification.IMPORTANCE_HIGH)
                        .setCategory(Notification.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    if (sp.getBoolean(R.string.key_wear_control, false)) {
                        builder.setLocalOnly(true)
                    }
                    presentSuggestion(builder)
                } else if (allowNotification) {
                    dismissSuggestion()
                }
            }
            rxBus.send(EventLoopUpdateGui())
        } finally {
            aapsLogger.debug(LTag.APS, "invoke end")
        }
    }

    fun disableCarbSuggestions(durationMinutes: Int) {
        carbsSuggestionsSuspendedUntil = System.currentTimeMillis() + durationMinutes * 60 * 1000
        dismissSuggestion()
    }

    private fun presentSuggestion(builder: NotificationCompat.Builder) {
        // Creates an explicit intent for an Activity in your app
        val resultIntent = Intent(context, MainActivity::class.java)

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(MainActivity::class.java)
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent)
        val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(resultPendingIntent)
        builder.setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // mId allows you to update the notification later on.
        mNotificationManager.notify(Constants.notificationID, builder.build())
        rxBus.send(EventNewOpenLoopNotification())

        // Send to Wear
        rxBus.send(EventWearDoAction("changeRequest"))
    }

    private fun dismissSuggestion() {
        // dismiss notifications
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(Constants.notificationID)
        rxBus.send(EventWearDoAction("cancelChangeRequest"))
    }

    fun acceptChangeRequest() {
        val profile = profileFunction.getProfile()
        val lp = this
        applyTBRRequest(lastRun!!.constraintsProcessed, profile, object : Callback() {
            override fun run() {
                if (result.enacted) {
                    lastRun!!.tbrSetByPump = result
                    lastRun!!.lastTBRRequest = lastRun!!.lastAPSRun
                    lastRun!!.lastTBREnact = DateUtil.now()
                    lastRun!!.lastOpenModeAccept = DateUtil.now()
                    nsUpload.uploadDeviceStatus(lp, iobCobCalculatorPlugin, profileFunction, activePlugin.activePump, receiverStatusStore, BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION)
                    sp.incInt(R.string.key_ObjectivesmanualEnacts)
                }
                rxBus.send(EventAcceptOpenLoopChange())
            }
        })
        fabricPrivacy.logCustom("AcceptTemp")
    }

    /**
     * expect absolute request and allow both absolute and percent response based on pump capabilities
     * TODO: update pump drivers to support APS request in %
     */
    private fun applyTBRRequest(request: APSResult?, profile: Profile?, callback: Callback?) {
        if (!request!!.tempBasalRequested) {
            callback?.result(PumpEnactResult(injector).enacted(false).success(true).comment(resourceHelper.gs(R.string.nochangerequested)))?.run()
            return
        }
        val pump = activePlugin.activePump
        if (!pump.isInitialized()) {
            aapsLogger.debug(LTag.APS, "applyAPSRequest: " + resourceHelper.gs(R.string.pumpNotInitialized))
            callback?.result(PumpEnactResult(injector).comment(resourceHelper.gs(R.string.pumpNotInitialized)).enacted(false).success(false))?.run()
            return
        }
        if (pump.isSuspended()) {
            aapsLogger.debug(LTag.APS, "applyAPSRequest: " + resourceHelper.gs(R.string.pumpsuspended))
            callback?.result(PumpEnactResult(injector).comment(resourceHelper.gs(R.string.pumpsuspended)).enacted(false).success(false))?.run()
            return
        }
        aapsLogger.debug(LTag.APS, "applyAPSRequest: $request")
        val now = System.currentTimeMillis()
        val activeTemp = treatmentsPlugin.getTempBasalFromHistory(now)
        if (request.usePercent && allowPercentage()) {
            if (request.percent == 100 && request.duration == 0) {
                if (activeTemp != null) {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: cancelTempBasal()")
                    commandQueue.cancelTempBasal(false, callback)
                } else {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: Basal set correctly")
                    callback?.result(PumpEnactResult(injector).percent(request.percent).duration(0)
                        .enacted(false).success(true).comment(resourceHelper.gs(R.string.basal_set_correctly)))?.run()
                }
            } else if (activeTemp != null && activeTemp.plannedRemainingMinutes > 5 && request.duration - activeTemp.plannedRemainingMinutes < 30 && request.percent == activeTemp.percentRate) {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: Temp basal set correctly")
                callback?.result(PumpEnactResult(injector).percent(request.percent)
                    .enacted(false).success(true).duration(activeTemp.plannedRemainingMinutes)
                    .comment(resourceHelper.gs(R.string.let_temp_basal_run)))?.run()
            } else {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: tempBasalPercent()")
                commandQueue.tempBasalPercent(request.percent, request.duration, false, profile!!, callback)
            }
        } else {
            if (request.rate == 0.0 && request.duration == 0 || abs(request.rate - pump.baseBasalRate) < pump.pumpDescription.basalStep) {
                if (activeTemp != null) {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: cancelTempBasal()")
                    commandQueue.cancelTempBasal(false, callback)
                } else {
                    aapsLogger.debug(LTag.APS, "applyAPSRequest: Basal set correctly")
                    callback?.result(PumpEnactResult(injector).absolute(request.rate).duration(0)
                        .enacted(false).success(true).comment(resourceHelper.gs(R.string.basal_set_correctly)))?.run()
                }
            } else if (activeTemp != null && activeTemp.plannedRemainingMinutes > 5 && request.duration - activeTemp.plannedRemainingMinutes < 30 && abs(request.rate - activeTemp.tempBasalConvertedToAbsolute(now, profile)) < pump.pumpDescription.basalStep) {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: Temp basal set correctly")
                callback?.result(PumpEnactResult(injector).absolute(activeTemp.tempBasalConvertedToAbsolute(now, profile))
                    .enacted(false).success(true).duration(activeTemp.plannedRemainingMinutes)
                    .comment(resourceHelper.gs(R.string.let_temp_basal_run)))?.run()
            } else {
                aapsLogger.debug(LTag.APS, "applyAPSRequest: setTempBasalAbsolute()")
                commandQueue.tempBasalAbsolute(request.rate, request.duration, false, profile!!, callback)
            }
        }
    }

    private fun applySMBRequest(request: APSResult, callback: Callback?) {
        if (!request.bolusRequested) {
            return
        }
        val pump = activePlugin.activePump
        val lastBolusTime = treatmentsPlugin.lastBolusTime
        if (lastBolusTime != 0L && lastBolusTime + 3 * 60 * 1000 > System.currentTimeMillis()) {
            aapsLogger.debug(LTag.APS, "SMB requested but still in 3 min interval")
            callback?.result(PumpEnactResult(injector)
                .comment(resourceHelper.gs(R.string.smb_frequency_exceeded))
                .enacted(false).success(false))?.run()
            return
        }
        if (!pump.isInitialized()) {
            aapsLogger.debug(LTag.APS, "applySMBRequest: " + resourceHelper.gs(R.string.pumpNotInitialized))
            callback?.result(PumpEnactResult(injector).comment(resourceHelper.gs(R.string.pumpNotInitialized)).enacted(false).success(false))?.run()
            return
        }
        if (pump.isSuspended()) {
            aapsLogger.debug(LTag.APS, "applySMBRequest: " + resourceHelper.gs(R.string.pumpsuspended))
            callback?.result(PumpEnactResult(injector).comment(resourceHelper.gs(R.string.pumpsuspended)).enacted(false).success(false))?.run()
            return
        }
        aapsLogger.debug(LTag.APS, "applySMBRequest: $request")

        // deliver SMB
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.lastKnownBolusTime = treatmentsPlugin.lastBolusTime
        detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS
        detailedBolusInfo.insulin = request.smb
        detailedBolusInfo.isSMB = true
        detailedBolusInfo.source = Source.USER
        detailedBolusInfo.deliverAt = request.deliverAt
        aapsLogger.debug(LTag.APS, "applyAPSRequest: bolus()")
        commandQueue.bolus(detailedBolusInfo, callback)
    }

    private fun allowPercentage(): Boolean {
        return virtualPumpPlugin.isEnabled(PluginType.PUMP)
    }

    fun disconnectPump(durationInMinutes: Int, profile: Profile?) {
        val pump = activePlugin.activePump
        disconnectTo(System.currentTimeMillis() + durationInMinutes * 60 * 1000L)
        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
            commandQueue.tempBasalAbsolute(0.0, durationInMinutes, true, profile!!, object : Callback() {
                override fun run() {
                    if (!result.success) {
                        ErrorHelperActivity.runAlarm(context, result.comment, resourceHelper.gs(R.string.tempbasaldeliveryerror), info.nightscout.androidaps.dana.R.raw.boluserror)
                    }
                }
            })
        } else {
            commandQueue.tempBasalPercent(0, durationInMinutes, true, profile!!, object : Callback() {
                override fun run() {
                    if (!result.success) {
                        ErrorHelperActivity.runAlarm(context, result.comment, resourceHelper.gs(R.string.tempbasaldeliveryerror), info.nightscout.androidaps.dana.R.raw.boluserror)
                    }
                }
            })
        }
        if (pump.pumpDescription.isExtendedBolusCapable && treatmentsPlugin.isInHistoryExtendedBoluslInProgress) {
            commandQueue.cancelExtended(object : Callback() {
                override fun run() {
                    if (!result.success) {
                        ErrorHelperActivity.runAlarm(context, result.comment, resourceHelper.gs(R.string.extendedbolusdeliveryerror), info.nightscout.androidaps.dana.R.raw.boluserror)
                    }
                }
            })
        }
        createOfflineEvent(durationInMinutes)
    }

    fun suspendLoop(durationInMinutes: Int) {
        suspendTo(System.currentTimeMillis() + durationInMinutes * 60 * 1000)
        commandQueue.cancelTempBasal(true, object : Callback() {
            override fun run() {
                if (!result.success) {
                    ErrorHelperActivity.runAlarm(context, result.comment, resourceHelper.gs(R.string.tempbasaldeliveryerror), info.nightscout.androidaps.dana.R.raw.boluserror)
                }
            }
        })
        createOfflineEvent(durationInMinutes)
    }

    fun createOfflineEvent(durationInMinutes: Int) {
        val data = JSONObject()
        try {
            data.put("eventType", CareportalEvent.OPENAPSOFFLINE)
            data.put("duration", durationInMinutes)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        val event = CareportalEvent(injector)
        event.date = DateUtil.now()
        event.source = Source.USER
        event.eventType = CareportalEvent.OPENAPSOFFLINE
        event.json = data.toString()
        MainApp.getDbHelper().createOrUpdate(event)
        nsUpload.uploadOpenAPSOffline(event)
    }

    companion object {

        private const val CHANNEL_ID = "AndroidAPS-OpenLoop"
    }
}
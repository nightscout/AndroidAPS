package app.aaps.implementation.queue

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.text.Spanned
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.annotations.OpenForTesting
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.EPS
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.Command.CommandType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissBolusProgressIfRunning
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventProfileSwitchChanged
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.getCustomizedName
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.HtmlHelper
import app.aaps.implementation.R
import app.aaps.implementation.queue.commands.CommandBolus
import app.aaps.implementation.queue.commands.CommandCancelExtendedBolus
import app.aaps.implementation.queue.commands.CommandCancelTempBasal
import app.aaps.implementation.queue.commands.CommandClearAlarms
import app.aaps.implementation.queue.commands.CommandCustomCommand
import app.aaps.implementation.queue.commands.CommandDeactivate
import app.aaps.implementation.queue.commands.CommandExtendedBolus
import app.aaps.implementation.queue.commands.CommandInsightSetTBROverNotification
import app.aaps.implementation.queue.commands.CommandLoadEvents
import app.aaps.implementation.queue.commands.CommandLoadHistory
import app.aaps.implementation.queue.commands.CommandLoadTDDs
import app.aaps.implementation.queue.commands.CommandReadStatus
import app.aaps.implementation.queue.commands.CommandSMBBolus
import app.aaps.implementation.queue.commands.CommandSetProfile
import app.aaps.implementation.queue.commands.CommandSetUserSettings
import app.aaps.implementation.queue.commands.CommandStartPump
import app.aaps.implementation.queue.commands.CommandStopPump
import app.aaps.implementation.queue.commands.CommandTempBasalAbsolute
import app.aaps.implementation.queue.commands.CommandTempBasalPercent
import app.aaps.implementation.queue.commands.CommandUpdateTime
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@OpenForTesting
@Singleton
class CommandQueueImplementation @Inject constructor(
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    aapsSchedulers: AapsSchedulers,
    private val rh: ResourceHelper,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val context: Context,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val fabricPrivacy: FabricPrivacy,
    private val uiInteraction: UiInteraction,
    private val persistenceLayer: PersistenceLayer,
    private val decimalFormatter: DecimalFormatter,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val jobName: CommandQueueName,
    private val workManager: WorkManager
) : CommandQueue {

    private val disposable = CompositeDisposable()
    internal var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private val queue = LinkedList<Command>()
    override var waitingForDisconnect = false

    @Volatile var performing: Command? = null

    init {
        disposable += rxBus
            .toObservable(EventProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .throttleLatest(3L, TimeUnit.SECONDS)
            .subscribe({
                           if (config.AAPSCLIENT) { // Effective profileswitch should be synced over NS, do not create EffectiveProfileSwitch here
                               return@subscribe
                           }
                           aapsLogger.debug(LTag.PROFILE, "onEventProfileSwitchChanged")
                           profileFunction.getRequestedProfile()?.let {
                               setProfile(ProfileSealed.PS(it, activePlugin), it.ids.nightscoutId != null, object : Callback() {
                                   override fun run() {
                                       if (!result.success) {
                                           uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile), app.aaps.core.ui.R.raw.boluserror)
                                       } else /* if (result.enacted || effective != null && effective.originalEnd < dateUtil.now() && effective.originalDuration != 0L) */ {
                                           // Pump may return enacted == false if basal profile is the same, but IC/ISF can be different
                                           val nonCustomized = ProfileSealed.PS(it, activePlugin).convertToNonCustomizedProfile(dateUtil)
                                           EPS(
                                               timestamp = dateUtil.now(),
                                               basalBlocks = nonCustomized.basalBlocks,
                                               isfBlocks = nonCustomized.isfBlocks,
                                               icBlocks = nonCustomized.icBlocks,
                                               targetBlocks = nonCustomized.targetBlocks,
                                               glucoseUnit = it.glucoseUnit,
                                               originalProfileName = it.profileName,
                                               originalCustomizedName = it.getCustomizedName(decimalFormatter),
                                               originalTimeshift = it.timeshift,
                                               originalPercentage = it.percentage,
                                               originalDuration = it.duration,
                                               originalEnd = it.end,
                                               iCfg = it.iCfg
                                           ).also { eps ->
                                               disposable += persistenceLayer.insertEffectiveProfileSwitch(eps).subscribe()
                                           }
                                       }
                                   }
                               })
                           }
                       }, fabricPrivacy::logException)
        /*
         * Clear old WorkManager jobs, because they survive restart
         */
        workManager.cancelUniqueWork(jobName.name)
    }

    private fun executingNowError(): PumpEnactResult =
        pumpEnactResultProvider.get().success(false).enacted(false).comment(R.string.executing_right_now)

    override fun isRunning(type: CommandType): Boolean = performing?.commandType == type

    @Synchronized
    private fun removeAll(type: CommandType) {
        synchronized(queue) {
            for (i in queue.indices.reversed()) {
                if (queue[i].commandType == type) {
                    queue.removeAt(i)
                }
            }
        }
    }

    /**
     * Watchdog. I observed issue where work stuck in RUNNING state but nothing actually happens
     * (last work completed successfully).
     * Cancel scheduled work in this case
     */
    private var readScheduledDetected: Long? = null

    @Synchronized
    fun isReadStatusScheduled(): Boolean {
        /*
         * Cancel all works if ReadStatus is scheduled for more that 15 min
         */
        readScheduledDetected?.let {
            if (dateUtil.isOlderThan(it, minutes = 15)) {
                workManager.cancelUniqueWork(jobName.name)
                fabricPrivacy.logCustom("QueueWorkerStuck")
                Thread.sleep(5000)
            }
        }

        synchronized(queue) {
            if (queue.isNotEmpty() && queue[queue.size - 1].commandType == CommandType.READSTATUS) {
                readScheduledDetected = dateUtil.now()
                return true
            }
        }
        readScheduledDetected = null
        return false
    }

    @Synchronized
    private fun add(command: Command) {
        aapsLogger.debug(LTag.PUMPQUEUE, "Adding: " + command.javaClass.simpleName + " - " + command.log())
        synchronized(queue) { queue.add(command) }
    }

    @Synchronized
    override fun pickup() {
        synchronized(queue) { performing = queue.poll() }
    }

    @Synchronized
    override fun clear() {
        performing = null
        synchronized(queue) {
            for (i in queue.indices) {
                queue[i].cancel()

            }
            queue.clear()
        }
    }

    override fun size(): Int = queue.size

    override fun performing(): Command? = performing

    override fun resetPerforming() {
        performing = null
    }

    private fun workIsRunning(): Boolean {
        for (workInfo in workManager.getWorkInfosForUniqueWork(jobName.name).get())
            if (workInfo.state == WorkInfo.State.BLOCKED || workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING)
                return true
        return false
    }

    // After new command added to the queue
    // start thread again if not already running
    @Synchronized fun notifyAboutNewCommand() = handler.post {
        waitForFinishedThread()
        if (!workIsRunning()) {
            workManager.enqueueUniqueWork(
                jobName.name, ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequest.Builder(QueueWorker::class.java)
                    .build()
            )
            aapsLogger.debug(LTag.PUMPQUEUE, "Starting new work")
        } else {
            aapsLogger.debug(LTag.PUMPQUEUE, "Work is already running")
        }
    }

    fun waitForFinishedThread() {
        while (workIsRunning() && waitingForDisconnect) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Waiting for previous work finish")
            SystemClock.sleep(500)
        }
    }

    @Synchronized
    override fun bolusInQueue(): Boolean {
        if (isRunning(CommandType.BOLUS)) return true
        if (isRunning(CommandType.SMB_BOLUS)) return true
        synchronized(queue) {
            for (i in queue.indices) {
                if (queue[i].commandType == CommandType.BOLUS) return true
                if (queue[i].commandType == CommandType.SMB_BOLUS) return true
            }
        }
        return false
    }

    // returns true if command is queued
    @Synchronized
    override fun bolus(detailedBolusInfo: DetailedBolusInfo, callback: Callback?): Boolean {
        // Check if pump store carbs
        // If not, it's not necessary add command to the queue and initiate connection
        // Assuming carbs in the future and carbs with duration are NOT stores anyway

        var carbsRunnable = Runnable { }
        val originalCarbs = detailedBolusInfo.carbs
        if ((detailedBolusInfo.carbs != 0.0) /*&&
            (!activePlugin.activePump.pumpDescription.storesCarbInfo ||
                detailedBolusInfo.carbsDuration != 0L ||
                (detailedBolusInfo.carbsTimestamp ?: detailedBolusInfo.timestamp) > dateUtil.now())*/
        ) {
            carbsRunnable = Runnable {
                aapsLogger.debug(LTag.PUMPQUEUE, "Going to store carbs")
                detailedBolusInfo.carbs = originalCarbs
                disposable += persistenceLayer.insertOrUpdateCarbs(
                    carbs = detailedBolusInfo.createCarbs(),
                    action = Action.CARBS,
                    source = Sources.Database
                ).subscribe(
                    { callback?.result(pumpEnactResultProvider.get().enacted(false).success(true))?.run() },
                    { callback?.result(pumpEnactResultProvider.get().enacted(false).success(false))?.run() }
                )
            }
            // Do not process carbs anymore
            detailedBolusInfo.carbs = 0.0
            // if no insulin just exit
            if (detailedBolusInfo.insulin == 0.0) {
                carbsRunnable.run() // store carbs
                return true
            }

        }
        val type = if (detailedBolusInfo.bolusType == BS.Type.SMB) CommandType.SMB_BOLUS else CommandType.BOLUS
        if (type == CommandType.SMB_BOLUS) {
            if (bolusInQueue()) {
                aapsLogger.debug(LTag.PUMPQUEUE, "Rejecting SMB since a bolus is queue/running")
                callback?.result(pumpEnactResultProvider.get().enacted(false).success(false))?.run()
                return false
            }
            val lastBolusTime = persistenceLayer.getNewestBolus()?.timestamp ?: 0L
            if (detailedBolusInfo.lastKnownBolusTime < lastBolusTime) {
                aapsLogger.debug(LTag.PUMPQUEUE, "Rejecting bolus, another bolus was issued since request time")
                callback?.result(pumpEnactResultProvider.get().enacted(false).success(false))?.run()
                return false
            }
            removeAll(CommandType.SMB_BOLUS)
        }
        if (isRunning(type)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished boluses
        removeAll(type)
        // apply constraints
        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger)).value()
        detailedBolusInfo.carbs =
            constraintChecker.applyCarbsConstraints(ConstraintObject(detailedBolusInfo.carbs.toInt(), aapsLogger)).value().toDouble()
        // add new command to queue
        BolusProgressData.set(detailedBolusInfo.insulin, isSMB = detailedBolusInfo.bolusType === BS.Type.SMB, id = detailedBolusInfo.id)
        if (detailedBolusInfo.bolusType == BS.Type.SMB) {
            add(CommandSMBBolus(injector, detailedBolusInfo, callback))
        } else {
            add(CommandBolus(injector, detailedBolusInfo, callback, type, carbsRunnable))
            if (type == CommandType.BOLUS) { // Bring up bolus progress dialog (start here, so the dialog is shown when the bolus is requested,
                // not when the Bolus command is starting. The command closes the dialog upon completion).
                showBolusProgressDialog(detailedBolusInfo)
                // Notify Wear about upcoming bolus
                rxBus.send(EventMobileToWear(EventData.BolusProgress(percent = 0, status = rh.gs(app.aaps.core.ui.R.string.goingtodeliver, detailedBolusInfo.insulin))))
            }
        }
        notifyAboutNewCommand()
        return true
    }

    override fun stopPump(callback: Callback?) {
        add(CommandStopPump(injector, callback))
        notifyAboutNewCommand()
    }

    override fun startPump(callback: Callback?) {
        add(CommandStartPump(injector, callback))
        notifyAboutNewCommand()
    }

    override fun setTBROverNotification(callback: Callback?, enable: Boolean) {
        add(CommandInsightSetTBROverNotification(injector, enable, callback))
        notifyAboutNewCommand()
    }

    @Synchronized
    override fun cancelAllBoluses(id: Long?) {
        if (!isRunning(CommandType.BOLUS)) {
            rxBus.send(EventDismissBolusProgressIfRunning(true, id))
        }
        removeAll(CommandType.BOLUS)
        removeAll(CommandType.SMB_BOLUS)
        Thread { activePlugin.activePump.stopBolusDelivering() }.start()
    }

    // returns true if command is queued
    override fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType, callback: Callback?): Boolean {
        if (!enforceNew && isRunning(CommandType.TEMPBASAL)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.TEMPBASAL)
        val rateAfterConstraints = constraintChecker.applyBasalConstraints(ConstraintObject(absoluteRate, aapsLogger), profile).value()
        // add new command to queue
        add(CommandTempBasalAbsolute(injector, rateAfterConstraints, durationInMinutes, enforceNew, profile, tbrType, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType, callback: Callback?): Boolean {
        if (!enforceNew && isRunning(CommandType.TEMPBASAL)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.TEMPBASAL)
        val percentAfterConstraints = constraintChecker.applyBasalPercentConstraints(ConstraintObject(percent, aapsLogger), profile).value()
        // add new command to queue
        add(CommandTempBasalPercent(injector, percentAfterConstraints, durationInMinutes, enforceNew, profile, tbrType, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun extendedBolus(insulin: Double, durationInMinutes: Int, callback: Callback?): Boolean {
        if (isRunning(CommandType.EXTENDEDBOLUS)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        val rateAfterConstraints = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(insulin, aapsLogger)).value()
        // remove all unfinished
        removeAll(CommandType.EXTENDEDBOLUS)
        // add new command to queue
        add(CommandExtendedBolus(injector, rateAfterConstraints, durationInMinutes, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun cancelTempBasal(enforceNew: Boolean, autoForced: Boolean, callback: Callback?): Boolean {
        if (!enforceNew && isRunning(CommandType.TEMPBASAL)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.TEMPBASAL)
        // add new command to queue
        add(CommandCancelTempBasal(injector, enforceNew, autoForced = autoForced, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun cancelExtended(callback: Callback?): Boolean {
        if (isRunning(CommandType.EXTENDEDBOLUS)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.EXTENDEDBOLUS)
        // add new command to queue
        add(CommandCancelExtendedBolus(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    fun setProfile(profile: ProfileSealed, hasNsId: Boolean, callback: Callback?): Boolean {
        if (isRunning(CommandType.BASAL_PROFILE)) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Command is already executed")
            callback?.result(pumpEnactResultProvider.get().success(true).enacted(false))?.run()
            return false
        }
        if (isThisProfileSet(profile) && persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now()) != null) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Correct profile already set")
            callback?.result(pumpEnactResultProvider.get().success(true).enacted(false))?.run()
            return false
        }
        // Compare with pump limits
        val basalValues = profile.getBasalValues()
        for (basalValue in basalValues) {
            if (basalValue.value < activePlugin.activePump.pumpDescription.basalMinimumRate) {
                val notification = Notification(Notification.BASAL_VALUE_BELOW_MINIMUM, rh.gs(R.string.basal_value_below_minimum), Notification.URGENT)
                rxBus.send(EventNewNotification(notification))
                callback?.result(pumpEnactResultProvider.get().success(false).enacted(false).comment(R.string.basal_value_below_minimum))?.run()
                return false
            }
        }
        rxBus.send(EventDismissNotification(Notification.BASAL_VALUE_BELOW_MINIMUM))
        // remove all unfinished
        removeAll(CommandType.BASAL_PROFILE)
        // add new command to queue
        add(CommandSetProfile(injector, profile, hasNsId, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun readStatus(reason: String, callback: Callback?): Boolean {
        if (isReadStatusScheduled()) {
            aapsLogger.debug(LTag.PUMPQUEUE, "READSTATUS $reason ignored as duplicated")
            callback?.result(executingNowError())?.run()
            return false
        }

        // add new command to queue
        add(CommandReadStatus(injector, reason, callback))
        notifyAboutNewCommand()
        return true
    }

    @Synchronized
    override fun statusInQueue(): Boolean {
        if (isRunning(CommandType.READSTATUS)) return true
        synchronized(queue) {
            for (i in queue.indices) {
                if (queue[i].commandType == CommandType.READSTATUS) {
                    return true
                }
            }
        }
        return false
    }

    // returns true if command is queued
    override fun loadHistory(type: Byte, callback: Callback?): Boolean {
        if (isRunning(CommandType.LOAD_HISTORY)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.LOAD_HISTORY)
        // add new command to queue
        add(CommandLoadHistory(injector, type, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun setUserOptions(callback: Callback?): Boolean {
        if (isRunning(CommandType.SET_USER_SETTINGS)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.SET_USER_SETTINGS)
        // add new command to queue
        add(CommandSetUserSettings(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun loadTDDs(callback: Callback?): Boolean {
        if (isRunning(CommandType.LOAD_TDD)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.LOAD_TDD)
        // add new command to queue
        add(CommandLoadTDDs(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun loadEvents(callback: Callback?): Boolean {
        if (isRunning(CommandType.LOAD_EVENTS)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.LOAD_EVENTS)
        // add new command to queue
        add(CommandLoadEvents(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun clearAlarms(callback: Callback?): Boolean {
        if (isRunning(CommandType.CLEAR_ALARMS)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.CLEAR_ALARMS)
        // add new command to queue
        add(CommandClearAlarms(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    override fun deactivate(callback: Callback?): Boolean {
        if (isRunning(CommandType.DEACTIVATE)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.DEACTIVATE)
        // add new command to queue
        add(CommandDeactivate(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    override fun updateTime(callback: Callback?): Boolean {
        if (isRunning(CommandType.UPDATE_TIME)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.UPDATE_TIME)
        // add new command to queue
        add(CommandUpdateTime(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    override fun customCommand(customCommand: CustomCommand, callback: Callback?): Boolean {
        if (isCustomCommandInQueue(customCommand.javaClass)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAllCustomCommands(customCommand.javaClass)
        // add new command to queue
        add(CommandCustomCommand(injector, customCommand, callback))
        notifyAboutNewCommand()
        return true
    }

    @Synchronized
    override fun isCustomCommandInQueue(customCommandType: Class<out CustomCommand>): Boolean {
        if (isCustomCommandRunning(customCommandType)) {
            return true
        }
        synchronized(queue) {
            for (i in queue.indices) {
                val command = queue[i]
                if (command is CommandCustomCommand && customCommandType.isInstance(command.customCommand)) {
                    return true
                }
            }
        }
        return false
    }

    override fun isCustomCommandRunning(customCommandType: Class<out CustomCommand>): Boolean {
        val performing = this.performing
        return performing is CommandCustomCommand && customCommandType.isInstance(performing.customCommand)
    }

    @Synchronized
    private fun removeAllCustomCommands(targetType: Class<out CustomCommand>) {
        synchronized(queue) {
            for (i in queue.indices.reversed()) {
                val command = queue[i]
                if (command is CustomCommand && targetType.isInstance(command.commandType)) {
                    queue.removeAt(i)
                }
            }
        }
    }

    override fun spannedStatus(): Spanned {
        var s = ""
        var line = 0
        val perf = performing
        if (perf != null) {
            s += "<b>" + perf.status() + "</b>"
            line++
        }
        synchronized(queue) {
            for (i in queue.indices) {
                if (line != 0) s += "<br>"
                s += queue[i].status()
                line++
            }
        }
        return HtmlHelper.fromHtml(s)
    }

    override fun isThisProfileSet(requestedProfile: Profile): Boolean {
        val runningProfile = profileFunction.getProfile() ?: return false
        val result = activePlugin.activePump.isThisProfileSet(requestedProfile) && requestedProfile.isEqual(runningProfile)
        if (!result) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Current profile: ${profileFunction.getProfile()}")
            aapsLogger.debug(LTag.PUMPQUEUE, "New profile: $requestedProfile")
        }
        return result
    }

    private fun showBolusProgressDialog(detailedBolusInfo: DetailedBolusInfo) {
        if (detailedBolusInfo.context != null) {
            uiInteraction.runBolusProgressDialog((detailedBolusInfo.context as AppCompatActivity).supportFragmentManager)
        } else {
            val i = Intent()
            i.setClass(context, uiInteraction.bolusProgressHelperActivity)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }
}
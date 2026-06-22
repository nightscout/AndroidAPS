package app.aaps.implementation.queue

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.text.Spanned
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.aaps.annotations.OpenForTesting
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.PS
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
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
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.getCustomizedName
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.objects.runningMode.PumpCommandGate
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@OpenForTesting
@Singleton
class CommandQueueImplementation @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val constraintChecker: ConstraintsChecker,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val fabricPrivacy: FabricPrivacy,
    private val uiInteraction: UiInteraction,
    private val notificationManager: NotificationManager,
    private val persistenceLayer: PersistenceLayer,
    private val decimalFormatter: DecimalFormatter,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val pumpSync: PumpSync,
    private val preferences: Preferences,
    private val localAlertUtils: Provider<LocalAlertUtils>,
    private val smsCommunicator: Provider<SmsCommunicator>,
    private val jobName: CommandQueueName,
    private val workManager: WorkManager,
    @ApplicationScope private val appScope: CoroutineScope,
    private val bolusProgressData: BolusProgressData
) : CommandQueue {

    internal var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private val queue = LinkedList<Command>()

    // Serializes every check-then-enqueue sequence (bolus, TBR, extended bolus, cancel*) and
    // cancelAllBoluses. The pre-suspend queue methods were @Synchronized; without this lock two
    // concurrent bolus() calls can both pass isRunning()/removeAll() and enqueue two boluses.
    // Critical sections must not contain suspension points.
    private val enqueueLock = Any()
    override var waitingForDisconnect = false

    @Volatile var performing: Command? = null

    // Upper bound for a single pump profile push. A normal connection failure resolves the command
    // (and the awaited deferred) on its own; this only guards the pathological lost-callback case so a
    // hung set can't block the sequential profile-change collector forever. Tune if pumps legitimately
    // need longer.
    private val PROFILE_SET_TIMEOUT_MS = 10 * 60 * 1000L

    init {
        // collectResilient guarantees a single failed onProfileChanged() can never permanently wedge
        // profile switching (an unguarded collector would be cancelled for the whole process lifetime,
        // after which every later ProfileSwitch is silently dropped: no pump push, no
        // EffectiveProfileSwitch, isProfileChangePending() stuck true). The hang vector is handled
        // separately by the withTimeoutOrNull() inside onProfileChanged().
        merge(
            rxBus.toFlow(EventProfileChangeRequested::class.java),
            persistenceLayer.observeChanges(PS::class.java)
        ).collectResilient(appScope, aapsLogger, LTag.PROFILE) { onProfileChanged() }
        /*
         * Clear old WorkManager jobs, because they survive restart
         */
        workManager.cancelUniqueWork(jobName.name)
    }

    private suspend fun onProfileChanged() {
        if (config.AAPSCLIENT) return // Effective profileswitch should be synced over NS, do not create EffectiveProfileSwitch here
        aapsLogger.debug(LTag.PROFILE, "onProfileChanged")
        // Exceptions are handled by collectResilient at the call site; here we only guard the hang vector.
        profileFunction.getRequestedProfile()?.let {
            // Skip if the active EPS was already triggered by this PS (e.g. NSClient updating PS with nsId
            // retriggers observeChanges(PS)). The previous onProfileChanged already pushed the profile to the pump.
            val active = persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now())
            if (active != null && active.originalPsId != null && active.originalPsId == it.id) {
                aapsLogger.debug(LTag.PROFILE, "Skipping onProfileChanged: active EPS id=${active.id} already represents PS id=${it.id}")
                return@let
            }
            // Bound the pump round-trip. setProfile() awaits a CommandSetProfile callback; if that
            // callback is ever lost the deferred never completes, and because the collector processes
            // emissions sequentially that single hang would block every future ProfileSwitch. On
            // timeout we treat it as a failed update so the collector stays alive.
            val result = withTimeoutOrNull(PROFILE_SET_TIMEOUT_MS) {
                setProfile(ProfileSealed.PS(it, activePlugin), it.ids.nightscoutId != null)
            }
            if (result == null) {
                aapsLogger.error(LTag.PROFILE, "setProfile timed out after $PROFILE_SET_TIMEOUT_MS ms for PS id=${it.id}")
                uiInteraction.runAlarm(
                    rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile),
                    rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile),
                    app.aaps.core.ui.R.raw.boluserror
                )
                return@let
            }
            if (!result.success) {
                uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile), app.aaps.core.ui.R.raw.boluserror)
            } else {
                // Pump may return enacted == false if basal profile is the same, but IC/ISF can be different
                val nonCustomized = ProfileSealed.PS(it, activePlugin).convertToNonCustomizedProfile(dateUtil)
                val eps = EPS(
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
                    originalPsId = it.id,
                    iCfg = it.iCfg
                )
                persistenceLayer.insertOrUpdateEffectiveProfileSwitch(eps)
            }
        }
    }

    private fun executingNowError(): PumpEnactResult =
        pumpEnactResultProvider.get().success(false).enacted(false).comment(R.string.executing_right_now)

    /**
     * Running-mode gate: reject commands that contradict the currently active running mode.
     * Returns true if the command was rejected (caller should return false); false if allowed.
     * Fails open on read errors — the gate is a belt, not the only line; upstream checks already
     * handle most suspended-mode paths in [LoopPlugin.invoke] and [applySMBRequest].
     */
    /** Returns the rejection result if the gate rejects, or null if the command is allowed. */
    private suspend fun rejectedByRunningModeGate(kind: PumpCommandGate.CommandKind): PumpEnactResult? {
        val mode = try {
            persistenceLayer.getRunningModeActiveAt(dateUtil.now()).mode
        } catch (e: Throwable) {
            aapsLogger.warn(LTag.PUMPQUEUE, "Running-mode gate: failed to read active mode, allowing command: ${e.message}")
            return null
        }
        val decision = PumpCommandGate.check(mode, kind)
        if (decision is PumpCommandGate.Decision.Reject) {
            val commentRes = when (decision.reason) {
                PumpCommandGate.Reason.PUMP_DISCONNECTED       -> app.aaps.core.ui.R.string.pump_disconnected
                PumpCommandGate.Reason.LOOP_SUSPENDED_DST,
                PumpCommandGate.Reason.SUPER_BOLUS_ACTIVE      -> app.aaps.core.ui.R.string.loopsuspended

                PumpCommandGate.Reason.PUMP_REPORTED_SUSPENDED -> app.aaps.core.ui.R.string.pumpsuspended
            }
            aapsLogger.debug(
                LTag.PUMPQUEUE,
                "Command rejected by running-mode gate: mode=$mode, kind=$kind, reason=${decision.reason}"
            )
            return pumpEnactResultProvider.get().success(false).enacted(false).comment(commentRes)
        }
        return null
    }

    override fun isRunning(type: CommandType): Boolean = performing?.commandType == type

    @Synchronized
    private fun removeAll(type: CommandType) {
        synchronized(queue) {
            for (i in queue.indices.reversed()) {
                if (queue[i].commandType == type) {
                    queue[i].cancel(app.aaps.core.ui.R.string.command_replaced)
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
         * Cancel all works if ReadStatus is scheduled for more than 15 min
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
                // Connection-timeout drop: the pump was never reached, so the command was not
                // executed. Report failure (success = false) so a waiting bolus caller is not told
                // a dose was delivered. (Supersession via removeAll keeps the default success = true.)
                queue[i].cancel(app.aaps.core.ui.R.string.connectiontimedout, success = false)
            }
            queue.clear()
        }
    }

    @Synchronized
    override fun completeAllAsNoOp(commentResId: Int) {
        performing = null
        synchronized(queue) {
            for (i in queue.indices) {
                queue[i].callback?.result(
                    pumpEnactResultProvider.get().success(true).enacted(false).comment(commentResId)
                )?.run()
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

    override suspend fun bolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        val originalCarbs = detailedBolusInfo.carbs
        // Carbs-only path: store and return without queuing a pump command.
        if (originalCarbs != 0.0 && detailedBolusInfo.insulin == 0.0) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Going to store carbs")
            return try {
                persistenceLayer.insertOrUpdateCarbs(
                    carbs = detailedBolusInfo.createCarbs(),
                    action = Action.CARBS,
                    source = Sources.Database
                )
                pumpEnactResultProvider.get().enacted(false).success(true)
            } catch (_: Exception) {
                pumpEnactResultProvider.get().enacted(false).success(false)
            }
        }
        // Bolus + carbs path: drop carbs from the queued command and persist them after the bolus succeeds.
        val hasCarbs = originalCarbs != 0.0
        if (hasCarbs) detailedBolusInfo.carbs = 0.0

        // Running-mode gate: reject bolus if current mode forbids new delivery.
        rejectedByRunningModeGate(PumpCommandGate.CommandKind.BOLUS)?.let { return it }

        val type = if (detailedBolusInfo.bolusType == BS.Type.SMB) CommandType.SMB_BOLUS else CommandType.BOLUS
        // Suspend DB read must stay outside enqueueLock; CommandSMBBolus.execute() re-checks freshness at execution time.
        val lastBolusTime = if (type == CommandType.SMB_BOLUS) persistenceLayer.getNewestBolus()?.timestamp ?: 0L else 0L
        val deferred = CompletableDeferred<PumpEnactResult>()
        val cb = object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }
        synchronized(enqueueLock) {
            if (type == CommandType.SMB_BOLUS) {
                if (bolusInQueue()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "Rejecting SMB since a bolus is queue/running")
                    return pumpEnactResultProvider.get().enacted(false).success(false)
                }
                if (detailedBolusInfo.lastKnownBolusTime < lastBolusTime) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "Rejecting bolus, another bolus was issued since request time")
                    return pumpEnactResultProvider.get().enacted(false).success(false)
                }
                removeAll(CommandType.SMB_BOLUS)
            }
            if (isRunning(type)) return executingNowError()
            removeAll(type)
            // apply constraints
            detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(ConstraintObject(detailedBolusInfo.insulin, aapsLogger)).value()
            val bolusGeneration = bolusProgressData.start(detailedBolusInfo.insulin, isSMB = detailedBolusInfo.bolusType === BS.Type.SMB, isPriming = detailedBolusInfo.bolusType == BS.Type.PRIMING)
            if (detailedBolusInfo.bolusType == BS.Type.SMB) {
                add(CommandSMBBolus(aapsLogger, rh, dateUtil, activePlugin, persistenceLayer, preferences, bolusProgressData, pumpEnactResultProvider, detailedBolusInfo, cb, bolusGeneration))
            } else {
                add(CommandBolus(aapsLogger, rh, activePlugin, pumpEnactResultProvider, bolusProgressData, detailedBolusInfo, cb, type, bolusGeneration))
                if (type == CommandType.BOLUS) { // Notify Wear about upcoming bolus
                    rxBus.send(EventMobileToWear(EventData.BolusProgress(percent = 0, status = rh.gs(app.aaps.core.ui.R.string.goingtodeliver, detailedBolusInfo.insulin))))
                }
            }
            notifyAboutNewCommand()
        }
        val result = deferred.await()
        // Persist carbs only when the bolus actually succeeded.
        if (hasCarbs && result.success) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Going to store carbs")
            detailedBolusInfo.carbs = originalCarbs
            try {
                persistenceLayer.insertOrUpdateCarbs(
                    carbs = detailedBolusInfo.createCarbs(),
                    action = Action.CARBS,
                    source = Sources.Database
                )
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPQUEUE, "Failed to store carbs after bolus", e)
            }
        }
        return result
    }

    override suspend fun stopPump(): PumpEnactResult {
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandStopPump(aapsLogger, rh, activePlugin, pumpEnactResultProvider, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override suspend fun startPump(): PumpEnactResult {
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandStartPump(aapsLogger, rh, activePlugin, pumpEnactResultProvider, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override suspend fun setTBROverNotification(enable: Boolean): PumpEnactResult {
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandInsightSetTBROverNotification(aapsLogger, rh, activePlugin, pumpEnactResultProvider, enable, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override fun cancelAllBoluses(id: Long?) {
        synchronized(enqueueLock) {
            if (isRunning(CommandType.BOLUS)) {
                bolusProgressData.stopPressed()
            } else {
                bolusProgressData.clear()
            }
            removeAll(CommandType.BOLUS)
            removeAll(CommandType.SMB_BOLUS)
        }
        Thread { activePlugin.activePump.stopBolusDelivering() }.start()
    }

    override suspend fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val gateKind = if (absoluteRate == 0.0) PumpCommandGate.CommandKind.TEMP_BASAL_ZERO else PumpCommandGate.CommandKind.TEMP_BASAL_NONZERO
        rejectedByRunningModeGate(gateKind)?.let { return it }
        val deferred = CompletableDeferred<PumpEnactResult>()
        synchronized(enqueueLock) {
            if (!enforceNew && isRunning(CommandType.TEMPBASAL)) return executingNowError()
            removeAll(CommandType.TEMPBASAL)
            val rateAfterConstraints = constraintChecker.applyBasalConstraints(ConstraintObject(absoluteRate, aapsLogger), profile).value()
            add(CommandTempBasalAbsolute(aapsLogger, rh, activePlugin, pumpEnactResultProvider, rateAfterConstraints, durationInMinutes, enforceNew, tbrType, object : Callback() {
                override fun run() {
                    deferred.complete(result)
                }
            }))
            notifyAboutNewCommand()
        }
        return deferred.await()
    }

    override suspend fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult {
        val gateKind = if (percent == 0) PumpCommandGate.CommandKind.TEMP_BASAL_ZERO else PumpCommandGate.CommandKind.TEMP_BASAL_NONZERO
        rejectedByRunningModeGate(gateKind)?.let { return it }
        val deferred = CompletableDeferred<PumpEnactResult>()
        synchronized(enqueueLock) {
            if (!enforceNew && isRunning(CommandType.TEMPBASAL)) return executingNowError()
            removeAll(CommandType.TEMPBASAL)
            val percentAfterConstraints = constraintChecker.applyBasalPercentConstraints(ConstraintObject(percent, aapsLogger), profile).value()
            add(CommandTempBasalPercent(aapsLogger, rh, activePlugin, pumpEnactResultProvider, percentAfterConstraints, durationInMinutes, enforceNew, tbrType, object : Callback() {
                override fun run() {
                    deferred.complete(result)
                }
            }))
            notifyAboutNewCommand()
        }
        return deferred.await()
    }

    override suspend fun extendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        rejectedByRunningModeGate(PumpCommandGate.CommandKind.EXTENDED_BOLUS)?.let { return it }
        val deferred = CompletableDeferred<PumpEnactResult>()
        synchronized(enqueueLock) {
            if (isRunning(CommandType.EXTENDEDBOLUS)) return executingNowError()
            val rateAfterConstraints = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(insulin, aapsLogger)).value()
            removeAll(CommandType.EXTENDEDBOLUS)
            add(CommandExtendedBolus(aapsLogger, rh, activePlugin, pumpEnactResultProvider, rateAfterConstraints, durationInMinutes, object : Callback() {
                override fun run() {
                    deferred.complete(result)
                }
            }))
            notifyAboutNewCommand()
        }
        return deferred.await()
    }

    override suspend fun cancelTempBasal(enforceNew: Boolean, autoForced: Boolean): PumpEnactResult {
        val deferred = CompletableDeferred<PumpEnactResult>()
        synchronized(enqueueLock) {
            if (!enforceNew && isRunning(CommandType.TEMPBASAL)) return executingNowError()
            removeAll(CommandType.TEMPBASAL)
            add(CommandCancelTempBasal(aapsLogger, rh, activePlugin, pumpSync, dateUtil, pumpEnactResultProvider, enforceNew, autoForced, object : Callback() {
                override fun run() {
                    deferred.complete(result)
                }
            }))
            notifyAboutNewCommand()
        }
        return deferred.await()
    }

    override suspend fun cancelExtended(): PumpEnactResult {
        val deferred = CompletableDeferred<PumpEnactResult>()
        synchronized(enqueueLock) {
            if (isRunning(CommandType.EXTENDEDBOLUS)) return executingNowError()
            removeAll(CommandType.EXTENDEDBOLUS)
            add(CommandCancelExtendedBolus(aapsLogger, rh, activePlugin, pumpEnactResultProvider, object : Callback() {
                override fun run() {
                    deferred.complete(result)
                }
            }))
            notifyAboutNewCommand()
        }
        return deferred.await()
    }

    suspend fun setProfile(profile: EffectiveProfile, hasNsId: Boolean): PumpEnactResult {
        if (isRunning(CommandType.BASAL_PROFILE)) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Command is already executed")
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }
        if (isThisProfileSet(profile) && persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now()) != null) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Correct profile already set")
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }
        // Compare with pump limits
        val basalValues = profile.getBasalValues()
        for (basalValue in basalValues) {
            if (basalValue.value < activePlugin.activePump.pumpDescription.basalMinimumRate) {
                notificationManager.post(NotificationId.BASAL_VALUE_BELOW_MINIMUM, R.string.basal_value_below_minimum)
                return pumpEnactResultProvider.get().success(false).enacted(false).comment(R.string.basal_value_below_minimum)
            }
        }
        notificationManager.dismiss(NotificationId.BASAL_VALUE_BELOW_MINIMUM)
        removeAll(CommandType.BASAL_PROFILE)
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(
            CommandSetProfile(
                aapsLogger, rh, smsCommunicator.get(), activePlugin, dateUtil, this, config, persistenceLayer, pumpEnactResultProvider,
                profile, hasNsId, object : Callback() {
                    override fun run() {
                        deferred.complete(result)
                    }
                }
            ))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override suspend fun readStatus(reason: String): PumpEnactResult {
        if (isReadStatusScheduled()) {
            aapsLogger.debug(LTag.PUMPQUEUE, "READSTATUS $reason ignored as duplicated")
            return executingNowError()
        }
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandReadStatus(aapsLogger, rh, activePlugin, localAlertUtils.get(), pumpEnactResultProvider, reason, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
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

    override suspend fun loadHistory(type: Byte): PumpEnactResult {
        if (isRunning(CommandType.LOAD_HISTORY)) return executingNowError()
        removeAll(CommandType.LOAD_HISTORY)
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandLoadHistory(aapsLogger, rh, activePlugin, pumpEnactResultProvider, type, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override suspend fun setUserOptions(): PumpEnactResult {
        if (isRunning(CommandType.SET_USER_SETTINGS)) return executingNowError()
        removeAll(CommandType.SET_USER_SETTINGS)
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandSetUserSettings(aapsLogger, rh, activePlugin, pumpEnactResultProvider, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override suspend fun loadTDDs(): PumpEnactResult {
        if (isRunning(CommandType.LOAD_TDD)) return executingNowError()
        removeAll(CommandType.LOAD_TDD)
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandLoadTDDs(aapsLogger, rh, activePlugin, pumpEnactResultProvider, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override suspend fun loadEvents(): PumpEnactResult {
        if (isRunning(CommandType.LOAD_EVENTS)) return executingNowError()
        removeAll(CommandType.LOAD_EVENTS)
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandLoadEvents(aapsLogger, rh, activePlugin, pumpEnactResultProvider, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override suspend fun clearAlarms(): PumpEnactResult {
        if (isRunning(CommandType.CLEAR_ALARMS)) return executingNowError()
        removeAll(CommandType.CLEAR_ALARMS)
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandClearAlarms(aapsLogger, rh, activePlugin, pumpEnactResultProvider, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override suspend fun deactivate(): PumpEnactResult {
        if (isRunning(CommandType.DEACTIVATE)) return executingNowError()
        removeAll(CommandType.DEACTIVATE)
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandDeactivate(aapsLogger, rh, activePlugin, pumpEnactResultProvider, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override suspend fun updateTime(): PumpEnactResult {
        if (isRunning(CommandType.UPDATE_TIME)) return executingNowError()
        removeAll(CommandType.UPDATE_TIME)
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandUpdateTime(aapsLogger, rh, activePlugin, pumpEnactResultProvider, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
    }

    override suspend fun customCommand(customCommand: CustomCommand): PumpEnactResult {
        if (isCustomCommandInQueue(customCommand.javaClass)) return executingNowError()
        removeAllCustomCommands(customCommand.javaClass)
        val deferred = CompletableDeferred<PumpEnactResult>()
        add(CommandCustomCommand(aapsLogger, activePlugin, pumpEnactResultProvider, customCommand, object : Callback() {
            override fun run() {
                deferred.complete(result)
            }
        }))
        notifyAboutNewCommand()
        return deferred.await()
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

    override suspend fun isThisProfileSet(requestedProfile: EffectiveProfile): Boolean {
        val runningProfile = profileFunction.getProfile() ?: return false
        val result = activePlugin.activePump.isThisProfileSet(requestedProfile) && requestedProfile.isEqual(runningProfile)
        if (!result) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Current profile: ${profileFunction.getProfile()}")
            aapsLogger.debug(LTag.PUMPQUEUE, "New profile: $requestedProfile")
        }
        return result
    }

}
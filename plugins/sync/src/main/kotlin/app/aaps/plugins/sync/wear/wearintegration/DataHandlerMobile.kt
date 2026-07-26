package app.aaps.plugins.sync.wear.wearintegration

import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.HR
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TDD
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.BatchExecutor
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.PumpStatusProvider
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.rx.events.EventWearUpdateGui
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.RunningModeList.AvailableRunningMode
import app.aaps.core.interfaces.rx.weardata.LoopStatusData
import app.aaps.core.interfaces.rx.weardata.OapsResultInfo
import app.aaps.core.interfaces.rx.weardata.TargetRange
import app.aaps.core.interfaces.rx.weardata.TempTargetInfo
import app.aaps.core.interfaces.scenes.SceneActions
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.interfaces.tempTargets.ttDurationMinutes
import app.aaps.core.interfaces.tempTargets.ttTargetMgdl
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.apsAdjustedTargetMgdl
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.generateCOBString
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.extensions.toStringShort
import app.aaps.core.objects.extensions.valueToUnits
import app.aaps.core.objects.runningMode.PumpCommandGate
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.core.ui.clientcontrol.failTextResId
import app.aaps.core.ui.compose.DarkGeneralColors
import app.aaps.core.ui.compose.LightGeneralColors
import app.aaps.plugins.sync.R
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.rx3.rxCompletable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

// Quiet-period that closes a Wear-event batch. Long enough to absorb a Data Layer reconnect-flush,
// short enough that live data stays effectively real-time.
private const val HEALTH_EVENT_QUIET_PERIOD_MS = 500L

@Singleton
class DataHandlerMobile @Inject constructor(
    private val aapsSchedulers: AapsSchedulers,
    private val context: Context,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val config: Config,
    private val iobCobCalculator: IobCobCalculator,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val loop: Loop,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val receiverStatusStore: ReceiverStatusStore,
    private val quickWizard: QuickWizard,
    private val trendCalculator: TrendCalculator,
    private val dateUtil: DateUtil,
    private val constraintChecker: ConstraintsChecker,
    private val activePlugin: ActivePlugin,
    private val insulin: Insulin,
    private val commandQueue: CommandQueue,
    private val fabricPrivacy: FabricPrivacy,
    private val uiInteraction: UiInteraction,
    private val persistenceLayer: PersistenceLayer,
    private val importExportPrefs: ImportExportPrefs,
    private val decimalFormatter: DecimalFormatter,
    private val pumpStatusProvider: PumpStatusProvider,
    private val ch: ConcentrationHelper,
    private val runningModeGuard: RunningModeGuard,
    private val wizardBolusExecutor: WizardBolusExecutor,
    // Role-transparent relays: on a MASTER they run locally (via wizardBolusExecutor); on a CLIENT they route the
    // action to the master over the signed round-trip (gated on masterReachable). EVERY wear therapy action goes
    // through these — bolus/eCarbs/TT/PS/RM via batchExecutor, quick-wizard/wizard via wizardExecutor. The local
    // wizardBolusExecutor stays on the wear path for Fill ONLY (no relay command for Fill).
    private val batchExecutor: BatchExecutor,
    private val wizardExecutor: WizardExecutor,
) {

    @Inject lateinit var automation: Automation
    @Inject lateinit var scenes: SceneAutomationApi
    @Inject lateinit var sceneActions: SceneActions
    private val disposable = CompositeDisposable()

    /**
     * Registers a serialized suspend [handler] for one [EventData] subtype arriving from Wear.
     *
     * One subscription per type preserves the prior concurrency model: same-type events are
     * serialized via concatMapCompletable, different types run on independent pipelines. Errors
     * are logged and swallowed so a single failing event can't tear the subscription down.
     */
    private inline fun <reified T : EventData> onEvent(crossinline handler: suspend (T) -> Unit) {
        disposable += rxBus
            .toObservable(T::class.java)
            .observeOn(aapsSchedulers.io)
            .concatMapCompletable { event ->
                rxCompletable {
                    aapsLogger.debug(LTag.WEAR, "${T::class.java.simpleName} received from ${event.sourceNodeId}")
                    handler(event)
                }
                    .doOnError(fabricPrivacy::logException)
                    .onErrorComplete()
            }
            .subscribe()
    }

    /**
     * Fire-immediately sibling of [onEvent] for non-suspend handlers (no concatMap serialization).
     * Emits the same uniform "<Type> received from <node>" debug line; extra diagnostics go through
     * [detail]. Errors are routed to [FabricPrivacy.logException], matching the prior subscriptions.
     */
    private inline fun <reified T : EventData> onEventSync(
        crossinline detail: (T) -> String = { "" },
        crossinline handler: (T) -> Unit
    ) {
        disposable += rxBus
            .toObservable(T::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.WEAR, "${T::class.java.simpleName} received from ${it.sourceNodeId}${detail(it)}")
                           handler(it)
                       }, fabricPrivacy::logException)
    }

    init {
        // From Wear
        onEventSync<EventData.ActionPong> { fabricPrivacy.logCustom("WearOS_${it.apiLevel}") }
        onEventSync<EventData.CancelBolus> {
            if (!config.appInitialized) return@onEventSync
            activePlugin.activePump.stopBolusDelivering()
        }
        onEvent<EventData.OpenLoopRequestConfirmed> {
            if (!config.appInitialized) return@onEvent
            loop.acceptChangeRequest()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Constants.notificationID)
        }
        onEvent<EventData.ActionResendData> { resendData(it.from) }
        onEvent<EventData.ActionPumpStatus> {
            sendToWear(
                EventData.ConfirmAction(
                    rh.gs(R.string.pump_status).uppercase(),
                    pumpStatusProvider.shortStatus(false),
                    returnCommand = null
                )
            )
        }
        onEvent<EventData.ActionLoopStatus> {
            sendToWear(
                EventData.ConfirmAction(
                    // title is the watch's curved header for the (read-only) lines screen.
                    rh.gs(R.string.loop_status), message = "",
                    lines = (targetsStatus() + loopStatus() + oAPSResultStatus()).map { l -> EventData.ConfirmActionLine(l.role.name, l.text) },
                    returnCommand = null
                )
            )
        }
        onEvent<EventData.ActionLoopStatusDetailed> {
            val statusData = buildLoopStatusData()
            sendToWear(
                EventData.LoopStatusResponse(
                    timeStamp = System.currentTimeMillis(),
                    data = statusData
                )
            )
        }
        onEvent<EventData.RunningModeRequest> { handleAvailableRunningModes() }
        onEvent<EventData.RunningModeSelected> { handleRunningModeSelected(it) }
        onEvent<EventData.RunningModeConfirmed> { handleRunningModeConfirmed(it) }
        onEvent<EventData.ActionTddStatus> { handleTddStatus() }
        onEvent<EventData.ActionProfileSwitchSendInitialData> { handleProfileSwitchSendInitialData() }
        onEvent<EventData.ActionProfileSwitchPreCheck> { handleProfileSwitchPreCheck(it) }
        onEvent<EventData.ActionProfileSwitchConfirmed> {
            // Commit the parked profile switch through the role-transparent relay (MASTER → local applyProfileSwitch;
            // CLIENT → signed BolusCommit so the MASTER applies it, not the follower locally).
            contacting() // CLIENT: show the spinner during the commit round-trip too (no-op on master).
            onCommitResult(batchExecutor.commit(it.bolusId, Sources.Wear, rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch)))
        }
        onEvent<EventData.ActionTempTargetPreCheck> { handleTempTargetPreCheck(it) }
        onEvent<EventData.ActionTempTargetConfirmed> {
            // Commit the parked TT through the relay (MASTER → local applyTempTarget set/cancel; CLIENT → master applies).
            contacting() // CLIENT: show the spinner during the commit round-trip too (no-op on master).
            onCommitResult(batchExecutor.commit(it.bolusId, Sources.Wear, rh.gs(app.aaps.core.ui.R.string.temporary_target)))
        }
        onEvent<EventData.ActionBolusPreCheck> { handleBolusPreCheck(it) }
        onEvent<EventData.ActionBolusConfirmed> {
            // Commit the parked dose by id through the role-transparent relay (MASTER → local deliver; CLIENT →
            // signed BolusCommit). Consume-once = no double bolus; a failure surfaces to the watch.
            contacting() // CLIENT: show the spinner during the commit round-trip too (no-op on master).
            // markAsUsed is done by the MASTER inside the executor's confirm() (a fixed QuickWizard batch carries its
            // quickWizardGuid) — this device must NOT write the synced QuickWizard pref itself (on a client that pushes
            // it back over the round-trip → "Update settings … Another action is already in progress"). On a delivered
            // bolus refresh the tile's lastUsed: immediate on a master; on a client it reflects after the master's mark
            // syncs back via the cold-doc.
            onCommitResult(batchExecutor.commit(it.bolusId, Sources.Wear, rh.gs(app.aaps.core.ui.R.string.overview_treatment_label))) {
                sendQuickWizardListToWear()
            }
        }
        onEvent<EventData.ActionECarbsPreCheck> { handleECarbsPreCheck(it) }
        onEvent<EventData.ActionECarbsConfirmed> {
            // Commit the parked eCarbs through the relay (MASTER → local deliverECarbs; CLIENT → master records them).
            contacting() // CLIENT: show the spinner during the commit round-trip too (no-op on master).
            onCommitResult(batchExecutor.commit(it.bolusId, Sources.Wear, rh.gs(app.aaps.core.ui.R.string.overview_treatment_label)))
        }
        onEvent<EventData.ActionFillPresetPreCheck> { handleFillPresetPreCheck(it) }
        onEvent<EventData.ActionFillPreCheck> { handleFillPreCheck(it) }
        onEvent<EventData.ActionFillConfirmed> {
            if (!config.appInitialized) return@onEvent
            // Defense-in-depth: Fill is off-relay and delivered locally only — a client must never reach here.
            if (rejectIfAapsClient()) return@onEvent
            if (constraintChecker.applyBolusConstraints(ConstraintObject(it.insulin, aapsLogger)).value() - it.insulin != 0.0) {
                rxBus.send(EventShowSnackbar("aborting: previously applied constraint changed", EventShowSnackbar.Type.Warning))
                sendError("aborting: previously applied constraint changed")
            } else
                wizardBolusExecutor.deliverFillBolus(it.insulin, null, Sources.Wear, ::sendError)
        }
        onEvent<EventData.ActionQuickWizardPreCheck> { handleQuickWizardPreCheck(it) }
        onEvent<EventData.ActionWizardPreCheck> { handleWizardPreCheck(it) }
        onEvent<EventData.ActionWizardConfirmed> {
            // Commit the parked wizard/quick-wizard dose by id through the role-transparent relay (MASTER → local
            // deliver; CLIENT → signed BolusCommit; wear has no advisor fork → asAdvisor=false). Refresh the watch's
            // quick-wizard list (lastUsed) only when something was actually delivered; a failure surfaces to the watch.
            // NOTE: `it.timeStamp` is NOT a timestamp here — the legacy field name carries the master-assigned
            // consume-once bolusId of the parked prepare. Do not rename the field (wire-compat with older watches).
            contacting() // CLIENT: show the spinner during the commit round-trip too (no-op on master).
            onCommitResult(wizardExecutor.commit(it.timeStamp, asAdvisor = false, Sources.Wear, rh.gs(app.aaps.core.ui.R.string.boluswizard), correctionU = it.correctionU)) {
                sendQuickWizardListToWear()
            }
        }
        onEvent<EventData.ActionUserActionPreCheck> {
            if (!config.appInitialized) return@onEvent
            handleUserActionPreCheck(it)
        }
        onEvent<EventData.ActionUserActionConfirmed> {
            if (!config.appInitialized) return@onEvent
            handleUserActionConfirmed(it)
        }
        onEvent<EventData.ActionScenePreCheck> {
            if (!config.appInitialized) return@onEvent
            handleScenePreCheck(it)
        }
        onEvent<EventData.ActionSceneConfirmed> {
            if (!config.appInitialized) return@onEvent
            handleSceneConfirmed(it)
        }
        onEvent<EventData.ActionSceneStop> {
            if (!config.appInitialized) return@onEvent
            scenes.stopActiveScene()
        }
        onEvent<EventData.ActionSceneStopPreCheck> {
            if (!config.appInitialized) return@onEvent
            handleSceneStopPreCheck()
        }
        onEvent<EventData.ActionSceneStopConfirmed> {
            if (!config.appInitialized) return@onEvent
            onCommitResult(sceneActions.stop(triggerChain = false))
        }
        onEventSync<EventData.SnoozeAlert> { uiInteraction.stopAlarm("Muted from wear") }
        onEventSync<EventData.WearException> { fabricPrivacy.logWearException(it) }
        // Coalesce Wear reconnect-flush bursts (Data Layer replays queued events back-to-back).
        // publish/debounce keeps the timer idle when no events arrive, unlike fixed-window buffer().
        disposable += rxBus
            .toObservable(EventData.ActionHeartRate::class.java)
            .publish { shared -> shared.buffer(shared.debounce(HEALTH_EVENT_QUIET_PERIOD_MS, TimeUnit.MILLISECONDS, aapsSchedulers.io)) }
            .observeOn(aapsSchedulers.io)
            .concatMapCompletable {
                rxCompletable { handleHeartRateBatch(it) }
                    .doOnError(fabricPrivacy::logException)
                    .onErrorComplete()
            }
            .subscribe()
        disposable += rxBus
            .toObservable(EventData.ActionStepsRate::class.java)
            .publish { shared -> shared.buffer(shared.debounce(HEALTH_EVENT_QUIET_PERIOD_MS, TimeUnit.MILLISECONDS, aapsSchedulers.io)) }
            .observeOn(aapsSchedulers.io)
            .concatMapCompletable {
                rxCompletable { handleStepsCountBatch(it) }
                    .doOnError(fabricPrivacy::logException)
                    .onErrorComplete()
            }
            .subscribe()
        onEventSync<EventData.ActionGetCustomWatchface>(detail = { " watchface=${it.customWatchface}" }) { handleGetCustomWatchface(it) }
    }

    private fun maxOfNullable(vararg values: Long?): Long? {
        return values.filterNotNull().maxOrNull()
    }

    private suspend fun buildLoopStatusData(): LoopStatusData {
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        val profile = profileFunction.getProfile()
        val usedAPS = activePlugin.activeAPS

        // Get data based on app type
        val (lastRunTimestamp, lastEnactTimestamp, apsResult) = if (config.APS) {
            // AAPS - use local loop data
            val lastRun = loop.lastRun

            // For enacted timestamp, use the LATEST of TBR or SMB
            val lastTbrEnact = lastRun?.lastTBREnact?.takeIf { it != 0L }
            val lastSmbEnact = lastRun?.lastSMBEnact?.takeIf { it != 0L }
            val lastEnact = maxOfNullable(lastTbrEnact, lastSmbEnact)

            Triple(
                lastRun?.lastAPSRun,
                lastEnact,
                lastRun?.constraintsProcessed
            )
        } else {
            // AAPSClient - use data from NS/device status
            val apsData = processedDeviceStatusData.openAPSData

            // Use clockEnacted only if it's within 30s of clockSuggested or newer
            val timeWindowMs = 30_000L
            val apsDataLastEnact = if (apsData.clockEnacted >= apsData.clockSuggested - timeWindowMs) {
                apsData.clockEnacted
            } else {
                null
            }
            Triple(
                apsData.clockSuggested,
                apsDataLastEnact,
                processedDeviceStatusData.getAPSResult()
            )
        }

        // Map loop mode
        val loopMode = when (loop.runningMode()) {
            RM.Mode.CLOSED_LOOP       -> LoopStatusData.LoopMode.CLOSED
            RM.Mode.OPEN_LOOP         -> LoopStatusData.LoopMode.OPEN
            RM.Mode.CLOSED_LOOP_LGS   -> LoopStatusData.LoopMode.LGS
            RM.Mode.DISABLED_LOOP     -> LoopStatusData.LoopMode.DISABLED
            RM.Mode.SUSPENDED_BY_USER -> LoopStatusData.LoopMode.SUSPENDED
            RM.Mode.SUSPENDED_BY_PUMP -> LoopStatusData.LoopMode.PUMP_SUSPENDED
            RM.Mode.SUSPENDED_BY_DST  -> LoopStatusData.LoopMode.DST_SUSPENDED
            RM.Mode.DISCONNECTED_PUMP -> LoopStatusData.LoopMode.DISCONNECTED
            RM.Mode.SUPER_BOLUS       -> LoopStatusData.LoopMode.SUPERBOLUS
            else                      -> LoopStatusData.LoopMode.UNKNOWN
        }

        // Build temp target info
        val tempTargetInfo = tempTarget?.let {
            val units = if (profileUtil.units == GlucoseUnit.MGDL) "mg/dL" else "mmol/L"
            val targetString = profileUtil.toTargetRangeString(
                it.lowTarget,
                it.highTarget,
                GlucoseUnit.MGDL
            )
            val durationMin = ((it.end - dateUtil.now()) / 60000).toInt()

            TempTargetInfo(
                targetDisplay = targetString,
                endTime = it.end,
                durationMinutes = durationMin,
                units = units
            )
        }

        // Build autosens-adjusted target (only when no TT active)
        val autosensTarget = if (tempTarget == null && profile != null) {
            val adjustedTarget = profile.apsAdjustedTargetMgdl(loop, config, processedDeviceStatusData)
            if (adjustedTarget != null) profileUtil.fromMgdlToStringWithUnits(adjustedTarget)
            else null
        } else null

        // Build default range
        val defaultRange = if (profile != null) {
            val units = if (profileUtil.units == GlucoseUnit.MGDL) "mg/dL" else "mmol/L"
            TargetRange(
                lowDisplay = profileUtil.fromMgdlToStringInUnits(profile.getTargetLowMgdl()),
                highDisplay = profileUtil.fromMgdlToStringInUnits(profile.getTargetHighMgdl()),
                targetDisplay = profileUtil.fromMgdlToStringInUnits(profile.getTargetMgdl()),
                units = units
            )
        } else {
            TargetRange("--", "--", "--", "")
        }

        // Build OAPS result info
        val oapsResultInfo = apsResult?.let { result ->
            val constrainedRate = result.rate
            val constrainedDuration = result.duration

            // Check if this is "let temp basal run" scenario
            // AAPS: rate=0.0 and duration=-1
            // AAPSClient: rate=-1.0 and duration=-1
            val isLetTempRun = if (config.APS) {
                constrainedRate == 0.0 && constrainedDuration == -1
            } else {
                constrainedRate == -1.0 && constrainedDuration == -1
            }

            // Determine what to display
            val (displayRate, displayDuration, displayPercent) = if (isLetTempRun) {
                // Get currently running temp basal from database
                val currentTbr = persistenceLayer.getTemporaryBasalActiveAt(dateUtil.now())

                if (currentTbr != null) {
                    // Calculate absolute rate
                    val rate = if (currentTbr.isAbsolute) {
                        currentTbr.rate
                    } else if (profile != null) {
                        // Percent-based TBR - convert to absolute
                        profile.getBasal(dateUtil.now()) * currentTbr.rate / 100.0
                    } else {
                        currentTbr.rate
                    }

                    // Calculate remaining duration
                    val remainingMin = ((currentTbr.end - dateUtil.now()) / 60000).toInt()

                    val percentValue = if (ch.fromPump(activePlugin.activePump.baseBasalRate) > 0) {
                        ((rate / ch.fromPump(activePlugin.activePump.baseBasalRate)) * 100).toInt()
                    } else 0

                    aapsLogger.debug(LTag.WEAR, "Let temp run - rate: $rate U/h ($percentValue%), remaining: $remainingMin min")

                    Triple(rate, remainingMin, percentValue)
                } else {
                    aapsLogger.debug(LTag.WEAR, "Let temp run requested but no active TBR found")
                    Triple(null, null, null)
                }
            } else {
                // Normal case - show the new requested values
                val percentValue = if (result.usePercent) {
                    result.percent
                } else if (ch.fromPump(activePlugin.activePump.baseBasalRate) > 0) {
                    ((constrainedRate / ch.fromPump(activePlugin.activePump.baseBasalRate)) * 100).toInt()
                } else null

                // For AAPSClient, use current TBR rate if available, otherwise use constrained rate
                val finalRate = if (!config.APS) {
                    val currentTbr = persistenceLayer.getTemporaryBasalActiveAt(dateUtil.now())
                    currentTbr?.rate ?: constrainedRate
                } else {
                    constrainedRate
                }

                Triple(finalRate, constrainedDuration, percentValue)
            }

            OapsResultInfo(
                changeRequested = result.isChangeRequested() && !isLetTempRun,
                isLetTempRun = isLetTempRun,
                rate = displayRate,
                ratePercent = displayPercent,
                duration = displayDuration,
                reason = result.reason,
                smbAmount = result.smb
            )
        }

        return LoopStatusData(
            timestamp = System.currentTimeMillis(),
            loopMode = loopMode,
            apsName = if (loop.runningMode().isLoopRunning())
                (usedAPS as? PluginBase)?.name else null,
            lastRun = lastRunTimestamp,
            lastEnact = lastEnactTimestamp,
            tempTarget = tempTargetInfo,
            autosensTarget = autosensTarget,
            defaultRange = defaultRange,
            oapsResult = oapsResultInfo
        )
    }

    private suspend fun handleTddStatus() {
        val activePump = activePlugin.activePump
        val dummies: MutableList<TDD> = LinkedList()
        val historyList = getTDDList(dummies)
        if (isOldData(historyList)) {
            val busy = activePump.isBusy()
            val message = rh.gs(app.aaps.core.ui.R.string.tdd_old_data) + ", " +
                if (busy) rh.gs(app.aaps.core.ui.R.string.pump_busy) else rh.gs(R.string.pump_fetching_data)
            sendToWear(EventData.ConfirmAction(rh.gs(app.aaps.core.ui.R.string.tdd_short), message, returnCommand = null))
            if (!busy) {
                commandQueue.loadTDDs()
                val dummies1: MutableList<TDD> = LinkedList()
                val historyList1 = getTDDList(dummies1)
                val reloadMessage =
                    if (isOldData(historyList1))
                        rh.gs(R.string.pump_old_data) + "\n" + generateTDDMessage(historyList1, dummies1)
                    else
                        generateTDDMessage(historyList1, dummies1)
                sendToWear(EventData.ConfirmAction(rh.gs(app.aaps.core.ui.R.string.tdd_short), reloadMessage, returnCommand = null))
            }
        } else {
            sendToWear(EventData.ConfirmAction(rh.gs(app.aaps.core.ui.R.string.tdd_short), generateTDDMessage(historyList, dummies), returnCommand = null))
        }
    }

    private fun rejectIfAapsClient(): Boolean {
        if (config.AAPSCLIENT) {
            sendError(rh.gs(R.string.wear_remote_insulin_not_allowed_in_client))
            return true
        }
        return false
    }

    // internal + suspend so DataHandlerMobileWearBolusTest can drive it; routes through the shared recompute path.
    internal suspend fun handleWizardPreCheck(command: EventData.ActionWizardPreCheck) {
        // Role-transparent recompute: MASTER recomputes + caps + parks + authors lines locally; CLIENT relays a
        // WizardPrepare(inputs) to the master (gated on masterReachable). The watch renders the master's lines in
        // AcceptActivity (no bespoke breakdown — same as the phone confirm). The recompute trusts inputs.bg, so the
        // caller supplies the current BG (the master's own on a master; the follower's NS-synced BG on a client, as
        // the manual-wizard dialog does) and null-checks it; profile/pump/COB/constraints/zero-net live in prepareWizard.
        val bgReading = iobCobCalculator.ads.actualBg() ?: run {
            sendError(rh.gs(app.aaps.core.ui.R.string.wizard_no_actual_bg))
            return
        }
        val inputs = WizardBolusExecutor.WizardInputs(
            bg = bgReading.valueToUnits(profileFunction.getUnits()),
            carbs = command.carbs,
            percentage = command.percentage,
            directCorrection = 0.0,
            carbTime = 0,
            useBg = preferences.get(BooleanKey.WearWizardBg),
            useCob = preferences.get(BooleanKey.WearWizardCob),
            useIob = preferences.get(BooleanKey.WearWizardIob),
            useTt = preferences.get(BooleanKey.WearWizardTt),
            useTrend = preferences.get(BooleanKey.WearWizardTrend),
            alarm = false,
            notes = "",
            source = Sources.Wear
        )
        contacting()
        shipPrepared(
            wizardExecutor.prepare(WizardExecutor.WizardSource.Manual(inputs), rh.gs(app.aaps.core.ui.R.string.boluswizard)),
            rh.gs(app.aaps.core.ui.R.string.boluswizard)
        ) { EventData.ActionWizardConfirmed(it) }
    }

    private suspend fun handleUserActionPreCheck(command: EventData.ActionUserActionPreCheck) {
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        if (loop.runningMode().isLoopRunning() && pump.isInitialized() && profile != null) {
            // Stable UUID lookup. findEventById finds any event regardless of userAction flag;
            // we re-check isEnabled/canRun below as the gating policy.
            automation.findEventById(command.id)?.takeIf { it.userAction }?.let { event ->
                if (event.isEnabled && event.canRun()) {
                    sendToWear(
                        EventData.ConfirmAction(
                            rh.gs(app.aaps.core.ui.R.string.confirm).uppercase(), command.title,
                            returnCommand = EventData.ActionUserActionConfirmed(command.id, command.title)
                        )
                    )
                } else {
                    sendError(rh.gs(R.string.user_action_not_available, command.title))
                }
            } ?: apply {
                sendError(rh.gs(R.string.user_action_not_available, command.title))
            }
        } else {
            sendError(rh.gs(app.aaps.core.ui.R.string.wizard_pump_not_available))
        }
    }

    internal suspend fun handleUserActionConfirmed(command: EventData.ActionUserActionConfirmed) {
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        if (loop.runningMode().isLoopRunning() && pump.isInitialized() && profile != null) {
            // Symmetric with handleUserActionPreCheck: surface sendError on every failure path so a
            // race between PreCheck and Confirmed (event deleted, userAction flag cleared, canRun
            // flipped) doesn't silently drop the user's tap — the watch UI would otherwise dismiss
            // the dialog as if the action ran.
            val event = automation.findEventById(command.id)?.takeIf { it.userAction }
            val ok = event != null && event.isEnabled && event.canRun() &&
                // canRun() is suspend and can yield; re-verify the same event instance is still in
                // the cache afterward so a concurrent autoRemove / user delete / NS-synced removal
                // during the suspension doesn't let us run an orphan that no longer exists in the
                // plugin's list.
                automation.findEventById(command.id) === event
            if (ok) {
                automation.processEvent(event)
            } else {
                sendError(rh.gs(R.string.user_action_not_available, command.title))
            }
        } else {
            sendError(rh.gs(app.aaps.core.ui.R.string.wizard_pump_not_available))
        }
    }

    private suspend fun handleScenePreCheck(command: EventData.ActionScenePreCheck) {
        val label = rh.gs(app.aaps.core.ui.R.string.scenes)
        contacting()
        shipPrepared(sceneActions.prepareStart(command.id), label) { bolusId ->
            EventData.ActionSceneConfirmed(command.id, command.title, bolusId)
        }
    }

    private suspend fun handleSceneConfirmed(command: EventData.ActionSceneConfirmed) {
        if (command.bolusId != null) {
            onCommitResult(sceneActions.commitStart(command.bolusId!!))
        } else {
            // Fallback for watch builds that pre-date the SceneActions flow (no bolusId).
            when (val result = scenes.runScene(command.id)) {
                is SceneAutomationResult.Success        -> Unit
                is SceneAutomationResult.SceneNotFound,
                is SceneAutomationResult.SceneDisabled  -> sendError(rh.gs(R.string.scene_not_available, command.title))
                is SceneAutomationResult.Failed         -> sendError(result.message ?: rh.gs(R.string.scene_not_available, command.title))
                is SceneAutomationResult.ChainCompleted -> Unit
            }
        }
    }

    private suspend fun handleSceneStopPreCheck() {
        // Build confirm locally — no master round-trip needed before showing "End active scene".
        // The watch waits for RemoteDelivered (deferConfirm) while the stop relays to master.
        if (!scenes.isAnySceneActive()) return sendError(rh.gs(app.aaps.core.ui.R.string.scene_ended))
        sendToWear(
            EventData.ConfirmAction(
                title = rh.gs(app.aaps.core.ui.R.string.scenes),
                message = "",
                returnCommand = EventData.ActionSceneStopConfirmed(),
                lines = listOf(EventData.ConfirmActionLine(ConfirmationRole.NORMAL.name, rh.gs(app.aaps.core.ui.R.string.scene_end_active))),
                deferConfirm = config.AAPSCLIENT
            )
        )
    }

    // internal (not private) so DataHandlerMobileWearBolusTest can drive it without RxBus scaffolding.
    internal suspend fun handleQuickWizardPreCheck(command: EventData.ActionQuickWizardPreCheck) {
        // Branch on the entry mode exactly like the phone (MainViewModel.executeQuickWizard): a fixed INSULIN/CARBS
        // button goes through the generic BatchExecutor (no wizard recompute — that would deliver insulin for a
        // carbs-only button), while a WIZARD button recomputes the dose. All three stay role-transparent (MASTER →
        // local; CLIENT → signed round-trip) and the master is the single capping + confirmation authority.
        // A null entry (guid gone) falls through to the wizard path, which ships a proper "not available" error.
        val entry = quickWizard.get(command.guid)
        when (entry?.mode()) {
            QuickWizardMode.INSULIN -> sendBatchPreCheck(
                BatchAction.Bolus(
                    insulin = entry.insulin(), carbs = 0, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0,
                    recordOnly = false, notes = entry.buttonText(), timestamp = 0L, iCfg = null,
                    quickWizardGuid = command.guid // the MASTER marks the entry used on commit (SOT) — no local pref write
                ),
                label = rh.gs(app.aaps.core.ui.R.string.bolus)
            ) { bolusId -> EventData.ActionBolusConfirmed(bolusId) }

            QuickWizardMode.CARBS   -> {
                val hasEcarbs = entry.useEcarbs() == QuickWizardEntry.YES
                sendBatchPreCheck(
                    BatchAction.Bolus(
                        insulin = 0.0, carbs = entry.carbs(), carbsTimeOffsetMinutes = 0, carbsDurationHours = 0,
                        recordOnly = false, notes = entry.buttonText(), timestamp = 0L, iCfg = null,
                        eCarbsGrams = if (hasEcarbs) entry.carbs2() else 0,
                        eCarbsDelayMinutes = if (hasEcarbs) entry.time() else 0,
                        eCarbsDurationHours = if (hasEcarbs) entry.duration() else 0,
                        quickWizardGuid = command.guid // the MASTER marks the entry used on commit (SOT) — no local pref write
                    ),
                    label = rh.gs(app.aaps.core.ui.R.string.carbs)
                ) { bolusId -> EventData.ActionBolusConfirmed(bolusId) }
            }

            else                    -> {
                // Role-transparent recompute: MASTER computes + caps + parks + authors lines locally; CLIENT relays a
                // BolusPrepare(guid) to the master (gated on masterReachable). The watch renders the master's EXACT lines.
                contacting()
                shipPrepared(
                    wizardExecutor.prepare(WizardExecutor.WizardSource.QuickWizard(command.guid), rh.gs(app.aaps.core.ui.R.string.boluswizard)),
                    rh.gs(app.aaps.core.ui.R.string.boluswizard)
                ) { EventData.ActionWizardConfirmed(it) }
            }
        }
    }

    // internal (not private) so DataHandlerMobileWearBolusTest can drive it without RxBus scaffolding.
    internal suspend fun handleBolusPreCheck(command: EventData.ActionBolusPreCheck) {
        // Role-transparent: MASTER caps + parks + authors lines locally; CLIENT relays a BatchPrepare to the master
        // (gated on masterReachable). The watch renders the master's lines and echoes back only the bolusId on confirm
        // (consume-once) — no locally-capped amount travels back. The gate is now masterReachable (inside BatchExecutor),
        // not a blanket client refusal. NOTE: a carbs-only watch *bolus* on an offline client is blocked here — offline
        // carb entry goes through eCarbs (handleECarbsPreCheck), which stays local + NS-syncs.
        contacting()
        shipPrepared(
            batchExecutor.prepare(
                listOf(
                    BatchAction.Bolus(
                        insulin = command.insulin, carbs = command.carbs, carbsTimeOffsetMinutes = 0, carbsDurationHours = 0,
                        recordOnly = false, notes = "", timestamp = 0L, iCfg = null
                    )
                ),
                Sources.Wear, rh.gs(app.aaps.core.ui.R.string.overview_treatment_label)
            ),
            rh.gs(app.aaps.core.ui.R.string.overview_treatment_label)
        ) { EventData.ActionBolusConfirmed(it) }
    }

    internal suspend fun handleECarbsPreCheck(command: EventData.ActionECarbsPreCheck) {
        sendBatchPreCheck(
            BatchAction.Bolus(
                insulin = 0.0, carbs = command.carbs, carbsTimeOffsetMinutes = command.carbsTimeShift, carbsDurationHours = command.duration,
                recordOnly = false, notes = "", timestamp = 0L, iCfg = null
            )
        ) { EventData.ActionECarbsConfirmed(it) }
    }

    /**
     * Shared wear eCarbs precheck: cap + park + author the confirmation on the MASTER via the role-transparent
     * [BatchExecutor] (MASTER → local prepareBatch; CLIENT → signed BatchPrepare so the master records them), then push
     * the master-authored [lines][EventData.ConfirmAction.lines] to the watch with the parked bolusId. The wear ✓
     * commits by id (consume-once). (Was a local-only `wizardBolusExecutor.prepareBatch`; now everything runs on the master.)
     */
    private suspend fun sendBatchPreCheck(
        bolus: BatchAction.Bolus,
        label: String = rh.gs(app.aaps.core.ui.R.string.overview_treatment_label),
        returnCommand: (bolusId: Long) -> EventData
    ) {
        contacting()
        shipPrepared(batchExecutor.prepare(listOf(bolus), Sources.Wear, label), label, returnCommand)
    }

    /** Show the watch's transient "Contacting master…" spinner while a CLIENT→master round-trip is in flight (no-op on a master). */
    private fun contacting() {
        if (config.AAPSCLIENT) sendToWear(EventData.ContactingMaster)
    }

    /**
     * Relay a role-transparent PREPARE ([BatchExecutor]/[WizardExecutor]) result to the watch: a [ActionProgress.Prepared]
     * becomes the master-authored confirmation lines screen (carrying only the parked [returnCommand] bolusId); any
     * failure becomes a [sendError]. [title] is the watch's curved header. On a CLIENT the confirm is marked
     * [EventData.ConfirmAction.deferConfirm] so the watch waits for the master's real commit terminal (no false "sent").
     */
    private fun shipPrepared(progress: ActionProgress, title: String, returnCommand: (bolusId: Long) -> EventData) {
        when (progress) {
            is ActionProgress.Prepared -> sendToWear(
                EventData.ConfirmAction(
                    title, message = "",
                    returnCommand = returnCommand(progress.id),
                    lines = progress.lines.map { EventData.ConfirmActionLine(it.role.name, it.text) },
                    deferConfirm = config.AAPSCLIENT,
                    wizardDetail = progress.wizardDetail,
                )
            )

            else                       -> sendError(relayReason(progress))
        }
    }

    /**
     * Map a role-transparent COMMIT result: [ActionProgress.Applied] runs [onApplied] and — on a CLIENT — tells the
     * watch to show its (deferred) success animation via [EventData.RemoteDelivered]; any failure → [sendError].
     * On a master the watch already showed success locally on ✓ (the confirm wasn't deferred), so no RemoteDelivered.
     */
    private fun onCommitResult(progress: ActionProgress, onApplied: () -> Unit = {}) {
        when (progress) {
            is ActionProgress.Applied -> {
                onApplied()
                if (config.AAPSCLIENT) sendToWear(EventData.RemoteDelivered)
            }

            else                      -> sendError(relayReason(progress))
        }
    }

    /**
     * Localized failure text for a relayed prepare/commit. [Unconfirmed][ActionProgress.Unconfirmed] (commit timed out —
     * the dose state is UNKNOWN) gets a distinct, do-not-re-bolus message, NOT the same wording as a definite rejection.
     */
    private fun relayReason(progress: ActionProgress): String = when (progress) {
        is ActionProgress.Unconfirmed -> rh.gs(app.aaps.core.ui.R.string.clientcontrol_unconfirmed_wear)
        is ActionProgress.Rejected    -> progress.detail ?: rh.gs(progress.reason.failTextResId())
        else                          -> rh.gs(app.aaps.core.ui.R.string.error)
    }

    private suspend fun handleFillPresetPreCheck(command: EventData.ActionFillPresetPreCheck) {
        if (rejectIfAapsClient()) return
        runningModeGuard.rejectionMessage(PumpCommandGate.CommandKind.BOLUS)?.let {
            sendError(it)
            return
        }
        val amount: Double = when (command.button) {
            1    -> preferences.get(DoubleKey.ActionsFillButton1)
            2    -> preferences.get(DoubleKey.ActionsFillButton2)
            3    -> preferences.get(DoubleKey.ActionsFillButton3)
            else -> return
        }
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(amount, aapsLogger)).value()
        var message = rh.gs(app.aaps.core.ui.R.string.prime_fill) + ": " + insulinAfterConstraints + rh.gs(R.string.units_short)
        if (insulinAfterConstraints - amount != 0.0) message += "\n" + rh.gs(app.aaps.core.ui.R.string.constraint_applied)
        sendToWear(
            EventData.ConfirmAction(
                rh.gs(app.aaps.core.ui.R.string.confirm).uppercase(), message,
                returnCommand = EventData.ActionFillConfirmed(insulinAfterConstraints)
            )
        )
    }

    private suspend fun handleFillPreCheck(command: EventData.ActionFillPreCheck) {
        if (rejectIfAapsClient()) return
        runningModeGuard.rejectionMessage(PumpCommandGate.CommandKind.BOLUS)?.let {
            sendError(it)
            return
        }
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(command.insulin, aapsLogger)).value()
        var message = rh.gs(app.aaps.core.ui.R.string.prime_fill) + ": " + insulinAfterConstraints + rh.gs(R.string.units_short)
        if (insulinAfterConstraints - command.insulin != 0.0) message += "\n" + rh.gs(app.aaps.core.ui.R.string.constraint_applied)
        sendToWear(
            EventData.ConfirmAction(
                rh.gs(app.aaps.core.ui.R.string.confirm).uppercase(), message,
                returnCommand = EventData.ActionFillConfirmed(insulinAfterConstraints)
            )
        )
    }

    private suspend fun handleProfileSwitchSendInitialData() {
        val activeProfileSwitch = persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now())
        if (activeProfileSwitch != null) { // read CPP values
            sendToWear(EventData.ActionProfileSwitchOpenActivity(T.msecs(activeProfileSwitch.originalTimeshift).hours().toInt(), activeProfileSwitch.originalPercentage, activeProfileSwitch.originalDuration.toInt()))
        } else {
            sendError(rh.gs(R.string.no_active_profile))
            return
        }

    }

    // internal + suspend so DataHandlerMobileWearBolusTest can drive it; routes through the role-transparent relay.
    internal suspend fun handleProfileSwitchPreCheck(command: EventData.ActionProfileSwitchPreCheck) {
        // Validate (active profile + CPP ranges) + park + author the confirmation on the MASTER via BatchExecutor
        // (MASTER → local; CLIENT → signed BatchPrepare so the master applies the switch, not the follower locally).
        // The wear ✓ then commits by bolusId → the shared applyProfileSwitch on the master.
        val label = rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch)
        contacting()
        shipPrepared(
            batchExecutor.prepare(listOf(BatchAction.ProfileSwitch(command.percentage, command.timeShift, command.duration)), Sources.Wear, label),
            label
        ) { EventData.ActionProfileSwitchConfirmed(it) }
    }

    private fun formatGlucose(value: Double, isMgdl: Boolean): String {
        return if (isMgdl)
            String.format(Locale.getDefault(), "%.0f mg/dL", value)
        else
            String.format(Locale.getDefault(), "%.1f mmol/L", value)
    }

    // internal + suspend so DataHandlerMobileWearBolusTest can drive it; routes through the shared prepare/confirm.
    internal suspend fun handleTempTargetPreCheck(action: EventData.ActionTempTargetPreCheck) {
        val presetIsMGDL = profileFunction.getUnits() == GlucoseUnit.MGDL

        // Park + author the confirmation on the MASTER via BatchExecutor (MASTER → local applyTempTarget;
        // CLIENT → signed BatchPrepare so the master applies it, not the follower locally). Wear ✓ commits by id.
        suspend fun sendTt(tt: BatchAction.TempTarget) {
            val label = rh.gs(app.aaps.core.ui.R.string.temporary_target)
            contacting()
            shipPrepared(batchExecutor.prepare(listOf(tt), Sources.Wear, label), label) { EventData.ActionTempTargetConfirmed(it) }
        }

        suspend fun sendPreset(reason: TT.Reason) {
            val mgdl = preferences.ttTargetMgdl(reason)
            sendTt(BatchAction.TempTarget(reason.text, mgdl, mgdl, preferences.ttDurationMinutes(reason), 0))
        }

        when (action.command) {
            EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_ACTIVITY -> sendPreset(TT.Reason.ACTIVITY)
            EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_HYPO     -> sendPreset(TT.Reason.HYPOGLYCEMIA)
            EventData.ActionTempTargetPreCheck.TempTargetCommand.PRESET_EATING   -> sendPreset(TT.Reason.EATING_SOON)
            EventData.ActionTempTargetPreCheck.TempTargetCommand.CANCEL          -> sendTt(BatchAction.TempTarget(TT.Reason.WEAR.text, 0.0, 0.0, 0, 0))

            EventData.ActionTempTargetPreCheck.TempTargetCommand.MANUAL          -> {
                if (profileFunction.getUnits() == GlucoseUnit.MGDL != action.isMgdl) {
                    sendError(rh.gs(R.string.wear_action_tempt_unit_error))
                    return
                }
                if (action.duration == 0) {
                    sendTt(BatchAction.TempTarget(TT.Reason.WEAR.text, 0.0, 0.0, 0, 0))
                    return
                }
                val lowMgdl = if (action.isMgdl) action.low else action.low * Constants.MMOLL_TO_MGDL
                val highMgdl = if (action.isMgdl) action.high else action.high * Constants.MMOLL_TO_MGDL
                if (lowMgdl < HardLimits.LIMIT_TEMP_MIN_BG[0] || lowMgdl > HardLimits.LIMIT_TEMP_MIN_BG[1]) {
                    sendError(rh.gs(R.string.wear_action_tempt_min_bg_error))
                    return
                }
                if (highMgdl < HardLimits.LIMIT_TEMP_MAX_BG[0] || highMgdl > HardLimits.LIMIT_TEMP_MAX_BG[1]) {
                    sendError(rh.gs(R.string.wear_action_tempt_max_bg_error))
                    return
                }
                if (lowMgdl > highMgdl) {
                    sendError(rh.gs(R.string.wear_action_tempt_range_error, formatGlucose(action.low, presetIsMGDL), formatGlucose(action.high, presetIsMGDL)))
                    return
                }
                sendTt(BatchAction.TempTarget(TT.Reason.WEAR.text, lowMgdl, highMgdl, action.duration, 0))
            }
        }
    }

    // To make sure WearOS-sent running mode change is constrained
    private var lastAuthorizedRunningModeChangeTS: Long? = null
    private var lastRunningModes: List<AvailableRunningMode>? = null

    // internal so DataHandlerMobileWearBolusTest can negotiate the available modes (populating the nonce + tile list).
    internal suspend fun handleAvailableRunningModes() {
        if (!profileFunction.isProfileValid("WearDataHandler_LoopChangeState")) return

        val pumpDescription = activePlugin.activePump.pumpDescription
        val disconnectDurs = arrayListOf<Int>().apply {
            if (pumpDescription.tempDurationStep15mAllowed) add(15)
            if (pumpDescription.tempDurationStep30mAllowed) add(30)
            for (i in listOf(1, 2, 3)) add(i * 60)
        }

        suspend fun mapMode(mode: RM.Mode): AvailableRunningMode? =
            when (mode) {
                RM.Mode.CLOSED_LOOP       -> AvailableRunningMode(AvailableRunningMode.RunningMode.LOOP_CLOSED)
                RM.Mode.CLOSED_LOOP_LGS   -> AvailableRunningMode(AvailableRunningMode.RunningMode.LOOP_LGS)
                RM.Mode.OPEN_LOOP         -> AvailableRunningMode(AvailableRunningMode.RunningMode.LOOP_OPEN)
                RM.Mode.DISABLED_LOOP     -> AvailableRunningMode(AvailableRunningMode.RunningMode.LOOP_DISABLE)
                RM.Mode.SUPER_BOLUS       -> null
                RM.Mode.DISCONNECTED_PUMP -> AvailableRunningMode(AvailableRunningMode.RunningMode.PUMP_DISCONNECT, disconnectDurs)
                RM.Mode.SUSPENDED_BY_PUMP -> null
                RM.Mode.SUSPENDED_BY_USER -> AvailableRunningMode(AvailableRunningMode.RunningMode.LOOP_USER_SUSPEND, listOf(1, 2, 3, 10).map { it * 60 })
                RM.Mode.SUSPENDED_BY_DST  -> null
                RM.Mode.RESUME            -> if (loop.runningMode() == RM.Mode.DISCONNECTED_PUMP)
                    AvailableRunningMode(AvailableRunningMode.RunningMode.PUMP_RECONNECT)
                else
                    AvailableRunningMode(AvailableRunningMode.RunningMode.LOOP_RESUME)
            }

        val allStates = loop.allowedNextModes().mapNotNull { mapMode(it) }
        // LOOP_DISABLE is dropped when LOOP_USER_SUSPEND is present to fit within 4 tile slots.
        val states = if (allStates.any { it.state == AvailableRunningMode.RunningMode.LOOP_USER_SUSPEND })
            allStates.filter { it.state != AvailableRunningMode.RunningMode.LOOP_DISABLE }
        else allStates
        // Only rotate the timestamp when available modes actually change.
        // Keeping the old TS when modes are identical lets in-flight tile taps (e.g. from a
        // just-woken watch) succeed without a "Please try again" race against onTileEnterEvent.
        if (states != lastRunningModes || lastAuthorizedRunningModeChangeTS == null) {
            lastAuthorizedRunningModeChangeTS = System.currentTimeMillis()
            lastRunningModes = states
        }
        sendToWear(
            EventData.RunningModeList(lastAuthorizedRunningModeChangeTS!!, states)
        )
    }

    /** Wire (wear-tile) running mode → domain [RM.Mode]; null for the non-user-selectable states (gated out anyway). */
    private fun AvailableRunningMode.RunningMode.toRmMode(): RM.Mode? = when (this) {
        AvailableRunningMode.RunningMode.LOOP_CLOSED       -> RM.Mode.CLOSED_LOOP
        AvailableRunningMode.RunningMode.LOOP_LGS          -> RM.Mode.CLOSED_LOOP_LGS
        AvailableRunningMode.RunningMode.LOOP_OPEN         -> RM.Mode.OPEN_LOOP
        AvailableRunningMode.RunningMode.LOOP_DISABLE      -> RM.Mode.DISABLED_LOOP
        AvailableRunningMode.RunningMode.LOOP_RESUME,
        AvailableRunningMode.RunningMode.PUMP_RECONNECT    -> RM.Mode.RESUME

        AvailableRunningMode.RunningMode.LOOP_USER_SUSPEND -> RM.Mode.SUSPENDED_BY_USER
        AvailableRunningMode.RunningMode.PUMP_DISCONNECT   -> RM.Mode.DISCONNECTED_PUMP
        AvailableRunningMode.RunningMode.SUPERBOLUS,
        AvailableRunningMode.RunningMode.LOOP_UNKNOWN,
        AvailableRunningMode.RunningMode.LOOP_PUMP_SUSPEND -> null
    }

    // internal + suspend so DataHandlerMobileWearBolusTest can drive it; routes through the shared prepare/confirm.
    internal suspend fun handleRunningModeSelected(action: EventData.RunningModeSelected) {
        if (action.timeStamp != lastAuthorizedRunningModeChangeTS) return sendError(rh.gs(R.string.wear_action_loop_state_unauthorized))
        val newState = lastRunningModes?.elementAtOrNull(action.index) ?: return sendError(rh.gs(R.string.wear_action_loop_state_invalid))
        val mode = newState.state.toRmMode() ?: return sendError(rh.gs(R.string.wear_action_loop_state_invalid))
        // Park + author the confirmation on the MASTER via BatchExecutor (which re-validates the transition + caps the
        // duration). MASTER → local prepareBatch; CLIENT → signed BatchPrepare so the MASTER applies the mode change
        // (not the follower locally). The wear ✓ then commits by bolusId → the shared applyRunningMode on the master.
        val label = rh.gs(R.string.wear_action_running_mode_title)
        contacting()
        shipPrepared(
            batchExecutor.prepare(listOf(BatchAction.RunningMode(mode, action.duration ?: 0)), Sources.Wear, label),
            label
        ) { EventData.RunningModeConfirmed(it) }
    }

    internal suspend fun handleRunningModeConfirmed(action: EventData.RunningModeConfirmed) {
        // Commit the parked mode change through the relay (MASTER → local applyRunningMode; CLIENT → master applies),
        // then refresh the wear tiles so the next negotiation reflects the new mode (and issues a fresh nonce).
        contacting() // CLIENT: show the spinner during the commit round-trip too (no-op on master).
        onCommitResult(batchExecutor.commit(action.bolusId, Sources.Wear, rh.gs(R.string.wear_action_running_mode_title)))
        handleAvailableRunningModes()
    }

    private fun QuickWizardEntry.toWear(): EventData.QuickWizard.QuickWizardEntry =
        EventData.QuickWizard.QuickWizardEntry(
            guid = guid(),
            buttonText = buttonText(),
            carbs = carbs(),
            validFrom = validFrom(),
            validTo = validTo(),
            lastUsed = lastUsed(),
            mode = mode().value,
            insulin = insulin()
        )

    private fun sendQuickWizardListToWear() =
        sendToWear(EventData.QuickWizard(ArrayList(quickWizard.list().filter { e -> e.forDevice(QuickWizardEntry.DEVICE_WATCH) }.map { e -> e.toWear() })))

    suspend fun resendData(from: String) {
        aapsLogger.debug(LTag.WEAR, "Sending data to wear from $from")
        // Wear can request a resend before MainApp's init scope has populated pluginStore.plugins
        // (e.g. immediately after device reboot). Skip until the active pump is selectable —
        // the wear app will retry on its next state change.
        if (!config.appInitialized) {
            aapsLogger.debug(LTag.WEAR, "Skipping resendData — app not yet initialized")
            return
        }
        // SingleBg
        iobCobCalculator.ads.lastBg()?.let { sendToWear(getSingleBG(it)) }
        // Preferences
        sendToWear(
            EventData.Preferences(
                timeStamp = System.currentTimeMillis(),
                wearControl = preferences.get(BooleanKey.WearControl),
                unitsMgdl = profileFunction.getUnits() == GlucoseUnit.MGDL,
                bolusPercentage = preferences.get(IntKey.OverviewBolusPercentage),
                maxCarbs = preferences.get(IntKey.SafetyMaxCarbs),
                maxBolus = preferences.get(DoubleKey.SafetyMaxBolus),
                insulinButtonIncrement1 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement1),
                insulinButtonIncrement2 = preferences.get(DoubleKey.OverviewInsulinButtonIncrement2),
                carbsButtonIncrement1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1),
                carbsButtonIncrement2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2)
            )
        )
        // QuickWizard
        sendQuickWizardListToWear()
        //UserAction
        sendUserActions()
        // Scenes
        sendScenes()
        sendActiveSceneState(scenes.isAnySceneActive())
        // GraphData
        iobCobCalculator.ads.getBucketedDataTableCopy()?.let { bucketedData ->
            // Hoist out of the per-bucket map: getGlucoseStatusData copies the bucketed table and runs a polynomial fit on every call.
            val glucoseStatus = glucoseStatusProvider.getGlucoseStatusData(true)
            val units = profileFunction.getUnits()
            val lowLine = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewLowMark), units)
            val highLine = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewHighMark), units)
            val slopeArrow = (trendCalculator.getTrendArrow(iobCobCalculator.ads) ?: TrendArrow.NONE).symbol
            sendToWear(EventData.GraphData(ArrayList(bucketedData.map { buildSingleBg(it, glucoseStatus, units, lowLine, highLine, slopeArrow) })))
        }
        // Treatments
        sendTreatments()
        // Status
        // Keep status last. Wear start refreshing after status received
        sendStatus(from)
        handleAvailableRunningModes()
    }

    private fun AutomationEvent.toWear(now: Long): EventData.UserAction.UserActionEntry =
        EventData.UserAction.UserActionEntry(timeStamp = now, id = id, title = title)

    suspend fun sendUserActions() {
        val now = System.currentTimeMillis()
        val filtered = mutableListOf<AutomationEvent>()
        // Automation executes on master only — clients show no user-action tiles (tapping one would
        // run nothing). Send an empty list so the watch clears any stale tiles.
        if (automation.executionEnabled)
            for (event in automation.events.value) {
                if (event.userAction && event.isEnabled && event.canRun()) filtered.add(event)
            }
        sendToWear(EventData.UserAction(ArrayList(filtered.map { it.toWear(now) })))
    }

    private fun Scene.toWear(now: Long): EventData.SceneList.SceneEntry =
        EventData.SceneList.SceneEntry(timeStamp = now, id = id, title = name)

    fun sendScenes() {
        val now = System.currentTimeMillis()
        val enabled = scenes.getScenes().filter { it.isEnabled }
        sendToWear(EventData.SceneList(ArrayList(enabled.map { it.toWear(now) })))
    }

    fun sendActiveSceneState(active: Boolean) {
        sendToWear(EventData.ActiveSceneState(active))
    }

    private suspend fun sendTreatments() {
        val now = System.currentTimeMillis()
        val startTimeWindow = now - (60000 * 60 * 5.5).toLong()
        val basals = arrayListOf<EventData.TreatmentData.Basal>()
        val temps = arrayListOf<EventData.TreatmentData.TempBasal>()
        val boluses = arrayListOf<EventData.TreatmentData.Treatment>()
        val predictions = arrayListOf<EventData.SingleBg>()
        if (!config.appInitialized) return
        val profile = profileFunction.getProfile() ?: return
        var beginBasalSegmentTime = startTimeWindow
        var runningTime = startTimeWindow
        var beginBasalValue = profile.getBasal(beginBasalSegmentTime)
        var endBasalValue = beginBasalValue
        var tb1 = processedTbrEbData.getTempBasalIncludingConvertedExtended(runningTime)
        var tb2: TB?
        var tbBefore = beginBasalValue
        var tbAmount = beginBasalValue
        var tbStart = runningTime
        if (tb1 != null) {
            val profileTB = profileFunction.getProfile(runningTime)
            if (profileTB != null) {
                tbAmount = tb1.convertedToAbsolute(runningTime, profileTB)
                tbStart = runningTime
            }
        }
        while (runningTime < now) {
            val profileTB = profileFunction.getProfile(runningTime) ?: return
            //basal rate
            endBasalValue = profile.getBasal(runningTime)
            if (endBasalValue != beginBasalValue) {
                //push the segment we recently left
                basals.add(EventData.TreatmentData.Basal(beginBasalSegmentTime, runningTime, beginBasalValue))

                //begin new Basal segment
                beginBasalSegmentTime = runningTime
                beginBasalValue = endBasalValue
            }

            //temps
            tb2 = processedTbrEbData.getTempBasalIncludingConvertedExtended(runningTime)
            when {
                tb1 == null && tb2 == null -> {
                    //no temp stays no temp
                }

                tb1 != null && tb2 == null -> {
                    //temp is over -> push it
                    temps.add(EventData.TreatmentData.TempBasal(tbStart, tbBefore, runningTime, endBasalValue, tbAmount))
                    tb1 = null
                }

                tb1 == null && tb2 != null -> {
                    //temp begins
                    tb1 = tb2
                    tbStart = runningTime
                    tbBefore = endBasalValue
                    tbAmount = tb1.convertedToAbsolute(runningTime, profileTB)
                }

                tb1 != null && tb2 != null -> {
                    val currentAmount = tb2.convertedToAbsolute(runningTime, profileTB)
                    if (currentAmount != tbAmount) {
                        temps.add(EventData.TreatmentData.TempBasal(tbStart, tbBefore, runningTime, currentAmount, tbAmount))
                        tbStart = runningTime
                        tbBefore = tbAmount
                        tbAmount = currentAmount
                        tb1 = tb2
                    }
                }
            }
            runningTime += (5 * 60 * 1000L)
        }
        if (beginBasalSegmentTime != runningTime) {
            //push the remaining segment
            basals.add(EventData.TreatmentData.Basal(beginBasalSegmentTime, runningTime, beginBasalValue))
        }
        if (tb1 != null) {
            tb2 = processedTbrEbData.getTempBasalIncludingConvertedExtended(now) //use "now" to express current situation
            if (tb2 == null) {
                //express the canceled temp by painting it down one minute early
                temps.add(EventData.TreatmentData.TempBasal(tbStart, tbBefore, now - 60 * 1000, endBasalValue, tbAmount))
            } else {
                //express currently running temp by painting it a bit into the future
                val profileNow = profileFunction.getProfile(now)
                val currentAmount = tb2.convertedToAbsolute(now, profileNow!!)
                if (currentAmount != tbAmount) {
                    temps.add(EventData.TreatmentData.TempBasal(tbStart, tbBefore, now, tbAmount, tbAmount))
                    temps.add(EventData.TreatmentData.TempBasal(now, tbAmount, runningTime + 5 * 60 * 1000, currentAmount, currentAmount))
                } else {
                    temps.add(EventData.TreatmentData.TempBasal(tbStart, tbBefore, runningTime + 5 * 60 * 1000, tbAmount, tbAmount))
                }
            }
        } else {
            tb2 = processedTbrEbData.getTempBasalIncludingConvertedExtended(now) //use "now" to express current situation
            if (tb2 != null) {
                //onset at the end
                val profileTB = profileFunction.getProfile(runningTime)
                val currentAmount = tb2.convertedToAbsolute(runningTime, profileTB!!)
                temps.add(EventData.TreatmentData.TempBasal(now - 60 * 1000, endBasalValue, runningTime + 5 * 60 * 1000, currentAmount, currentAmount))
            }
        }
        persistenceLayer.getBolusesFromTimeIncludingInvalid(startTimeWindow, true)
            .stream()
            .filter { (_, _, _, _, _, _, _, _, _, type) -> type !== BS.Type.PRIMING }
            .forEach { (_, _, _, isValid, _, _, timestamp, _, amount, type) -> boluses.add(EventData.TreatmentData.Treatment(timestamp, amount, 0.0, type === BS.Type.SMB, isValid)) }
        persistenceLayer.getCarbsFromTimeExpanded(startTimeWindow, true)
            .forEach { (_, _, _, isValid, _, _, timestamp, _, _, amount) -> boluses.add(EventData.TreatmentData.Treatment(timestamp, 0.0, amount, false, isValid)) }
        val apsResult = if (config.APS) {
            val lastRun = loop.lastRun
            if (lastRun?.request?.hasPredictions == true) {
                lastRun.constraintsProcessed
            } else null
        } else {
            processedDeviceStatusData.getAPSResult()
        }

        apsResult
            ?.predictionsAsGv
            ?.filter { it.value > 39 }
            ?.forEach { bg ->
                predictions.add(
                    EventData.SingleBg(
                        dataset = 0,
                        timeStamp = bg.timestamp,
                        glucoseUnits = GlucoseUnit.MGDL.asText,
                        sgv = bg.value,
                        high = 0.0,
                        low = 0.0,
                        color = predictionColor(bg)
                    )
                )
            }
        sendToWear(EventData.TreatmentData(temps, basals, boluses, predictions))
    }

    private fun predictionColor(data: GV): Int {
        val isDark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val colors = if (isDark) DarkGeneralColors else LightGeneralColors
        return when (data.sourceSensor) {
            SourceSensor.IOB_PREDICTION   -> colors.iobPrediction.toArgb()
            SourceSensor.COB_PREDICTION   -> colors.cobPrediction.toArgb()
            SourceSensor.A_COB_PREDICTION -> colors.aCobPrediction.toArgb()
            SourceSensor.UAM_PREDICTION   -> colors.uamPrediction.toArgb()
            SourceSensor.ZT_PREDICTION    -> colors.ztPrediction.toArgb()
            else                          -> android.graphics.Color.WHITE
        }
    }

    private suspend fun sendStatus(caller: String) {
        val profile = profileFunction.getProfile()
        var status = rh.gs(app.aaps.core.ui.R.string.noprofile)
        var iobSum = ""
        var iobDetail = ""
        var cobString = ""
        var currentBasal = ""
        var bgiString = ""
        if (config.appInitialized && profile != null) {
            val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
            val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
            iobSum = decimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob)
            iobDetail = "(${decimalFormatter.to2Decimal(bolusIob.iob)}|${decimalFormatter.to2Decimal(basalIob.basaliob)})"
            cobString = iobCobCalculator.getCobInfo("WatcherUpdaterService").generateCOBString(decimalFormatter)
            currentBasal =
                processedTbrEbData.getTempBasalIncludingConvertedExtended(System.currentTimeMillis())?.toStringShort(rh) ?: rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, profile.getBasal())

            //bgi
            if (glucoseStatusProvider.glucoseStatusData != null) {
                val bgi = -(bolusIob.activity + basalIob.activity) * 5 * profileUtil.fromMgdlToUnits(profile.getIsfMgdl("DataHandlerMobile $caller"))
                bgiString = "" + (if (bgi >= 0) "+" else "") + decimalFormatter.to1Decimal(bgi)
            }
            status = generateStatusString(profile)
        }

        //batteries
        val phoneBattery = receiverStatusStore.batteryLevel
        val rigBattery = processedDeviceStatusData.uploaderStatus.trim { it <= ' ' }
        //OpenAPS status
        val openApsStatus =
            if (config.APS) loop.lastRun?.let { if (it.lastTBREnact != 0L) it.lastTBREnact else -1 } ?: -1
            else processedDeviceStatusData.openApsTimestamp
        // Patient name for followers
        val patientName = preferences.get(StringKey.GeneralPatientName)
        //temptarget
        val units = profileFunction.getUnits()
        var tempTargetLevel = 0
        var tempTargetDuration = -1L
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())?.let { tempTarget ->
            tempTargetLevel = 2     // Yellow
            tempTargetDuration = tempTarget.end - dateUtil.now()
            profileUtil.toTargetRangeString(tempTarget.lowTarget, tempTarget.highTarget, GlucoseUnit.MGDL, units)
        } ?: profileFunction.getProfile()?.let { profile ->
            // If the target is not the same as set in the profile then oref has overridden it
            val adjustedTarget = profile.apsAdjustedTargetMgdl(loop, config, processedDeviceStatusData)
            if (adjustedTarget != null) {
                tempTargetLevel = 1     // Green
                profileUtil.toTargetRangeString(adjustedTarget, adjustedTarget, GlucoseUnit.MGDL, units)
            } else {
                profileUtil.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), GlucoseUnit.MGDL, units)
            }
        } ?: ""
        // Reservoir Level
        val pump = activePlugin.activePump
        val iCfg = insulin.iCfg
        val maxReading = pump.pumpDescription.maxReservoirReading.toDouble()
        val reservoir = pump.reservoirLevel.value.iU(iCfg.concentration).let { if (pump.pumpDescription.isPatchPump && it > maxReading) maxReading else it }
        val reservoirString = if (reservoir > 0) decimalFormatter.to0Decimal(reservoir, rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)) else ""
        val resUrgent = preferences.get(IntKey.OverviewResCritical)
        val resWarn = preferences.get(IntKey.OverviewResWarning)
        val reservoirLevel = when {
            reservoir <= resUrgent -> 2
            reservoir <= resWarn   -> 1
            else                   -> 0
        }

        sendToWear(
            EventData.Status(
                dataset = 0,
                externalStatus = status,
                iobSum = iobSum,
                iobDetail = iobDetail,
                cob = cobString,
                currentBasal = currentBasal,
                battery = phoneBattery.toString(),
                rigBattery = rigBattery,
                openApsStatus = openApsStatus,
                bgi = bgiString,
                batteryLevel = if (phoneBattery >= 30) 1 else 0,
                patientName = patientName,
                tempTarget = tempTarget,
                tempTargetLevel = tempTargetLevel,
                tempTargetDuration = tempTargetDuration,
                reservoirString = reservoirString,
                reservoir = reservoir,
                reservoirLevel = reservoirLevel
            )
        )
    }

    private fun deltaString(deltaMGDL: Double, deltaMMOL: Double, units: GlucoseUnit): String {
        var deltaString = if (deltaMGDL >= 0) "+" else "-"
        deltaString += if (units == GlucoseUnit.MGDL) {
            decimalFormatter.to0Decimal(abs(deltaMGDL))
        } else {
            decimalFormatter.to1Decimal(abs(deltaMMOL))
        }
        return deltaString
    }

    private fun deltaStringDetailed(deltaMGDL: Double, deltaMMOL: Double, units: GlucoseUnit): String {
        var deltaStringDetailed = if (deltaMGDL >= 0) "+" else "-"
        deltaStringDetailed += if (units == GlucoseUnit.MGDL) {
            decimalFormatter.to1Decimal(abs(deltaMGDL))
        } else {
            decimalFormatter.to2Decimal(abs(deltaMMOL))
        }
        return deltaStringDetailed
    }

    private fun getSingleBG(glucoseValue: InMemoryGlucoseValue): EventData.SingleBg {
        val glucoseStatus = glucoseStatusProvider.getGlucoseStatusData(true)
        val units = profileFunction.getUnits()
        val lowLine = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewLowMark), units)
        val highLine = profileUtil.convertToMgdl(preferences.get(UnitDoubleKey.OverviewHighMark), units)
        val slopeArrow = (trendCalculator.getTrendArrow(iobCobCalculator.ads) ?: TrendArrow.NONE).symbol
        return buildSingleBg(glucoseValue, glucoseStatus, units, lowLine, highLine, slopeArrow)
    }

    private fun buildSingleBg(
        glucoseValue: InMemoryGlucoseValue,
        glucoseStatus: GlucoseStatus?,
        units: GlucoseUnit,
        lowLine: Double,
        highLine: Double,
        slopeArrow: String
    ): EventData.SingleBg =
        EventData.SingleBg(
            dataset = 0,
            timeStamp = glucoseValue.timestamp,
            sgvString = profileUtil.stringInCurrentUnitsDetect(glucoseValue.recalculated),
            glucoseUnits = units.asText,
            slopeArrow = slopeArrow,
            delta = glucoseStatus?.let { deltaString(it.delta, it.delta * Constants.MGDL_TO_MMOLL, units) } ?: "--",
            deltaDetailed = glucoseStatus?.let { deltaStringDetailed(it.delta, it.delta * Constants.MGDL_TO_MMOLL, units) } ?: "--",
            avgDelta = glucoseStatus?.let { deltaString(it.shortAvgDelta, it.shortAvgDelta * Constants.MGDL_TO_MMOLL, units) } ?: "--",
            avgDeltaDetailed = glucoseStatus?.let { deltaStringDetailed(it.shortAvgDelta, it.shortAvgDelta * Constants.MGDL_TO_MMOLL, units) } ?: "--",
            sgvLevel = if (glucoseValue.recalculated > highLine) 1L else if (glucoseValue.recalculated < lowLine) -1L else 0L,
            sgv = glucoseValue.recalculated,
            high = highLine,
            low = lowLine,
            color = 0,
            deltaMgdl = glucoseStatus?.delta,
            avgDeltaMgdl = glucoseStatus?.shortAvgDelta
        )

    //Check for Temp-Target:
    // Read-only "Targets" section of the wear Loop-Status screen, as confirmation lines (template-built, translatable).
    private suspend fun targetsStatus(): List<ConfirmationLine> {
        if (!config.APS) return listOf(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.target_only_aps_mode)))
        val profile = profileFunction.getProfile() ?: return listOf(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.no_profile)))
        val out = mutableListOf(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(app.aaps.core.ui.R.string.loopstatus_targets)))
        persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())?.let { tt ->
            // Show the full low–high range (was passing lowTarget twice — a range TT only showed its low bound).
            out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(R.string.temp_target), profileUtil.toTargetRangeString(tt.lowTarget, tt.highTarget, GlucoseUnit.MGDL)))
            out += ConfirmationLine(ConfirmationRole.INFO, rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(R.string.until), dateUtil.timeString(tt.end)))
        }
        out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(R.string.default_range), profileUtil.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), GlucoseUnit.MGDL)))
        out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(R.string.target), profileUtil.fromMgdlToStringInUnits(profile.getTargetMgdl())))
        return out
    }

    // Read-only "OAPS result" section of the wear Loop-Status screen, as confirmation lines.
    private suspend fun oAPSResultStatus(): List<ConfirmationLine> {
        if (!config.APS) return listOf(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.aps_only)))
        val usedAPS = activePlugin.activeAPS ?: return listOf(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.last_aps_result_na)))
        val result = usedAPS.lastAPSResult ?: return listOf(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.last_aps_result_na)))
        val out = mutableListOf(ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(app.aaps.core.ui.R.string.loopstatus_OAPS_result)))
        out += when {
            !result.isChangeRequested()                -> ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(app.aaps.core.ui.R.string.nochangerequested))
            result.rate == 0.0 && result.duration == 0 -> ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(app.aaps.core.ui.R.string.cancel_temp))
            else                                       -> ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.rate_duration, result.rate, result.rate / ch.fromPump(activePlugin.activePump.baseBasalRate) * 100, result.duration))
        }
        out += ConfirmationLine(ConfirmationRole.INFO, rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(app.aaps.core.ui.R.string.reason), result.reason))
        return out
    }

    // Read-only "Loop" section of the wear Loop-Status screen, as confirmation lines. Decides enabled/disabled
    // closed/open + which plugin is APS.
    private suspend fun loopStatus(): List<ConfirmationLine> {
        val out = mutableListOf<ConfirmationLine>()
        val rm = loop.runningMode()
        when (rm) {
            RM.Mode.CLOSED_LOOP     -> out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.loop_status_closed))
            RM.Mode.OPEN_LOOP       -> out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.loop_status_open))
            RM.Mode.CLOSED_LOOP_LGS -> out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.loop_status_lgs))
            RM.Mode.DISABLED_LOOP   -> out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(R.string.loop_status_disabled))

            else                    -> { /* no line */
            }
        }
        if (rm.isLoopRunning()) {
            out += ConfirmationLine(ConfirmationRole.NORMAL, rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(R.string.aps), (activePlugin.activeAPS as? PluginBase)?.name ?: ""))
            loop.lastRun?.let { lastRun ->
                out += ConfirmationLine(ConfirmationRole.INFO, rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(R.string.last_run), dateUtil.timeString(lastRun.lastAPSRun)))
                if (lastRun.lastTBREnact != 0L) out += ConfirmationLine(ConfirmationRole.INFO, rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(R.string.last_enact), dateUtil.timeString(lastRun.lastTBREnact)))
            }
        }
        return out
    }

    private fun isOldData(historyList: List<TDD>): Boolean {
        val startsYesterday = activePlugin.activePump.pumpDescription.supportsTDDs
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        return historyList.size < 3 || df.format(Date(historyList[0].timestamp)) != df.format(Date(System.currentTimeMillis() - if (startsYesterday) 1000 * 60 * 60 * 24 else 0))
    }

    private suspend fun getTDDList(returnDummies: List<TDD>): MutableList<TDD> {
        var historyList = persistenceLayer.getLastTotalDailyDoses(10, false).toMutableList()
        //var historyList = databaseHelper.getTDDs().toMutableList()
        historyList = historyList.subList(0, min(10, historyList.size))
        // fill single gaps - only needed for Dana*R data
        val dummies: MutableList<TDD> = returnDummies.toMutableList()
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        for (i in 0 until historyList.size - 1) {
            val elem1 = historyList[i]
            val elem2 = historyList[i + 1]
            if (df.format(Date(elem1.timestamp)) != df.format(Date(elem2.timestamp + 25 * 60 * 60 * 1000))) {
                val dummy = TDD(timestamp = elem1.timestamp - T.hours(24).msecs(), bolusAmount = elem1.bolusAmount / 2, basalAmount = elem1.basalAmount / 2)
                dummies.add(dummy)
                elem1.basalAmount /= 2.0
                elem1.bolusAmount /= 2.0
            }
        }
        historyList.addAll(dummies)
        historyList.sortWith { lhs, rhs -> (rhs.timestamp - lhs.timestamp).toInt() }
        return historyList
    }

    private val TDD.total
        get() = if (totalAmount > 0) totalAmount else basalAmount + bolusAmount

    private suspend fun generateTDDMessage(historyList: MutableList<TDD>, dummies: List<TDD>): String {
        val profile = profileFunction.getProfile() ?: return rh.gs(R.string.no_profile)
        if (historyList.isEmpty()) {
            return rh.gs(R.string.no_history)
        }
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        var message = ""
        val refTDD = profile.baseBasalSum() * 2
        if (df.format(Date(historyList[0].timestamp)) == df.format(Date())) {
            val tdd = historyList[0].total
            historyList.removeAt(0)

            message += rh.gs(R.string.today) + ": " + rh.gs(R.string.tdd_line, tdd, 100 * tdd / refTDD) + "\n"
            message += "\n"
        }
        var weighted03 = 0.0
        var weighted05 = 0.0
        var weighted07 = 0.0
        historyList.reverse()
        for ((i, record) in historyList.withIndex()) {
            val tdd = record.total
            if (i == 0) {
                weighted03 = tdd
                weighted05 = tdd
                weighted07 = tdd
            } else {
                weighted07 = weighted07 * 0.3 + tdd * 0.7
                weighted05 = weighted05 * 0.5 + tdd * 0.5
                weighted03 = weighted03 * 0.7 + tdd * 0.3
            }
        }
        message += rh.gs(R.string.weighted) + ":\n"
        message += "0.3: " + rh.gs(R.string.tdd_line, weighted03, 100 * weighted03 / refTDD) + "\n"
        message += "0.5: " + rh.gs(R.string.tdd_line, weighted05, 100 * weighted05 / refTDD) + "\n"
        message += "0.7: " + rh.gs(R.string.tdd_line, weighted07, 100 * weighted07 / refTDD) + "\n"
        message += "\n"
        historyList.reverse()
        // add TDDs:
        for (record in historyList) {
            val tdd = record.total
            message += df.format(Date(record.timestamp)) + " " + rh.gs(R.string.tdd_line, tdd, 100 * tdd / refTDD)
            message += (if (dummies.contains(record)) "x" else "") + "\n"
        }
        return message
    }

    private suspend fun generateStatusString(profile: Profile?): String {
        var status = ""
        profile ?: return rh.gs(app.aaps.core.ui.R.string.noprofile)
        if (!loop.runningMode().isLoopRunning()) status += rh.gs(app.aaps.core.ui.R.string.disabled_loop) + "\n"
        return status
    }

    private fun sendError(errorMessage: String) {
        sendToWear(EventData.ConfirmAction(rh.gs(app.aaps.core.ui.R.string.error), errorMessage, returnCommand = EventData.Error(dateUtil.now()))) // ignore return path
    }

    private fun sendToWear(event: EventData) {
        rxBus.send(EventMobileToWear(event))
    }

    /** Stores heart rate events coming from the Wear device. */
    private suspend fun handleHeartRateBatch(events: List<EventData.ActionHeartRate>) {
        aapsLogger.debug(LTag.WEAR, "Heart rate batch received: ${events.size} event(s)")
        val rows = events.map { e ->
            HR(
                duration = e.duration,
                timestamp = e.timestamp,
                beatsPerMinute = e.beatsPerMinute,
                device = e.device
            )
        }
        persistenceLayer.insertOrUpdateHeartRates(rows)
    }

    private suspend fun handleStepsCountBatch(events: List<EventData.ActionStepsRate>) {
        aapsLogger.debug(LTag.WEAR, "Steps count batch received: ${events.size} event(s)")
        val rows = events.map { e ->
            SC(
                duration = e.duration,
                timestamp = e.timestamp,
                steps5min = e.steps5min,
                steps10min = e.steps10min,
                steps15min = e.steps15min,
                steps30min = e.steps30min,
                steps60min = e.steps60min,
                steps180min = e.steps180min,
                device = e.device
            )
        }
        persistenceLayer.insertOrUpdateStepsCounts(rows)
    }

    private fun handleGetCustomWatchface(command: EventData.ActionGetCustomWatchface) {
        val customWatchface = command.customWatchface
        aapsLogger.debug(LTag.WEAR, "Custom Watchface received from ${command.sourceNodeId}")
        val cwfData = customWatchface.customWatchfaceData
        rxBus.send(EventWearUpdateGui(cwfData, command.exportFile))
        val watchfaceName = preferences.get(StringNonKey.WearCwfWatchfaceName)
        val authorVersion = preferences.get(StringNonKey.WearCwfAuthorVersion)
        if (cwfData.metadata[CwfMetadataKey.CWF_NAME] != watchfaceName || cwfData.metadata[CwfMetadataKey.CWF_AUTHOR_VERSION] != authorVersion) {
            preferences.put(StringNonKey.WearCwfWatchfaceName, cwfData.metadata[CwfMetadataKey.CWF_NAME] ?: "")
            preferences.put(StringNonKey.WearCwfAuthorVersion, cwfData.metadata[CwfMetadataKey.CWF_AUTHOR_VERSION] ?: "")
            preferences.put(StringNonKey.WearCwfFileName, cwfData.metadata[CwfMetadataKey.CWF_FILENAME] ?: "")
        }

        if (command.exportFile)
            importExportPrefs.exportCustomWatchface(cwfData, command.withDate)
    }

}

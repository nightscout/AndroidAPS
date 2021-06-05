package info.nightscout.androidaps.plugins.pump.omnipod.dash

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventProfileSwitchChanged
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodConstants
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.CommandConfirmed
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.DashHistory
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.TempBasalRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.OmnipodDashOverviewFragment
import info.nightscout.androidaps.plugins.pump.omnipod.dash.util.mapProfileToBasalProgram
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.queue.commands.CustomCommand
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.TimeChangeType
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.blockingSubscribeBy
import io.reactivex.rxkotlin.subscribeBy
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashPumpPlugin @Inject constructor(
    private val omnipodManager: OmnipodDashManager,
    private val podStateManager: OmnipodDashPodStateManager,
    private val sp: SP,
    private val profileFunction: ProfileFunction,
    private val history: DashHistory,
    private val pumpSync: PumpSync,
    private val rxBus: RxBusWrapper,

    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    commandQueue: CommandQueueProvider
) : PumpPluginBase(pluginDescription, injector, aapsLogger, resourceHelper, commandQueue), Pump {

    companion object {

        private val pluginDescription = PluginDescription()
            .mainType(PluginType.PUMP)
            .fragmentClass(OmnipodDashOverviewFragment::class.java.name)
            .pluginIcon(R.drawable.ic_pod_128)
            .pluginName(R.string.omnipod_dash_name)
            .shortName(R.string.omnipod_dash_name_short)
            .preferencesId(R.xml.omnipod_dash_preferences)
            .description(R.string.omnipod_dash_pump_description)

        private val pumpDescription = PumpDescription(PumpType.OMNIPOD_DASH)
    }

    override fun isInitialized(): Boolean {
        // TODO
        return true
    }

    override fun isSuspended(): Boolean {
        return podStateManager.isSuspended
    }

    override fun isBusy(): Boolean {
        // prevents the queue from executing commands
        return podStateManager.activationProgress.isBefore(ActivationProgress.COMPLETED)
    }

    override fun isConnected(): Boolean {
        return true
    }

    override fun isConnecting(): Boolean {
        // TODO
        return false
    }

    override fun isHandshakeInProgress(): Boolean {
        // TODO
        return false
    }

    override fun finishHandshaking() {
        // TODO
    }

    override fun connect(reason: String) {
        // empty on purpose
    }

    override fun disconnect(reason: String) {
        // TODO
    }

    override fun stopConnecting() {
        // TODO
    }

    override fun getPumpStatus(reason: String) {
        val throwable = Completable.concat(listOf(
            omnipodManager
                .getStatus(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)
                .ignoreElements(),
            history.updateFromState(podStateManager),
            podStateManager.updateActiveCommand()
                .map { handleCommandConfirmation(it) }
                .ignoreElement(),
        )).blockingGet()
        if (throwable != null){
            aapsLogger.error(LTag.PUMP, "Error in getPumpStatus", throwable)
        } else {
            aapsLogger.info(LTag.PUMP, "getPumpStatus executed with success")

        }
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        val basalProgram = mapProfileToBasalProgram(profile)
        return executeSimpleProgrammingCommand(
            pre = suspendDeliveryIfActive(),
            historyEntry = history.createRecord(commandType = OmnipodCommandType.SET_BASAL_PROFILE),
            command = omnipodManager.setBasalProgram(basalProgram).ignoreElements(),
            basalProgram = basalProgram,
            post = failWhenUnconfirmed(),
        ).toPumpEnactResult()
    }

    private fun failWhenUnconfirmed(): Completable = Completable.defer{
        if (podStateManager.activeCommand != null) {
            Completable.error(java.lang.IllegalStateException("Command not confirmed"))
        }else {
            Completable.complete()
        }
    }

    private fun suspendDeliveryIfActive(): Completable = Completable.defer {
        if (podStateManager.deliveryStatus == DeliveryStatus.SUSPENDED)
            Completable.complete()
        else
            executeSimpleProgrammingCommand(
                history.createRecord(OmnipodCommandType.SUSPEND_DELIVERY),
                omnipodManager.suspendDelivery()
                    .filter { podEvent -> podEvent is PodEvent.CommandSent }
                    .map {
                        pumpSyncTempBasal(
                            it,
                            0.0,
                            PodConstants.MAX_POD_LIFETIME.standardMinutes,
                            PumpSync.TemporaryBasalType.PUMP_SUSPEND
                        )
                    }
                    .ignoreElements(),
            )
    }

    private fun observeDeliverySuspended(): Completable = Completable.defer {
        if (podStateManager.deliveryStatus == DeliveryStatus.SUSPENDED)
            Completable.complete()
        else {
            Completable.error(java.lang.IllegalStateException("Expected suspended delivery"))
        }
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        if (!podStateManager.isActivationCompleted) {
            // prevent setBasal requests
            return true
        }
        // TODO: what do we have to answer here if delivery is suspended?
        val running = podStateManager.basalProgram
        val equal = (mapProfileToBasalProgram(profile) == running)
        aapsLogger.info(LTag.PUMP, "isThisProfileSet: $equal")
        return equal
    }

    override fun lastDataTime(): Long {
        return podStateManager.lastUpdatedSystem
    }

    override val baseBasalRate: Double
        get() = podStateManager.basalProgram?.rateAt(Date()) ?: 0.0

    override val reservoirLevel: Double
        get() {
            if (podStateManager.activationProgress.isBefore(ActivationProgress.COMPLETED)) {
                return 0.0
            }

            // Omnipod only reports reservoir level when there's < 1023 pulses left
            return podStateManager.pulsesRemaining?.let {
                it * 0.05
            } ?: 75.0
        }

    override val batteryLevel: Int
        // Omnipod Dash doesn't report it's battery level. We return 0 here and hide related fields in the UI
        get() = 0

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        // TODO update Treatments (?)
        // TODO bolus progress
        // TODO report actual delivered amount after Pod Alarm and bolus cancellation

        return Single.create<PumpEnactResult> { source ->
            val bolusBeeps = sp.getBoolean(R.string.key_omnipod_common_bolus_beeps_enabled, false)

            Observable.concat(
                history.createRecord(
                    commandType = OmnipodCommandType.SET_BOLUS,
                    bolusRecord = BolusRecord(
                        detailedBolusInfo.insulin,
                        BolusType.fromBolusInfoBolusType(detailedBolusInfo.bolusType),
                    ),
                ).flatMapObservable { recordId ->
                    podStateManager.createActiveCommand(recordId).toObservable()
                },
                omnipodManager.bolus(
                    detailedBolusInfo.insulin,
                    bolusBeeps,
                    bolusBeeps
                ),
                history.updateFromState(podStateManager).toObservable(),
                podStateManager.updateActiveCommand().toObservable(),
            ).subscribeBy(
                onNext = { podEvent ->
                    aapsLogger.debug(
                        LTag.PUMP,
                        "Received PodEvent in deliverTreatment: $podEvent"
                    )
                },
                onError = { throwable ->
                    aapsLogger.error(LTag.PUMP, "Error in deliverTreatment", throwable)
                    source.onSuccess(
                        PumpEnactResult(injector).success(false).enacted(false).comment(
                            throwable.toString()
                        )
                    )
                },
                onComplete = {
                    aapsLogger.debug("deliverTreatment completed")
                    source.onSuccess(
                        PumpEnactResult(injector).success(true).enacted(true)
                            .bolusDelivered(detailedBolusInfo.insulin)
                            .carbsDelivered(detailedBolusInfo.carbs)
                    )
                }
            )
        }.blockingGet()
    }

    override fun stopBolusDelivering() {
        // TODO update Treatments (?)
        executeSimpleProgrammingCommand(
            history.createRecord(OmnipodCommandType.CANCEL_BOLUS),
            omnipodManager.stopBolus().ignoreElements()
        ).toPumpEnactResult()
    }

    override fun setTempBasalAbsolute(
        absoluteRate: Double,
        durationInMinutes: Int,
        profile: Profile,
        enforceNew: Boolean,
        tbrType: PumpSync.TemporaryBasalType
    ): PumpEnactResult {
        val tempBasalBeeps = sp.getBoolean(R.string.key_omnipod_common_tbr_beeps_enabled, false)

        return executeSimpleProgrammingCommand(
            historyEntry = history.createRecord(
                commandType = OmnipodCommandType.SET_TEMPORARY_BASAL,
                tempBasalRecord = TempBasalRecord(duration = durationInMinutes, rate = absoluteRate)
            ),
            command = omnipodManager.setTempBasal(
                absoluteRate,
                durationInMinutes.toShort(),
                tempBasalBeeps
            )
                .filter { podEvent -> podEvent is PodEvent.CommandSent }
                .map { pumpSyncTempBasal(it, absoluteRate, durationInMinutes.toLong(), tbrType) }
                .ignoreElements(),
            pre = observeNoActiveTempBasal(enforceNew)
        ).toPumpEnactResult()
    }

    private fun pumpSyncTempBasal(
        podEvent: PodEvent,
        absoluteRate: Double,
        durationInMinutes: Long,
        tbrType: PumpSync.TemporaryBasalType
    ): Boolean {
        val activeCommand = podStateManager.activeCommand
        if (activeCommand == null || podEvent !is PodEvent.CommandSent) {
            throw IllegalArgumentException(
                "No active command or illegal podEvent: " +
                    "activeCommand=$activeCommand" +
                    "podEvent=$podEvent"
            )
        }
        val historyEntry = history.getById(activeCommand.historyId)

        val ret = pumpSync.syncTemporaryBasalWithPumpId(
            timestamp = historyEntry.createdAt,
            rate = absoluteRate,
            duration = T.mins(durationInMinutes.toLong()).msecs(),
            isAbsolute = true,
            type = tbrType,
            pumpId = historyEntry.pumpId(),
            pumpType = PumpType.OMNIPOD_DASH,
            pumpSerial = serialNumber()
        )
        aapsLogger.debug(LTag.PUMP, "Pump sync temp basal: $ret")
        return ret
    }

    private fun observeNoActiveTempBasal(enforceNew: Boolean): Completable {
        return Completable.defer {
            when {
                podStateManager.deliveryStatus !in
                    arrayOf(DeliveryStatus.TEMP_BASAL_ACTIVE, DeliveryStatus.BOLUS_AND_TEMP_BASAL_ACTIVE) -> {
                    // TODO: what happens if we try to cancel inexistent temp basal?
                    aapsLogger.info(LTag.PUMP, "No temporary basal to cancel")
                    Completable.complete()
                }

                !enforceNew ->
                    Completable.error(
                        IllegalStateException(
                            "Temporary basal already active and enforeNew is not set."
                        )
                    )

                else -> {
                    // enforceNew == true
                    aapsLogger.info(LTag.PUMP, "Canceling existing temp basal")
                    executeSimpleProgrammingCommand(
                        history.createRecord(OmnipodCommandType.CANCEL_TEMPORARY_BASAL),
                        omnipodManager.stopTempBasal().ignoreElements()
                    )
                }
            }
        }
    }

    private fun observeActiveTempBasal(): Completable {

        return Completable.defer {
            if (podStateManager.tempBasalActive || pumpSync.expectedPumpState().temporaryBasal != null)
                Completable.complete()
            else
                Completable.error(
                    java.lang.IllegalStateException(
                        "There is no active basal to cancel"
                    )
                )
        }
    }

    override fun setTempBasalPercent(
        percent: Int,
        durationInMinutes: Int,
        profile: Profile,
        enforceNew: Boolean,
        tbrType: PumpSync.TemporaryBasalType
    ): PumpEnactResult {
        // TODO i18n
        return PumpEnactResult(injector).success(false).enacted(false)
            .comment("Omnipod Dash driver does not support percentage temp basals")
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        // TODO i18n
        return PumpEnactResult(injector).success(false).enacted(false)
            .comment("Omnipod Dash driver does not support extended boluses")
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        return executeSimpleProgrammingCommand(
            historyEntry = history.createRecord(OmnipodCommandType.CANCEL_TEMPORARY_BASAL),
            command = omnipodManager.stopTempBasal().ignoreElements(),
            pre = observeActiveTempBasal(),
        ).toPumpEnactResult()
    }

    fun Completable.toPumpEnactResult(): PumpEnactResult {
        return this.toSingleDefault(PumpEnactResult(injector).success(true).enacted(true))
            .onErrorReturnItem(PumpEnactResult(injector).success(false).enacted(false))
            .blockingGet()
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        // TODO i18n
        return PumpEnactResult(injector).success(false).enacted(false)
            .comment("Omnipod Dash driver does not support extended boluses")
    }

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        // TODO
        return JSONObject()
    }

    override val pumpDescription: PumpDescription = Companion.pumpDescription

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.Insulet
    }

    override fun model(): PumpType {
        return pumpDescription.pumpType
    }

    override fun serialNumber(): String {
        return podStateManager.uniqueId?.toString()
            ?: "n/a" // TODO i18n
    }

    override fun shortStatus(veryShort: Boolean): String {
        // TODO
        return "TODO"
    }

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = false

    override fun loadTDDs(): PumpEnactResult {
        // TODO i18n
        return PumpEnactResult(injector).success(false).enacted(false)
            .comment("Omnipod Dash driver does not support TDD")
    }

    override fun canHandleDST(): Boolean {
        return false
    }

    override fun getCustomActions(): List<CustomAction> {
        return emptyList()
    }

    override fun executeCustomAction(customActionType: CustomActionType) {
        aapsLogger.warn(LTag.PUMP, "Unsupported custom action: $customActionType")
    }

    override fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? {
        return when (customCommand) {
            is CommandSilenceAlerts ->
                silenceAlerts()
            is CommandSuspendDelivery ->
                suspendDelivery()
            is CommandResumeDelivery ->
                resumeDelivery()
            is CommandDeactivatePod ->
                deactivatePod()
            is CommandHandleTimeChange ->
                handleTimeChange()
            is CommandUpdateAlertConfiguration ->
                updateAlertConfiguration()
            is CommandPlayTestBeep ->
                playTestBeep()

            else -> {
                aapsLogger.warn(LTag.PUMP, "Unsupported custom command: " + customCommand.javaClass.name)
                PumpEnactResult(injector).success(false).enacted(false).comment(
                    resourceHelper.gs(
                        R.string.omnipod_common_error_unsupported_custom_command,
                        customCommand.javaClass.name
                    )
                )
            }
        }
    }

    private fun silenceAlerts(): PumpEnactResult {
        // TODO filter alert types
        return podStateManager.activeAlerts?.let {
            Single.create<PumpEnactResult> { source ->
                Observable.concat(
                    // TODO: is this a programming command? if yes, save to history
                    omnipodManager.silenceAlerts(it),
                    history.updateFromState(podStateManager).toObservable(),
                    podStateManager.updateActiveCommand().toObservable(),
                ).subscribeBy(
                    onNext = { podEvent ->
                        aapsLogger.debug(
                            LTag.PUMP,
                            "Received PodEvent in silenceAlerts: $podEvent"
                        )
                    },
                    onError = { throwable ->
                        aapsLogger.error(LTag.PUMP, "Error in silenceAlerts", throwable)
                        source.onSuccess(
                            PumpEnactResult(injector).success(false).comment(
                                throwable.toString()
                            )
                        )
                    },
                    onComplete = {
                        aapsLogger.debug("silenceAlerts completed")
                        source.onSuccess(PumpEnactResult(injector).success(true))
                    }
                )
            }.blockingGet()
        } ?: PumpEnactResult(injector).success(false).enacted(false).comment("No active alerts") // TODO i18n
    }

    private fun suspendDelivery(): PumpEnactResult {
        return executeSimpleProgrammingCommand(
            historyEntry = history.createRecord(OmnipodCommandType.SUSPEND_DELIVERY),
            command = omnipodManager.suspendDelivery()
                .filter { podEvent -> podEvent is PodEvent.CommandSent }
                .map {
                    pumpSyncTempBasal(
                        it,
                        0.0,
                        PodConstants.MAX_POD_LIFETIME.standardMinutes,
                        PumpSync.TemporaryBasalType.PUMP_SUSPEND
                    )
                }
                .ignoreElements(),
            pre = observeDeliveryActive(),
        ).toPumpEnactResult()
    }

    private fun observeDeliveryActive(): Completable = Completable.defer {
        if (podStateManager.deliveryStatus != DeliveryStatus.SUSPENDED)
            Completable.complete()
        else
            Completable.error(java.lang.IllegalStateException("Expected active delivery"))
    }

    private fun resumeDelivery(): PumpEnactResult {
        return profileFunction.getProfile()?.let {
            executeSimpleProgrammingCommand(
                history.createRecord(OmnipodCommandType.RESUME_DELIVERY),
                omnipodManager.setBasalProgram(mapProfileToBasalProgram(it)).ignoreElements(),
                pre = observeDeliverySuspended(),
            ).toPumpEnactResult()
        } ?: PumpEnactResult(injector).success(false).enacted(false).comment("No profile active") // TODO i18n
    }

    private fun deactivatePod(): PumpEnactResult {
        return executeSimpleProgrammingCommand(
            history.createRecord(OmnipodCommandType.DEACTIVATE_POD),
            omnipodManager.deactivatePod().ignoreElements()
        ).toPumpEnactResult()
    }

    private fun handleTimeChange(): PumpEnactResult {
        // TODO
        return PumpEnactResult(injector).success(false).enacted(false).comment("NOT IMPLEMENTED")
    }

    private fun updateAlertConfiguration(): PumpEnactResult {
        // TODO
        return PumpEnactResult(injector).success(false).enacted(false).comment("NOT IMPLEMENTED")
    }

    private fun playTestBeep(): PumpEnactResult {
        return executeSimpleProgrammingCommand(
            history.createRecord(OmnipodCommandType.PLAY_TEST_BEEP),
            omnipodManager.playBeep(BeepType.LONG_SINGLE_BEEP).ignoreElements()
        ).toPumpEnactResult()
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        val eventHandlingEnabled = sp.getBoolean(R.string.key_omnipod_common_time_change_event_enabled, false)

        aapsLogger.info(
            LTag.PUMP,
            "Time, Date and/or TimeZone changed. [timeChangeType=" + timeChangeType.name + ", eventHandlingEnabled=" + eventHandlingEnabled + "]"
        )

        if (timeChangeType == TimeChangeType.TimeChanged) {
            aapsLogger.info(LTag.PUMP, "Ignoring time change because it is not a DST or TZ change")
            return
        } else if (!podStateManager.isPodRunning) {
            aapsLogger.info(LTag.PUMP, "Ignoring time change because no Pod is active")
            return
        }

        aapsLogger.info(LTag.PUMP, "Handling time change")

        commandQueue.customCommand(CommandHandleTimeChange(false), null)
    }

    private fun executeSimpleProgrammingCommand(
        historyEntry: Single<String>,
        command: Completable,
        pre: Completable = Completable.complete(),
        basalProgram: BasalProgram? = null,
        post: Completable = Completable.complete(),
    ): Completable {
        return Completable.concat(
            listOf(
                pre,
                podStateManager.observeNoActiveCommand().ignoreElements(),
                historyEntry
                    .flatMap { podStateManager.createActiveCommand(it, basalProgram) }
                    .ignoreElement(),
                command.doOnError {
                    podStateManager.activeCommand?.sendError = it
                    aapsLogger.error(LTag.PUMP, "Error executing command", it)
                }.onErrorComplete(),
                history.updateFromState(podStateManager),
                podStateManager.updateActiveCommand()
                    .map { handleCommandConfirmation(it) }
                    .ignoreElement(),
                post,
            )
        )
    }

    private fun handleCommandConfirmation(confirmation: CommandConfirmed) {
        val command = confirmation.command
        val historyEntry = history.getById(command.historyId)
        aapsLogger.debug(LTag.PUMPCOMM, "handling command confirmation: $confirmation")
        when (historyEntry.commandType) {
            OmnipodCommandType.CANCEL_TEMPORARY_BASAL,
            OmnipodCommandType.RESUME_DELIVERY ->
                // We can't invalidate this command,
                // and this is why it is pumpSync-ed at this point
                if (confirmation.success) {
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        historyEntry.createdAt,
                        historyEntry.pumpId(),
                        PumpType.OMNIPOD_DASH,
                        serialNumber()
                    )
                }

            OmnipodCommandType.SET_BASAL_PROFILE -> {
                if (confirmation.success) {
                    podStateManager.basalProgram = command.basalProgram
                    if (podStateManager.basalProgram == null) {
                        aapsLogger.warn(LTag.PUMP, "Saving null basal profile")
                    }
                    if (!commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
                        // we are late-confirming this command. before that, we answered with success:false
                        rxBus.send(EventProfileSwitchChanged())
                    }
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        historyEntry.createdAt,
                        historyEntry.pumpId(),
                        PumpType.OMNIPOD_DASH,
                        serialNumber()
                    )
                }
            }

            OmnipodCommandType.SET_TEMPORARY_BASAL -> {
                // This treatment was synced before sending the command
                aapsLogger.info(LTag.PUMPCOMM, "temporary basal denied. PumpId: ${historyEntry.pumpId()}")
                if (!confirmation.success) {
                    pumpSync.invalidateTemporaryBasal(historyEntry.pumpId())
                }
            }

            OmnipodCommandType.SUSPEND_DELIVERY -> {
                if (!confirmation.success) {
                    pumpSync.invalidateTemporaryBasal(historyEntry.pumpId())
                }
            }

            else ->
                aapsLogger.warn(
                    LTag.PUMP,
                    "Will not sync confirmed command of type: $historyEntry and " +
                        "succes: ${confirmation.success}"
                )
        }
    }
}

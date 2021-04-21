package info.nightscout.androidaps.plugins.pump.omnipod.dash

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.DashHistory
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.BolusType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.TempBasalRecord
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.OmnipodDashOverviewFragment
import info.nightscout.androidaps.plugins.pump.omnipod.dash.util.mapProfileToBasalProgram
import info.nightscout.androidaps.queue.commands.CustomCommand
import info.nightscout.androidaps.utils.TimeChangeType
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.blockingSubscribeBy
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.toCompletable
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
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    commandQueue: CommandQueueProvider
) : PumpPluginBase(pluginDescription, injector, aapsLogger, resourceHelper, commandQueue), PumpInterface {

    companion object {

        private val pluginDescription = PluginDescription()
            .mainType(PluginType.PUMP)
            .fragmentClass(OmnipodDashOverviewFragment::class.java.name)
            .pluginIcon(R.drawable.ic_pod_128)
            .pluginName(R.string.omnipod_dash_name)
            .shortName(R.string.omnipod_dash_name_short)
            .preferencesId(R.xml.omnipod_dash_preferences)
            .description(R.string.omnipod_dash_pump_description)

        private val pumpDescription = PumpDescription(PumpType.Omnipod_Dash)
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
        // TODO
        return podStateManager.activeCommand == null
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
        if (podStateManager.activeCommand == null) {
            return
        }
        getPumpStatus("unconfirmed command")
    }

    override fun disconnect(reason: String) {
        // TODO
    }

    override fun stopConnecting() {
        // TODO
    }

    override fun getPumpStatus(reason: String) {
        Observable.concat(
            omnipodManager.getStatus(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE),
            history.updateFromState(podStateManager).toObservable(),
            podStateManager.updateActiveCommand().toObservable(),
        ).blockingSubscribeBy(
            onNext = { podEvent ->
                aapsLogger.debug(
                    LTag.PUMP,
                    "Received PodEvent in getPumpStatus: $podEvent"
                )
            },
            onError = { throwable ->
                aapsLogger.error(LTag.PUMP, "Error in getPumpStatus", throwable)
            },
            onComplete = {
                aapsLogger.debug("getPumpStatus completed")
            }
        )
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        return executeProgrammingCommand(
            history.createRecord(
                commandType = OmnipodCommandType.SET_BASAL_PROFILE
            ),
            omnipodManager.setBasalProgram(mapProfileToBasalProgram(profile))
        )
    }

    override fun isThisProfileSet(profile: Profile): Boolean = podStateManager.basalProgram?.let {
        it == mapProfileToBasalProgram(profile)
    } ?: true

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
                        if (detailedBolusInfo.isSMB) BolusType.SMB else BolusType.DEFAULT
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
                        PumpEnactResult(injector).success(false).enacted(false).comment(throwable.message)
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
        executeProgrammingCommand(
            history.createRecord(OmnipodCommandType.CANCEL_BOLUS),
            omnipodManager.stopBolus(),
        )
    }

    override fun setTempBasalAbsolute(
        absoluteRate: Double,
        durationInMinutes: Int,
        profile: Profile,
        enforceNew: Boolean
    ): PumpEnactResult {
        // TODO update Treatments
        // TODO check for existing basal
        return executeProgrammingCommand(
            history.createRecord(
                commandType = OmnipodCommandType.SET_TEMPORARY_BASAL,
                tempBasalRecord = TempBasalRecord(duration = durationInMinutes, rate = absoluteRate)
            ),
            omnipodManager.setTempBasal(
                absoluteRate,
                durationInMinutes.toShort()
            )
        )
    }

    override fun setTempBasalPercent(
        percent: Int,
        durationInMinutes: Int,
        profile: Profile,
        enforceNew: Boolean
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
        // TODO update Treatments
        return executeProgrammingCommand(
            history.createRecord(OmnipodCommandType.CANCEL_TEMPORARY_BASAL),
            omnipodManager.stopTempBasal()
        )
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
        return pumpDescription.pumpType.manufacturer
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
                        source.onSuccess(PumpEnactResult(injector).success(false).comment(throwable.message))
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
        return executeProgrammingCommand(
            history.createRecord(OmnipodCommandType.RESUME_DELIVERY),
            omnipodManager.suspendDelivery()
        )
    }

    private fun resumeDelivery(): PumpEnactResult {
        return profileFunction.getProfile()?.let {
            executeProgrammingCommand(
                history.createRecord(OmnipodCommandType.RESUME_DELIVERY),
                omnipodManager.setBasalProgram(mapProfileToBasalProgram(it))
            )
        } ?: PumpEnactResult(injector).success(false).enacted(false).comment("No profile active") // TODO i18n
    }

    private fun deactivatePod(): PumpEnactResult {
        return executeProgrammingCommand(
            history.createRecord(OmnipodCommandType.DEACTIVATE_POD),
            omnipodManager.deactivatePod()
        )
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
        return executeProgrammingCommand(
            history.createRecord(OmnipodCommandType.PLAY_TEST_BEEP),
            omnipodManager.playBeep(BeepType.LONG_SINGLE_BEEP)
        )
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

    private fun executeProgrammingCommand(
        historyEntry: Single<String>,
        command: Observable<PodEvent>
    ): PumpEnactResult {
        return Single.create<PumpEnactResult> { source ->
            Observable.concat(
                historyEntry.flatMapObservable { recordId ->
                    podStateManager.createActiveCommand(recordId).toObservable()
                },
                command,
                history.updateFromState(podStateManager).toObservable(),
                podStateManager.updateActiveCommand().toObservable(),
            ).subscribeBy(
                onNext = { podEvent ->
                    aapsLogger.debug(
                        LTag.PUMP,
                        "Received PodEvent: $podEvent"
                    )
                },
                onError = { throwable ->
                    aapsLogger.error(LTag.PUMP, "Error executing command", throwable)
                    // Here we assume that onError will be called only BEFORE we manage to send a command
                    // If it gets called later, we will have the command as "not sent" in history and will not try to
                    // get it's final status, even if it was send
                    podStateManager.resetActiveCommand()
                    source.onSuccess(
                        PumpEnactResult(injector).success(false).enacted(false).comment(throwable.message)
                    )
                },
                onComplete = {
                    aapsLogger.debug("Command completed")
                    source.onSuccess(
                        PumpEnactResult(injector).success(true).enacted(true)
                    )
                }
            )
        }.blockingGet()
    }
}

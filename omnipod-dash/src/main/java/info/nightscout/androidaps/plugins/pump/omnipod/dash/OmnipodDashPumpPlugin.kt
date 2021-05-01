package info.nightscout.androidaps.plugins.pump.omnipod.dash

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.OmnipodDashOverviewFragment
import info.nightscout.androidaps.plugins.pump.omnipod.dash.util.mapProfileToBasalProgram
import info.nightscout.androidaps.queue.commands.CustomCommand
import info.nightscout.androidaps.utils.TimeChangeType
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
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
        // TODO
    }

    override fun disconnect(reason: String) {
        // TODO
    }

    override fun stopConnecting() {
        // TODO
    }

    override fun getPumpStatus(reason: String) {
        // TODO history
        omnipodManager.getStatus(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE).blockingSubscribeBy(
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
        // TODO history

        return Single.create<PumpEnactResult> { source ->
            omnipodManager.setBasalProgram(mapProfileToBasalProgram(profile)).subscribeBy(
                onNext = { podEvent ->
                    aapsLogger.debug(
                        LTag.PUMP,
                        "Received PodEvent in setNewBasalProfile: $podEvent"
                    )
                },
                onError = { throwable ->
                    aapsLogger.error(LTag.PUMP, "Error in setNewBasalProfile", throwable)
                    source.onSuccess(PumpEnactResult(injector).success(false).enacted(false).comment(throwable.message))
                },
                onComplete = {
                    aapsLogger.debug("setNewBasalProfile completed")
                    source.onSuccess(PumpEnactResult(injector).success(true).enacted(true))
                }
            )
        }.blockingGet()
    }

    override fun isThisProfileSet(profile: Profile): Boolean = podStateManager.basalProgram?.let {
        it == mapProfileToBasalProgram(profile)
    } ?: true


    override fun lastDataTime(): Long {
        return podStateManager.lastConnection
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
        // TODO history
        // TODO update Treatments (?)
        // TODO bolus progress
        // TODO report actual delivered amount after Pod Alarm and bolus cancellation

        return Single.create<PumpEnactResult> { source ->
            val bolusBeeps = sp.getBoolean(R.string.key_omnipod_common_bolus_beeps_enabled, false)

            omnipodManager.bolus(
                detailedBolusInfo.insulin,
                bolusBeeps,
                bolusBeeps
            ).subscribeBy(
                onNext = { podEvent ->
                    aapsLogger.debug(
                        LTag.PUMP,
                        "Received PodEvent in deliverTreatment: $podEvent"
                    )
                },
                onError = { throwable ->
                    aapsLogger.error(LTag.PUMP, "Error in deliverTreatment", throwable)
                    source.onSuccess(PumpEnactResult(injector).success(false).enacted(false).comment(throwable.message))
                },
                onComplete = {
                    aapsLogger.debug("deliverTreatment completed")
                    source.onSuccess(
                        PumpEnactResult(injector).success(true).enacted(true).bolusDelivered(detailedBolusInfo.insulin)
                            .carbsDelivered(detailedBolusInfo.carbs)
                    )
                }
            )
        }.blockingGet()
    }

    override fun stopBolusDelivering() {
        // TODO history
        // TODO update Treatments (?)

        omnipodManager.stopBolus().blockingSubscribeBy(
            onNext = { podEvent ->
                aapsLogger.debug(
                    LTag.PUMP,
                    "Received PodEvent in stopBolusDelivering: $podEvent"
                )
            },
            onError = { throwable ->
                aapsLogger.error(LTag.PUMP, "Error in stopBolusDelivering", throwable)
            },
            onComplete = {
                aapsLogger.debug("stopBolusDelivering completed")
            }
        )
    }

    override fun setTempBasalAbsolute(
        absoluteRate: Double,
        durationInMinutes: Int,
        profile: Profile,
        enforceNew: Boolean,
        tbrType: PumpSync.TemporaryBasalType
    ): PumpEnactResult {
        // TODO history
        // TODO update Treatments

        return Single.create<PumpEnactResult> { source ->
            omnipodManager.setTempBasal(
                absoluteRate,
                durationInMinutes.toShort()
            ).subscribeBy(
                onNext = { podEvent ->
                    aapsLogger.debug(
                        LTag.PUMP,
                        "Received PodEvent in setTempBasalAbsolute: $podEvent"
                    )
                },
                onError = { throwable ->
                    aapsLogger.error(LTag.PUMP, "Error in setTempBasalAbsolute", throwable)
                    source.onSuccess(PumpEnactResult(injector).success(false).enacted(false).comment(throwable.message))
                },
                onComplete = {
                    aapsLogger.debug("setTempBasalAbsolute completed")
                    source.onSuccess(
                        PumpEnactResult(injector).success(true).enacted(true).absolute(absoluteRate)
                            .duration(durationInMinutes)
                    )
                }
            )
        }.blockingGet()
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
        // TODO history
        // TODO update Treatments

        return Single.create<PumpEnactResult> { source ->
            omnipodManager.stopTempBasal().subscribeBy(
                onNext = { podEvent ->
                    aapsLogger.debug(
                        LTag.PUMP,
                        "Received PodEvent in cancelTempBasal: $podEvent"
                    )
                },
                onError = { throwable ->
                    aapsLogger.error(LTag.PUMP, "Error in cancelTempBasal", throwable)
                    source.onSuccess(PumpEnactResult(injector).success(false).enacted(false).comment(throwable.message))
                },
                onComplete = {
                    aapsLogger.debug("cancelTempBasal completed")
                    source.onSuccess(
                        PumpEnactResult(injector).success(true).enacted(true)
                    )
                }
            )
        }.blockingGet()
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
        return if (podStateManager.uniqueId == null) {
            "n/a" // TODO i18n
        } else {
            podStateManager.uniqueId.toString()
        }
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
        // TODO history
        // TODO filter alert types

        return podStateManager.activeAlerts?.let {
            Single.create<PumpEnactResult> { source ->
                omnipodManager.silenceAlerts(it).subscribeBy(
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
        // TODO history

        return Single.create<PumpEnactResult> { source ->
            omnipodManager.suspendDelivery().subscribeBy(
                onNext = { podEvent ->
                    aapsLogger.debug(
                        LTag.PUMP,
                        "Received PodEvent in suspendDelivery: $podEvent"
                    )
                },
                onError = { throwable ->
                    aapsLogger.error(LTag.PUMP, "Error in suspendDelivery", throwable)
                    source.onSuccess(PumpEnactResult(injector).success(false).comment(throwable.message))
                },
                onComplete = {
                    aapsLogger.debug("suspendDelivery completed")
                    source.onSuccess(PumpEnactResult(injector).success(true))
                }
            )
        }.blockingGet()
    }

    private fun resumeDelivery(): PumpEnactResult {
        // TODO history

        return profileFunction.getProfile()?.let {

            Single.create<PumpEnactResult> { source ->
                omnipodManager.setBasalProgram(mapProfileToBasalProgram(it)).subscribeBy(
                    onNext = { podEvent ->
                        aapsLogger.debug(
                            LTag.PUMP,
                            "Received PodEvent in resumeDelivery: $podEvent"
                        )
                    },
                    onError = { throwable ->
                        aapsLogger.error(LTag.PUMP, "Error in resumeDelivery", throwable)
                        source.onSuccess(PumpEnactResult(injector).success(false).comment(throwable.message))
                    },
                    onComplete = {
                        aapsLogger.debug("resumeDelivery completed")
                        source.onSuccess(PumpEnactResult(injector).success(true))
                    }
                )
            }.blockingGet()
        } ?: PumpEnactResult(injector).success(false).enacted(false).comment("No profile active") // TODO i18n
    }

    private fun deactivatePod(): PumpEnactResult {
        // TODO history

        return Single.create<PumpEnactResult> { source ->
            omnipodManager.deactivatePod().subscribeBy(
                onNext = { podEvent ->
                    aapsLogger.debug(
                        LTag.PUMP,
                        "Received PodEvent in deactivatePod: $podEvent"
                    )
                },
                onError = { throwable ->
                    aapsLogger.error(LTag.PUMP, "Error in deactivatePod", throwable)
                    source.onSuccess(PumpEnactResult(injector).success(false).comment(throwable.message))
                },
                onComplete = {
                    aapsLogger.debug("deactivatePod completed")
                    source.onSuccess(PumpEnactResult(injector).success(true))
                }
            )
        }.blockingGet()
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
        // TODO history

        return Single.create<PumpEnactResult> { source ->
            omnipodManager.playBeep(BeepType.LONG_SINGLE_BEEP).subscribeBy(
                onNext = { podEvent ->
                    aapsLogger.debug(
                        LTag.PUMP,
                        "Received PodEvent in playTestBeep: $podEvent"
                    )
                },
                onError = { throwable ->
                    aapsLogger.error(LTag.PUMP, "Error in playTestBeep", throwable)
                    source.onSuccess(PumpEnactResult(injector).success(false).enacted(false).comment(throwable.message))
                },
                onComplete = {
                    aapsLogger.debug("playTestBeep completed")
                    source.onSuccess(
                        PumpEnactResult(injector).success(true).enacted(true)
                    )
                }
            )
        }.blockingGet()
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
}

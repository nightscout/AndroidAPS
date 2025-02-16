package app.aaps.pump.omnipod.dash.driver

import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.Round
import app.aaps.pump.omnipod.dash.driver.comm.OmnipodDashBleManager
import app.aaps.pump.omnipod.dash.driver.event.PodEvent
import app.aaps.pump.omnipod.dash.driver.pod.command.DeactivateCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.GetStatusCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.GetVersionCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.GetVersionCommand.Companion.DEFAULT_UNIQUE_ID
import app.aaps.pump.omnipod.dash.driver.pod.command.ProgramAlertsCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.ProgramBasalCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.ProgramBeepsCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.ProgramBolusCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.ProgramTempBasalCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.SetUniqueIdCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.SilenceAlertsCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.StopDeliveryCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.SuspendDeliveryCommand
import app.aaps.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertConfiguration
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertTrigger
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertType
import app.aaps.pump.omnipod.dash.driver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.dash.driver.pod.definition.BeepRepetitionType
import app.aaps.pump.omnipod.dash.driver.pod.definition.BeepType
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodConstants.Companion.MAX_POD_LIFETIME
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodConstants.Companion.POD_EXPIRATION_ALERT_HOURS_REMAINING_DEFAULT
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodConstants.Companion.POD_EXPIRATION_IMMINENT_ALERT_HOURS_REMAINING
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodConstants.Companion.POD_PULSE_BOLUS_UNITS
import app.aaps.pump.omnipod.dash.driver.pod.definition.PodStatus
import app.aaps.pump.omnipod.dash.driver.pod.definition.ProgramReminder
import app.aaps.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.dash.driver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.dash.driver.pod.response.Response
import app.aaps.pump.omnipod.dash.driver.pod.response.ResponseType
import app.aaps.pump.omnipod.dash.driver.pod.response.SetUniqueIdResponse
import app.aaps.pump.omnipod.dash.driver.pod.response.VersionResponse
import app.aaps.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Consumer
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Date
import java.util.EnumSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashManagerImpl @Inject constructor(
    private val logger: AAPSLogger,
    private val podStateManager: OmnipodDashPodStateManager,
    private val bleManager: OmnipodDashBleManager,
    private val aapsSchedulers: AapsSchedulers
) : OmnipodDashManager {

    companion object {

        const val NONCE = 1229869870 // The Omnipod Dash seems to use a fixed nonce
    }

    private val observePodReadyForActivationPart1: Observable<PodEvent>
        get() = Observable.defer {
            if (podStateManager.activationProgress.isBefore(ActivationProgress.PHASE_1_COMPLETED)) {
                Observable.empty()
            } else {
                // TODO introduce specialized Exception
                Observable.error(IllegalStateException("Pod is in an incorrect state"))
            }
        }

    private val observePodReadyForActivationPart2: Observable<PodEvent>
        get() = Observable.defer {
            if (podStateManager.activationProgress.isAtLeast(ActivationProgress.PHASE_1_COMPLETED) &&
                podStateManager.activationProgress.isBefore(ActivationProgress.COMPLETED)
            ) {
                Observable.empty()
            } else {
                // TODO introduce specialized Exception
                Observable.error(IllegalStateException("Pod is in an incorrect state"))
            }
        }

    private val observeUniqueIdSet: Observable<PodEvent>
        get() = Observable.defer {
            if (podStateManager.activationProgress.isAtLeast(ActivationProgress.SET_UNIQUE_ID)) {
                Observable.empty()
            } else {
                // TODO introduce specialized Exception
                Observable.error(IllegalStateException("Pod is in an incorrect state"))
            }
        }

    private val observePodRunning: Observable<PodEvent>
        get() = Observable.defer {
            if (podStateManager.activationProgress == ActivationProgress.COMPLETED && podStateManager.podStatus!!.isRunning()) {
                Observable.empty()
            } else {
                // TODO introduce specialized Exception
                Observable.error(IllegalStateException("Pod is in an incorrect state"))
            }
        }

    override fun disconnect(closeGatt: Boolean) {
        bleManager.disconnect(closeGatt)
    }

    override fun connect(stop: CountDownLatch): Observable<PodEvent> {
        return observeConnectToPodWithStop(stop)
            .interceptPodEvents()
    }

    private fun observeConnectToPodWithStop(stop: CountDownLatch): Observable<PodEvent> {
        return Observable.defer {
            bleManager.connect(stop)
                .doOnError { throwable -> logger.warn(LTag.PUMPBTCOMM, "observeConnectToPodWithStop error=$throwable") }
        }
    }

    private val observeConnectToPod: Observable<PodEvent>
        get() = Observable.defer {
            bleManager.connect()
                .doOnError { throwable -> logger.warn(LTag.PUMPBTCOMM, "observeConnectToPod error=$throwable") }
        }

    private val observePairNewPod: Observable<PodEvent>
        get() = Observable.defer {
            bleManager.pairNewPod()
        }

    private fun observeSendProgramBolusCommand(
        units: Double,
        rateInEighthPulsesPerSeconds: Byte,
        confirmationBeeps: Boolean,
        completionBeeps: Boolean
    ): Observable<PodEvent> {
        return Observable.defer {
            bleManager.sendCommand(
                ProgramBolusCommand.Builder()
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setNonce(NONCE)
                    .setNumberOfUnits(units)
                    .setDelayBetweenPulsesInEighthSeconds(rateInEighthPulsesPerSeconds)
                    .setProgramReminder(ProgramReminder(confirmationBeeps, completionBeeps, 0))
                    .build(),
                DefaultStatusResponse::class
            )
        }
    }

    private fun observeSendGetPodStatusCommand(type: ResponseType.StatusResponseType = ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE): Observable<PodEvent> {
        // TODO move somewhere else
        val expectedResponseType = when (type) {
            ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE -> DefaultStatusResponse::class
            ResponseType.StatusResponseType.ALARM_STATUS            -> AlarmStatusResponse::class

            else                                                    -> return Observable.error(UnsupportedOperationException("No response type to class mapping for ${type.name}"))
        }

        return Observable.defer {
            bleManager.sendCommand(
                GetStatusCommand.Builder()
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setStatusResponseType(type)
                    .build(),
                expectedResponseType
            )
        }
    }

    private val observeVerifyCannulaInsertion: Observable<PodEvent>
        get() = Observable.concat(
            observeSendGetPodStatusCommand(),
            Observable.defer {
                if (podStateManager.podStatus == PodStatus.RUNNING_ABOVE_MIN_VOLUME) {
                    Observable.empty()
                } else {
                    Observable.error(IllegalStateException("Unexpected Pod status"))
                }
            }
        )

    private fun observeSendProgramAlertsCommand(
        alertConfigurations: List<AlertConfiguration>,
        multiCommandFlag: Boolean = false
    ): Observable<PodEvent> {
        return Observable.defer {
            bleManager.sendCommand(
                ProgramAlertsCommand.Builder()
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setNonce(NONCE)
                    .setAlertConfigurations(alertConfigurations)
                    .setMultiCommandFlag(multiCommandFlag)
                    .build(),
                DefaultStatusResponse::class
            )
        }
    }

    private fun observeSendProgramBasalCommand(basalProgram: BasalProgram, hasBasalBeepEnabled: Boolean): Observable<PodEvent> {
        return Observable.defer {
            val currentTime = Date()
            logger.debug(LTag.PUMPCOMM, "Programming basal. currentTime={}, basalProgram={}", currentTime, basalProgram)
            bleManager.sendCommand(
                ProgramBasalCommand.Builder()
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setNonce(NONCE)
                    .setProgramReminder(ProgramReminder(atStart = hasBasalBeepEnabled, atEnd = false, atInterval = 0))
                    .setBasalProgram(basalProgram)
                    .setCurrentTime(currentTime)
                    .build(),
                DefaultStatusResponse::class
            )
        }.doOnComplete {
            podStateManager.updateTimeZone()
        }
    }

    private val observeVerifyPrime: Observable<PodEvent>
        get() = Observable.concat(
            observeSendGetPodStatusCommand(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE),
            Observable.defer {
                if (podStateManager.podStatus == PodStatus.CLUTCH_DRIVE_ENGAGED) {
                    Observable.empty()
                } else {
                    Observable.error(IllegalStateException("Unexpected Pod status: got ${podStateManager.podStatus}, expected CLUTCH_DRIVE_ENGAGED"))
                }
            }
        )

    private val observeSendSetUniqueIdCommand: Observable<PodEvent>
        get() = Observable.defer {
            bleManager.sendCommand(
                SetUniqueIdCommand.Builder()
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setLotNumber(podStateManager.lotNumber!!.toInt())
                    .setPodSequenceNumber(podStateManager.podSequenceNumber!!.toInt())
                    .setInitializationTime(Date())
                    .build(),
                SetUniqueIdResponse::class
            )
        }

    private val observeSendGetVersionCommand: Observable<PodEvent>
        get() = Observable.defer {
            bleManager.sendCommand(
                GetVersionCommand.Builder()
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setUniqueId(DEFAULT_UNIQUE_ID)
                    .build(),
                VersionResponse::class
            )
        }

    override fun activatePodPart1(lowReservoirAlertTrigger: AlertTrigger.ReservoirVolumeTrigger?): Observable<PodEvent> {
        return Observable.concat(
            observePodReadyForActivationPart1,
            observePairNewPod,
            observeConnectToPod,
            observeActivationPart1Commands(lowReservoirAlertTrigger)
        ).doOnComplete(ActivationProgressUpdater(ActivationProgress.PHASE_1_COMPLETED))
            .interceptPodEvents()
    }

    private fun observeActivationPart1Commands(lowReservoirAlertTrigger: AlertTrigger.ReservoirVolumeTrigger?): Observable<PodEvent> {
        val observables = createActivationPart1Observables(lowReservoirAlertTrigger)

        return if (observables.isEmpty()) {
            Observable.empty()
        } else {
            Observable.concat(observables)
        }
    }

    private fun createActivationPart1Observables(lowReservoirAlertTrigger: AlertTrigger.ReservoirVolumeTrigger?): List<Observable<PodEvent>> {
        val observables = ArrayList<Observable<PodEvent>>()

        if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIME_COMPLETED)) {
            observables.add(
                observeVerifyPrime.doOnComplete(ActivationProgressUpdater(ActivationProgress.PRIME_COMPLETED))
            )
        }

        if (podStateManager.activationProgress.isBefore(ActivationProgress.PRIMING)) {
            observables.add(observeConnectToPod) // connection can time out while waiting
            observables.add(
                Observable.defer {
                    Observable.timer(podStateManager.firstPrimeBolusVolume!!.toLong(), TimeUnit.SECONDS)
                        .flatMap { Observable.empty() }
                }
            )
            observables.add(
                Observable.defer {
                    bleManager.sendCommand(
                        ProgramBolusCommand.Builder()
                            .setUniqueId(podStateManager.uniqueId!!.toInt())
                            .setSequenceNumber(podStateManager.messageSequenceNumber)
                            .setNonce(NONCE)
                            .setNumberOfUnits(Round.roundTo(podStateManager.firstPrimeBolusVolume!! * POD_PULSE_BOLUS_UNITS, POD_PULSE_BOLUS_UNITS))
                            .setDelayBetweenPulsesInEighthSeconds(podStateManager.primePulseRate!!.toByte())
                            .setProgramReminder(ProgramReminder(atStart = false, atEnd = false, atInterval = 0))
                            .build(),
                        DefaultStatusResponse::class
                    )
                }.doOnComplete(ActivationProgressUpdater(ActivationProgress.PRIMING))
            )
        }

        if (podStateManager.activationProgress.isBefore(ActivationProgress.REPROGRAMMED_LUMP_OF_COAL_ALERT)) {
            observables.add(
                observeSendProgramAlertsCommand(
                    listOf(
                        AlertConfiguration(
                            AlertType.EXPIRATION,
                            enabled = true,
                            durationInMinutes = 55,
                            autoOff = false,
                            AlertTrigger.TimerTrigger(5),
                            BeepType.FOUR_TIMES_BIP_BEEP,
                            BeepRepetitionType.XXX5
                        )
                    )
                ).doOnComplete(ActivationProgressUpdater(ActivationProgress.REPROGRAMMED_LUMP_OF_COAL_ALERT))
            )
        }
        if (lowReservoirAlertTrigger != null && podStateManager.activationProgress.isBefore(ActivationProgress.PROGRAMMED_LOW_RESERVOIR_ALERTS)) {
            observables.add(
                observeSendProgramAlertsCommand(
                    listOf(
                        AlertConfiguration(
                            AlertType.LOW_RESERVOIR,
                            enabled = true,
                            durationInMinutes = 0,
                            autoOff = false,
                            lowReservoirAlertTrigger,
                            BeepType.FOUR_TIMES_BIP_BEEP,
                            BeepRepetitionType.XXX
                        )
                    )
                ).doOnComplete(ActivationProgressUpdater(ActivationProgress.PROGRAMMED_LOW_RESERVOIR_ALERTS))
            )
        }

        if (podStateManager.activationProgress.isBefore(ActivationProgress.SET_UNIQUE_ID)) {
            observables.add(
                observeSendSetUniqueIdCommand.doOnComplete(ActivationProgressUpdater(ActivationProgress.SET_UNIQUE_ID))
            )
        }
        if (podStateManager.activationProgress.isBefore(ActivationProgress.GOT_POD_VERSION)) {
            observables.add(
                observeSendGetVersionCommand.doOnComplete(ActivationProgressUpdater(ActivationProgress.GOT_POD_VERSION))
            )
        }

        return observables.reversed()
    }

    override fun activatePodPart2(basalProgram: BasalProgram, userConfiguredExpirationReminderHours: Long?, userConfiguredExpirationAlarmHours: Long?):
        Observable<PodEvent> {
        return Observable.concat(
            observePodReadyForActivationPart2,
            observeConnectToPod,
            observeActivationPart2Commands(basalProgram, userConfiguredExpirationReminderHours, userConfiguredExpirationAlarmHours)
        ).doOnComplete(ActivationProgressUpdater(ActivationProgress.COMPLETED))
            .interceptPodEvents()
    }

    private fun observeActivationPart2Commands(basalProgram: BasalProgram, userConfiguredExpirationReminderHours: Long?, userConfiguredExpirationAlarmHours: Long?):
        Observable<PodEvent> {
        val observables = createActivationPart2Observables(basalProgram, userConfiguredExpirationReminderHours, userConfiguredExpirationAlarmHours)

        return if (observables.isEmpty()) {
            Observable.empty()
        } else {
            Observable.concat(observables)
        }
    }

    private fun createActivationPart2Observables(
        basalProgram: BasalProgram,
        userConfiguredExpirationReminderHours: Long?,
        userConfiguredExpirationAlarmHours: Long?
    ):
        List<Observable<PodEvent>> {
        val observables = ArrayList<Observable<PodEvent>>()

        if (podStateManager.activationProgress.isBefore(ActivationProgress.CANNULA_INSERTED)) {
            observables.add(
                observeVerifyCannulaInsertion
                    .doOnComplete(ActivationProgressUpdater(ActivationProgress.CANNULA_INSERTED))
            )
        }
        if (podStateManager.activationProgress.isBefore(ActivationProgress.INSERTING_CANNULA)) {
            observables.add(observeConnectToPod) // connection can time out while waiting
            observables.add(
                Observable.defer {
                    Observable.timer(podStateManager.secondPrimeBolusVolume!!.toLong(), TimeUnit.SECONDS)
                        .flatMap { Observable.empty() }
                }
            )
            observables.add(
                observeSendProgramBolusCommand(
                    Round.roundTo(podStateManager.secondPrimeBolusVolume!! * POD_PULSE_BOLUS_UNITS, POD_PULSE_BOLUS_UNITS),
                    podStateManager.primePulseRate!!.toByte(),
                    confirmationBeeps = false,
                    completionBeeps = false
                ).doOnComplete(ActivationProgressUpdater(ActivationProgress.INSERTING_CANNULA))
            )
        }
        if (podStateManager.activationProgress.isBefore(ActivationProgress.UPDATED_EXPIRATION_ALERTS)) {
            val podLifeLeft = Duration.between(ZonedDateTime.now(), podStateManager.expiry)

            val expirationAlarmEnabled = userConfiguredExpirationAlarmHours != null && userConfiguredExpirationAlarmHours > 0
            val expirationAlarmDelay = podLifeLeft.minus(
                Duration.ofHours(userConfiguredExpirationAlarmHours ?: POD_EXPIRATION_ALERT_HOURS_REMAINING_DEFAULT)
            ).plus(Duration.ofHours(8)) // Add 8 hours for grace period

            val expirationImminentDelay = podLifeLeft.minus(
                Duration.ofHours(POD_EXPIRATION_IMMINENT_ALERT_HOURS_REMAINING)
            ).plus(Duration.ofHours(8)) // Add 8 hours for grace period

            val alerts = mutableListOf(
                AlertConfiguration(
                    AlertType.EXPIRATION,
                    enabled = expirationAlarmEnabled,
                    durationInMinutes = (TimeUnit.HOURS.toMinutes(
                        userConfiguredExpirationAlarmHours ?: POD_EXPIRATION_ALERT_HOURS_REMAINING_DEFAULT
                    ) - 60).toShort(),
                    autoOff = false,
                    AlertTrigger.TimerTrigger(
                        expirationAlarmDelay.toMinutes().toShort()
                    ),
                    BeepType.FOUR_TIMES_BIP_BEEP,
                    BeepRepetitionType.XXX3
                ),
                AlertConfiguration(
                    AlertType.EXPIRATION_IMMINENT,
                    enabled = expirationAlarmEnabled,
                    durationInMinutes = 0,
                    autoOff = false,
                    AlertTrigger.TimerTrigger(
                        expirationImminentDelay.toMinutes().toShort()
                    ),
                    BeepType.FOUR_TIMES_BIP_BEEP,
                    BeepRepetitionType.XXX4
                )
            )
            val userExpiryReminderEnabled = userConfiguredExpirationReminderHours != null && userConfiguredExpirationReminderHours > 0
            val userExpiryReminderDelay = podLifeLeft.minus(
                Duration.ofHours(userConfiguredExpirationReminderHours ?: (MAX_POD_LIFETIME.toHours() + 1))
            )
            if (userExpiryReminderDelay.isNegative) {
                logger.warn(
                    LTag.PUMPBTCOMM,
                    "createActivationPart2Observables negative " +
                        "expiryAlertDuration=$userExpiryReminderDelay"
                )
            } else {
                alerts.add(
                    AlertConfiguration(
                        AlertType.USER_SET_EXPIRATION,
                        enabled = userExpiryReminderEnabled,
                        durationInMinutes = 0,
                        autoOff = false,
                        AlertTrigger.TimerTrigger(
                            userExpiryReminderDelay.toMinutes().toShort()
                        ),
                        BeepType.FOUR_TIMES_BIP_BEEP,
                        BeepRepetitionType.EVERY_MINUTE_AND_EVERY_15_MIN
                    )
                )
            }

            observables.add(
                observeSendProgramAlertsCommand(
                    alerts,
                    multiCommandFlag = true
                ).doOnComplete(ActivationProgressUpdater(ActivationProgress.UPDATED_EXPIRATION_ALERTS))
            )
        }
        if (podStateManager.activationProgress.isBefore(ActivationProgress.PROGRAMMED_BASAL)) {
            observables.add(
                observeSendProgramBasalCommand(basalProgram, false)
                    .doOnComplete(ActivationProgressUpdater(ActivationProgress.PROGRAMMED_BASAL))
            )
        }

        return observables.reversed()
    }

    override fun getStatus(type: ResponseType.StatusResponseType): Observable<PodEvent> {
        return Observable.concat(
            observeUniqueIdSet,
            observeConnectToPod,
            observeSendGetPodStatusCommand(type)
        ).interceptPodEvents()
    }

    override fun setBasalProgram(basalProgram: BasalProgram, hasBasalBeepEnabled: Boolean): Observable<PodEvent> {
        return Observable.concat(
            observePodRunning,
            observeConnectToPod,
            observeSendProgramBasalCommand(basalProgram, hasBasalBeepEnabled)
        ).interceptPodEvents()
    }

    private fun observeSendStopDeliveryCommand(
        deliveryType: StopDeliveryCommand.DeliveryType,
        beepEnabled: Boolean
    ): Observable<PodEvent> {
        return Observable.defer {
            val beepType = if (!beepEnabled)
                BeepType.SILENT
            else
                BeepType.LONG_SINGLE_BEEP

            bleManager.sendCommand(
                StopDeliveryCommand.Builder()
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setNonce(NONCE)
                    .setDeliveryType(deliveryType)
                    .setBeepType(beepType)
                    .build(),
                DefaultStatusResponse::class
            )
        }
    }

    override fun suspendDelivery(hasBasalBeepEnabled: Boolean): Observable<PodEvent> {
        return Observable.concat(
            observePodRunning,
            observeConnectToPod,
            observeSuspendDeliveryCommand(hasBasalBeepEnabled)
        ).doOnComplete {
            podStateManager.suspendAlertsEnabled = true
        }.interceptPodEvents()
    }

    private fun observeSuspendDeliveryCommand(hasBasalBeepEnabled: Boolean): Observable<PodEvent> {
        return Observable.defer {
            val beepType = if (!hasBasalBeepEnabled)
                BeepType.SILENT
            else
                BeepType.LONG_SINGLE_BEEP

            bleManager.sendCommand(
                SuspendDeliveryCommand.Builder()
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setNonce(NONCE)
                    .setBeepType(beepType)
                    .build(),
                DefaultStatusResponse::class
            )
        }
    }

    private fun observeSendProgramTempBasalCommand(rate: Double, durationInMinutes: Short, tempBasalBeeps: Boolean): Observable<PodEvent> {
        return Observable.defer {
            bleManager.sendCommand(
                ProgramTempBasalCommand.Builder()
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setNonce(NONCE)
                    .setProgramReminder(ProgramReminder(tempBasalBeeps, tempBasalBeeps, 0))
                    .setRateInUnitsPerHour(rate)
                    .setDurationInMinutes(durationInMinutes)
                    .build(),
                DefaultStatusResponse::class
            )
        }
    }

    override fun setTempBasal(rate: Double, durationInMinutes: Short, tempBasalBeeps: Boolean): Observable<PodEvent> {
        return Observable.concat(
            observePodRunning,
            observeConnectToPod,
            observeSendProgramTempBasalCommand(rate, durationInMinutes, tempBasalBeeps)
        ).interceptPodEvents()
    }

    override fun stopTempBasal(hasTempBasalBeepEnabled: Boolean): Observable<PodEvent> {
        return Observable.concat(
            observePodRunning,
            observeConnectToPod,
            observeSendStopDeliveryCommand(StopDeliveryCommand.DeliveryType.TEMP_BASAL, hasTempBasalBeepEnabled)
        ).interceptPodEvents()
    }

    override fun bolus(units: Double, confirmationBeeps: Boolean, completionBeeps: Boolean): Observable<PodEvent> {
        return Observable.concat(
            observePodRunning,
            observeConnectToPod,
            observeSendProgramBolusCommand(
                units,
                podStateManager.pulseRate!!.toByte(),
                confirmationBeeps,
                completionBeeps
            )
        ).interceptPodEvents()
    }

    override fun stopBolus(beep: Boolean): Observable<PodEvent> {
        return Observable.concat(
            observePodRunning,
            observeConnectToPod,
            observeSendStopDeliveryCommand(StopDeliveryCommand.DeliveryType.BOLUS, beep)
        ).interceptPodEvents()
    }

    private fun observeSendConfigureBeepsCommand(
        basalReminder: ProgramReminder = ProgramReminder(atStart = false, atEnd = false, atInterval = 0),
        tempBasalReminder: ProgramReminder = ProgramReminder(atStart = false, atEnd = false, atInterval = 0),
        bolusReminder: ProgramReminder = ProgramReminder(atStart = false, atEnd = false, atInterval = 0),
        immediateBeepType: BeepType = BeepType.SILENT
    ): Observable<PodEvent> {
        return Observable.defer {
            bleManager.sendCommand(
                ProgramBeepsCommand.Builder()
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setBasalReminder(basalReminder)
                    .setTempBasalReminder(tempBasalReminder)
                    .setBolusReminder(bolusReminder)
                    .setImmediateBeepType(immediateBeepType)
                    .build(),
                DefaultStatusResponse::class
            )
        }
    }

    override fun playBeep(beepType: BeepType): Observable<PodEvent> {
        return Observable.concat(
            observePodRunning,
            observeConnectToPod,
            observeSendConfigureBeepsCommand(immediateBeepType = beepType)
        ).interceptPodEvents()
    }

    override fun programAlerts(alertConfigurations: List<AlertConfiguration>): Observable<PodEvent> {
        return Observable.concat(
            observePodRunning,
            observeConnectToPod,
            observeSendProgramAlertsCommand(alertConfigurations)
        ).interceptPodEvents()
    }

    private fun observeSendSilenceAlertsCommand(alertTypes: EnumSet<AlertType>): Observable<PodEvent> {
        return Observable.defer {
            bleManager.sendCommand(
                SilenceAlertsCommand.Builder()
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setNonce(NONCE)
                    .setAlertTypes(alertTypes)
                    .build(),
                DefaultStatusResponse::class
            )
        }
    }

    override fun silenceAlerts(alertTypes: EnumSet<AlertType>): Observable<PodEvent> {
        return Observable.concat(
            observePodRunning,
            observeConnectToPod,
            observeSendSilenceAlertsCommand(alertTypes)
        ).interceptPodEvents()
    }

    private val observeSendDeactivateCommand: Observable<PodEvent>
        get() = Observable.defer {
            bleManager.sendCommand(
                DeactivateCommand.Builder()
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setNonce(NONCE)
                    .build(),
                DefaultStatusResponse::class
            ).doOnComplete { bleManager.removeBond() }
        }

    override fun deactivatePod(): Observable<PodEvent> {
        return Observable.concat(
            observeConnectToPod,
            observeSendDeactivateCommand
        ).interceptPodEvents()
    }

    inner class PodEventInterceptor : Consumer<PodEvent> {

        override fun accept(event: PodEvent) {
            logger.debug(LTag.PUMP, "Intercepted PodEvent in OmnipodDashManagerImpl: ${event.javaClass.simpleName}")

            when (event) {
                is PodEvent.AlreadyConnected        -> {
                }

                is PodEvent.BluetoothConnected      -> {
                }

                is PodEvent.Connected               -> {
                }

                is PodEvent.CommandSent             -> {
                    logger.debug(LTag.PUMP, "Command sent: ${event.command.commandType}")
                    podStateManager.activeCommand?.let {
                        if (it.sequence == event.command.sequenceNumber) {
                            it.sentRealtime = SystemClock.elapsedRealtime()
                        }
                    }
                    podStateManager.increaseMessageSequenceNumber()
                }

                is PodEvent.CommandSendNotConfirmed -> {
                    podStateManager.activeCommand?.let {
                        if (it.sequence == event.command.sequenceNumber) {
                            it.sentRealtime = SystemClock.elapsedRealtime()
                        }
                    }
                    podStateManager.increaseMessageSequenceNumber()
                }

                is PodEvent.ResponseReceived        -> {
                    podStateManager.increaseMessageSequenceNumber()
                    handleResponse(event.response)
                }

                is PodEvent.Paired                  -> {
                    podStateManager.uniqueId = event.uniqueId.toLong()
                }

                else                                -> {
                    // Do nothing
                }
            }
        }

        private fun handleResponse(response: Response) {
            when (response) {
                is VersionResponse       -> {
                    podStateManager.updateFromVersionResponse(response)
                }

                is SetUniqueIdResponse   -> {
                    podStateManager.updateFromSetUniqueIdResponse(response)
                }

                is DefaultStatusResponse -> {
                    podStateManager.updateFromDefaultStatusResponse(response)
                }

                is AlarmStatusResponse   -> {
                    podStateManager.updateFromAlarmStatusResponse(response)
                }
            }
        }
    }

    private fun Observable<PodEvent>.interceptPodEvents(): Observable<PodEvent> {
        return this.doOnNext(PodEventInterceptor())
            .doOnError(ErrorInterceptor())
            .subscribeOn(aapsSchedulers.io)
    }

    inner class ErrorInterceptor : Consumer<Throwable> {

        override fun accept(throwable: Throwable) {
            logger.debug(LTag.PUMP, "Intercepted error in OmnipodDashManagerImpl: $throwable")
        }
    }

    inner class ActivationProgressUpdater(private val value: ActivationProgress) : Action {

        override fun run() {
            podStateManager.activationProgress = value
        }
    }
}

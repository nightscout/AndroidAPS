package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.GetVersionCommand.Companion.DEFAULT_UNIQUE_ID
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.Observable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import java.util.*
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
        const val PRIME_BOLUS_DURATION_SECONDS = 35L
        const val CANNULA_INSERTION_BOLUS_DURATION_SECONDS = 10L
    }

    private val observePodReadyForActivationPart1: Observable<PodEvent>
        get() = Observable.defer {
            if (podStateManager.activationProgress.isBefore(ActivationProgress.PHASE_1_COMPLETED)) {
                Observable.empty()
            } else {
                Observable.error(IllegalStateException("Pod is in an incorrect state"))
            }
        }

    private val observePodReadyForActivationPart2: Observable<PodEvent>
        get() = Observable.defer {
            if (podStateManager.activationProgress.isAtLeast(ActivationProgress.PHASE_1_COMPLETED) && podStateManager.activationProgress.isBefore(
                    ActivationProgress.COMPLETED
                )) {
                Observable.empty()
            } else {
                Observable.error(IllegalStateException("Pod is in an incorrect state"))
            }
        }

    private val observeConnectToPod: Observable<PodEvent>
        get() = Observable.defer {
            bleManager.connect()
        } // TODO add retry

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
                    .setNonce(1229869870) // TODO
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
            ResponseType.StatusResponseType.ALARM_STATUS -> AlarmStatusResponse::class

            else -> return Observable.error(UnsupportedOperationException("No response type to class mapping for ${type.name}"))
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
        get() = Observable.defer {
            observeSendGetPodStatusCommand()
                .ignoreElements() //
                .andThen(
                    Observable.defer {
                        if (podStateManager.podStatus == PodStatus.RUNNING_ABOVE_MIN_VOLUME) {
                            Observable.empty()
                        } else {
                            Observable.error(IllegalStateException("Unexpected Pod status"))
                        }
                    }
                )
        }

    private fun observeSendProgramAlertsCommand(
        alertConfigurations: List<AlertConfiguration>,
        multiCommandFlag: Boolean = false
    ): Observable<PodEvent> {
        return Observable.defer {
            bleManager.sendCommand(
                ProgramAlertsCommand.Builder()
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setNonce(1229869870) // TODO
                    .setAlertConfigurations(alertConfigurations)
                    .build(),
                DefaultStatusResponse::class
            )
        }
    }

    private fun observeProgramBasalCommand(basalProgram: BasalProgram): Observable<PodEvent> {
        return Observable.defer {
            bleManager.sendCommand(
                ProgramBasalCommand.Builder()
                    .setUniqueId(podStateManager.uniqueId!!.toInt())
                    .setSequenceNumber(podStateManager.messageSequenceNumber)
                    .setNonce(1229869870) // TODO
                    .setProgramReminder(ProgramReminder(atStart = false, atEnd = false, atInterval = 0))
                    .setBasalProgram(basalProgram)
                    .setCurrentTime(Date())
                    .build(),
                DefaultStatusResponse::class
            )
        }
    }

    private val observeVerifyPrime: Observable<PodEvent>
        get() = Observable.defer {
            observeSendGetPodStatusCommand()
                .ignoreElements() //
                .andThen(
                    Observable.defer {
                        if (podStateManager.podStatus == PodStatus.CLUTCH_DRIVE_ENGAGED) {
                            Observable.empty()
                        } else {
                            Observable.error(IllegalStateException("Unexpected Pod status"))
                        }
                    }
                )
        }

    private val observeSendSetUniqueIdCommand: Observable<PodEvent>
        get() = Observable.defer {
            bleManager.sendCommand(
                SetUniqueIdCommand.Builder() //
                    .setSequenceNumber(podStateManager.messageSequenceNumber) //
                    .setUniqueId(podStateManager.uniqueId!!.toInt()) //
                    .setLotNumber(podStateManager.lotNumber!!.toInt()) //
                    .setPodSequenceNumber(podStateManager.podSequenceNumber!!.toInt())
                    .setInitializationTime(Date())
                    .build(),
                SetUniqueIdResponse::class
            ) //
        }

    private val observeSendGetVersionCommand: Observable<PodEvent>
        get() = Observable.defer {
            bleManager.sendCommand(
                GetVersionCommand.Builder() //
                    .setSequenceNumber(podStateManager.messageSequenceNumber) //
                    .setUniqueId(DEFAULT_UNIQUE_ID) //
                    .build(),
                VersionResponse::class
            ) //
        }

    override fun activatePodPart1(lowReservoirAlertTrigger: AlertTrigger.ReservoirVolumeTrigger?): Observable<PodEvent> {
        return Observable.concat(
            observePodReadyForActivationPart1,
            observePairNewPod,
            observeActivationPart1Commands(lowReservoirAlertTrigger)
        ).doOnComplete(ActivationProgressUpdater(ActivationProgress.PHASE_1_COMPLETED))
            // TODO these would be common for any observable returned in a public function in this class
            .doOnNext(PodEventInterceptor()) //
            .doOnError(ErrorInterceptor())
            .subscribeOn(aapsSchedulers.io)
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
            observables.add(Observable.timer(PRIME_BOLUS_DURATION_SECONDS, TimeUnit.SECONDS).flatMap { Observable.empty() })
            observables.add(
                observeSendProgramBolusCommand(
                    podStateManager.firstPrimeBolusVolume!! * 0.05,
                    podStateManager.primePulseRate!!.toByte(),
                    confirmationBeeps = false,
                    completionBeeps = false
                ).doOnComplete(ActivationProgressUpdater(ActivationProgress.PRIMING))
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

    override fun activatePodPart2(basalProgram: BasalProgram): Observable<PodEvent> {
        return Observable.concat(
            observePodReadyForActivationPart2,
            observeConnectToPod,
            observeActivationPart2Commands(basalProgram)
        ).doOnComplete(ActivationProgressUpdater(ActivationProgress.COMPLETED))
            // TODO these would be common for any observable returned in a public function in this class
            .doOnNext(PodEventInterceptor()) //
            .doOnError(ErrorInterceptor())
            .subscribeOn(aapsSchedulers.io)
    }

    private fun observeActivationPart2Commands(basalProgram: BasalProgram): Observable<PodEvent> {
        val observables = createActivationPart2Observables(basalProgram)

        return if (observables.isEmpty()) {
            Observable.empty()
        } else {
            Observable.concat(observables)
        }
    }

    private fun createActivationPart2Observables(basalProgram: BasalProgram): List<Observable<PodEvent>> {
        val observables = ArrayList<Observable<PodEvent>>()

        if (podStateManager.activationProgress.isBefore(ActivationProgress.CANNULA_INSERTED)) {
            observables.add(
                observeVerifyCannulaInsertion
                    .doOnComplete(ActivationProgressUpdater(ActivationProgress.CANNULA_INSERTED))
            )
        }
        if (podStateManager.activationProgress.isBefore(ActivationProgress.INSERTING_CANNULA)) {
            observables.add(Observable.timer(CANNULA_INSERTION_BOLUS_DURATION_SECONDS, TimeUnit.SECONDS).flatMap { Observable.empty() })
            observables.add(
                observeSendProgramBolusCommand(
                    podStateManager.secondPrimeBolusVolume!! * 0.05,
                    podStateManager.primePulseRate!!.toByte(),
                    confirmationBeeps = false,
                    completionBeeps = false
                ).doOnComplete(ActivationProgressUpdater(ActivationProgress.INSERTING_CANNULA))
            )
        }
        if (podStateManager.activationProgress.isBefore(ActivationProgress.UPDATED_EXPIRATION_ALERTS)) {
            observables.add(
                observeSendProgramAlertsCommand(
                    listOf(
                        // FIXME use user configured expiration alert
                        AlertConfiguration(
                            AlertType.EXPIRATION,
                            enabled = true,
                            durationInMinutes = TimeUnit.HOURS.toMinutes(7).toShort(),
                            autoOff = false,
                            AlertTrigger.TimerTrigger(
                                TimeUnit.HOURS.toMinutes(73).toShort()
                            ), // FIXME use activation time
                            BeepType.FOUR_TIMES_BIP_BEEP,
                            BeepRepetitionType.XXX3
                        ),
                        AlertConfiguration(
                            AlertType.EXPIRATION_IMMINENT,
                            enabled = true,
                            durationInMinutes = TimeUnit.HOURS.toMinutes(1).toShort(),
                            autoOff = false,
                            AlertTrigger.TimerTrigger(
                                TimeUnit.HOURS.toMinutes(79).toShort()
                            ), // FIXME use activation time
                            BeepType.FOUR_TIMES_BIP_BEEP,
                            BeepRepetitionType.XXX4
                        )
                    ),
                    multiCommandFlag = true
                ).doOnComplete(ActivationProgressUpdater(ActivationProgress.UPDATED_EXPIRATION_ALERTS))
            )
        }
        if (podStateManager.activationProgress.isBefore(ActivationProgress.PROGRAMMED_BASAL)) {
            observables.add(
                observeProgramBasalCommand(basalProgram)
                    .doOnComplete(ActivationProgressUpdater(ActivationProgress.PROGRAMMED_BASAL))
            )
        }

        return observables.reversed()
    }

    override fun getStatus(): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun setBasalProgram(program: BasalProgram): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun suspendDelivery(): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun setTime(): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun setTempBasal(rate: Double, durationInMinutes: Short): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun cancelTempBasal(): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun bolus(amount: Double): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun cancelBolus(): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun programBeeps(): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun programAlerts(alertConfigurations: List<AlertConfiguration>): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun silenceAlerts(alerts: EnumSet<AlertType>): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun deactivatePod(): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    inner class PodEventInterceptor : Consumer<PodEvent> {

        override fun accept(event: PodEvent) {
            logger.debug(LTag.PUMP, "Intercepted PodEvent in OmnipodDashManagerImpl: ${event.javaClass.simpleName}")
            when (event) {
                is PodEvent.AlreadyConnected -> {
                    podStateManager.bluetoothAddress = event.bluetoothAddress
                }

                is PodEvent.BluetoothConnected -> {
                    podStateManager.bluetoothAddress = event.bluetoothAddress
                }

                is PodEvent.Connected -> {
                }

                is PodEvent.CommandSent -> {
                    podStateManager.increaseMessageSequenceNumber()
                }

                is PodEvent.ResponseReceived -> {
                    podStateManager.increaseMessageSequenceNumber()
                    handleResponse(event.response)
                }

                is PodEvent.Paired -> {
                    podStateManager.uniqueId = event.uniqueId.toLong()
                }

                else -> {
                    // Do nothing
                }
            }
        }

        private fun handleResponse(response: Response) {
            when (response) {
                is VersionResponse -> {
                    podStateManager.updateFromVersionResponse(response)
                }

                is SetUniqueIdResponse -> {
                    podStateManager.updateFromSetUniqueIdResponse(response)
                }

                is DefaultStatusResponse -> {
                    podStateManager.updateFromDefaultStatusResponse(response)
                }

                is AlarmStatusResponse -> {
                    podStateManager.updateFromAlarmStatusResponse(response)
                }
            }
        }
    }

    inner class ErrorInterceptor : Consumer<Throwable> {

        override fun accept(throwable: Throwable) {
            logger.debug(LTag.PUMP, "Intercepted error in OmnipodDashManagerImpl: ${throwable.javaClass.simpleName}")
        }
    }

    inner class ActivationProgressUpdater(private val value: ActivationProgress) : Action {

        override fun run() {
            podStateManager.activationProgress = value
        }
    }
}

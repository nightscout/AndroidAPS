package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.GetVersionCommand
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.GetVersionCommand.Companion.DEFAULT_UNIQUE_ID
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertConfiguration
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.DefaultStatusResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.SetUniqueIdResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.VersionResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.rx.retryWithBackoff
import io.reactivex.Observable
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

    private val observePodReadyForActivationPart1: Observable<PodEvent>
        get() = Observable.defer {
            if (podStateManager.activationProgress.isBefore(ActivationProgress.PHASE_1_COMPLETED)) {
                Observable.empty()
            } else {
                Observable.error(IllegalStateException("Pod is in an incorrect state"))
            }
        }

    private val observeConnectToPod: Observable<PodEvent>
        get() = Observable.defer { bleManager.connect().retryWithBackoff(retries = 2, delay = 3, timeUnit = TimeUnit.SECONDS) } // TODO are these reasonable values?

    private val observeSendGetVersionCommand: Observable<PodEvent>
        get() = Observable.defer {
            bleManager.sendCommand(GetVersionCommand.Builder() //
                .setSequenceNumber(podStateManager.messageSequenceNumber) //
                .setUniqueId(DEFAULT_UNIQUE_ID) //
                .build()) //
        }

    override fun activatePodPart1(): Observable<PodEvent> {
        return Observable.concat(
            observePodReadyForActivationPart1,
            observeConnectToPod,
            observeSendGetVersionCommand
            // ... Send more commands
        ) //
            // TODO these would be common for any observable returned in a public function in this class
            .doOnNext(PodEventInterceptor()) //
            .doOnError(ErrorInterceptor())
            .subscribeOn(aapsSchedulers.io)
    }

    override fun activatePodPart2(): Observable<PodEvent> {
        // TODO
        return Observable.empty()
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
                    podStateManager.uniqueId = event.uniqueId
                }

                is PodEvent.BluetoothConnected -> {
                    podStateManager.bluetoothAddress = event.address
                }

                is PodEvent.Connected -> {
                    podStateManager.uniqueId = event.uniqueId
                }

                is PodEvent.ResponseReceived -> {
                    podStateManager.increaseMessageSequenceNumber()
                    handleResponse(event.response)
                }

                else                           -> {
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
}
package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.GetVersionCommand
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.GetVersionCommand.Companion.DEFAULT_UNIQUE_ID
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertConfiguration
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertSlot
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmnipodDashManagerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val podStateManager: OmnipodDashPodStateManager,
    private val bleManager: OmnipodDashBleManager
) : OmnipodDashManager {

    private val observePodReadyForActivationPart1: Observable<PodEvent>
        get() = Observable.defer {
            if (podStateManager.activationProgress.isBefore(ActivationProgress.PHASE_1_COMPLETED)) {
                Observable.empty()
            } else {
                Observable.error(IllegalStateException("Pod is in an incorrect state"))
            }
        }

    override fun activatePodPart1(): Observable<PodEvent> {
        return Observable.concat(
            observePodReadyForActivationPart1,
            bleManager.connect(),
            Observable.defer {
                bleManager.sendCommand(GetVersionCommand.Builder() //
                    .setSequenceNumber(podStateManager.messageSequenceNumber) //
                    .setUniqueId(DEFAULT_UNIQUE_ID) //
                    .build()) //
            }
            // ... Send more commands
        ).subscribeOn(Schedulers.io()) //
            .observeOn(AndroidSchedulers.mainThread())
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

    override fun silenceAlerts(alerts: EnumSet<AlertSlot>): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }

    override fun deactivatePod(): Observable<PodEvent> {
        // TODO
        return Observable.empty()
    }
}
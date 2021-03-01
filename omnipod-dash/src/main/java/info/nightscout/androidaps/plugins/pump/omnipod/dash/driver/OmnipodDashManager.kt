package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertConfiguration
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertTrigger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import io.reactivex.Observable
import java.util.*

interface OmnipodDashManager {

    fun activatePodPart1(lowReservoirAlertTrigger: AlertTrigger.ReservoirVolumeTrigger?): Observable<PodEvent>

    fun activatePodPart2(basalProgram: BasalProgram): Observable<PodEvent>

    fun getStatus(): Observable<PodEvent>

    fun setBasalProgram(program: BasalProgram): Observable<PodEvent>

    fun suspendDelivery(): Observable<PodEvent>

    fun setTime(): Observable<PodEvent>

    fun setTempBasal(rate: Double, durationInMinutes: Short): Observable<PodEvent>

    fun cancelTempBasal(): Observable<PodEvent>

    fun bolus(amount: Double): Observable<PodEvent>

    fun cancelBolus(): Observable<PodEvent>

    fun programBeeps(): Observable<PodEvent>

    fun programAlerts(alertConfigurations: List<AlertConfiguration>): Observable<PodEvent>

    fun silenceAlerts(alerts: EnumSet<AlertType>): Observable<PodEvent>

    fun deactivatePod(): Observable<PodEvent>
}

package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import io.reactivex.Observable

interface OmnipodDashManager {

    fun activatePodPart1(): Observable<PodEvent>

    fun activatePodPart2(): Observable<PodEvent>

    fun getStatus(): Observable<PodEvent>

    fun setBasalProgram(program: BasalProgram): Observable<PodEvent>

    fun suspendDelivery(): Observable<PodEvent>

    fun setTime(): Observable<PodEvent>

    fun setTempBasal(rate: Double, durationInMinutes: Short): Observable<PodEvent>

    fun cancelTempBasal(): Observable<PodEvent>

    fun bolus(amount: Double): Observable<PodEvent>

    fun cancelBolus(): Observable<PodEvent>

    fun silenceAlerts(): Observable<PodEvent>

    fun deactivatePod(): Observable<PodEvent>
}
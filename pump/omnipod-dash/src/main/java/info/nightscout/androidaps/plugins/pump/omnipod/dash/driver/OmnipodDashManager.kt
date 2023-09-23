package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertConfiguration
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertTrigger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BasalProgram
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType
import io.reactivex.rxjava3.core.Observable
import java.util.*
import java.util.concurrent.CountDownLatch

interface OmnipodDashManager {

    fun activatePodPart1(lowReservoirAlertTrigger: AlertTrigger.ReservoirVolumeTrigger?): Observable<PodEvent>

    fun activatePodPart2(basalProgram: BasalProgram, userConfiguredExpirationReminderHours: Long?, userConfiguredExpirationAlarmHours: Long?): Observable<PodEvent>

    fun getStatus(type: ResponseType.StatusResponseType): Observable<PodEvent>

    fun setBasalProgram(basalProgram: BasalProgram, hasBasalBeepEnabled: Boolean): Observable<PodEvent>

    fun suspendDelivery(hasBasalBeepEnabled: Boolean): Observable<PodEvent>

    fun setTempBasal(rate: Double, durationInMinutes: Short, tempBasalBeeps: Boolean): Observable<PodEvent>

    fun stopTempBasal(hasTempBasalBeepEnabled: Boolean): Observable<PodEvent>

    fun bolus(units: Double, confirmationBeeps: Boolean, completionBeeps: Boolean): Observable<PodEvent>

    fun stopBolus(beep: Boolean): Observable<PodEvent>

    fun playBeep(beepType: BeepType): Observable<PodEvent>

    fun programAlerts(alertConfigurations: List<AlertConfiguration>): Observable<PodEvent>

    fun silenceAlerts(alertTypes: EnumSet<AlertType>): Observable<PodEvent>

    fun deactivatePod(): Observable<PodEvent>

    fun disconnect(closeGatt: Boolean = false)

    fun connect(stop: CountDownLatch): Observable<PodEvent>
}

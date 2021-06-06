package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session.ConnectionState
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response
import io.reactivex.Observable
import kotlin.reflect.KClass

interface OmnipodDashBleManager {

    fun sendCommand(cmd: Command, responseType: KClass<out Response>): Observable<PodEvent>

    fun getStatus(): ConnectionState

    fun connect(): Observable<PodEvent>

    fun pairNewPod(): Observable<PodEvent>

    fun disconnect()
}

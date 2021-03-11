package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.status.ConnectionStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.event.PodEvent
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import io.reactivex.Observable

interface OmnipodDashBleManager {

    fun sendCommand(cmd: Command): Observable<PodEvent>

    fun getStatus(): ConnectionStatus

    fun connect(): Observable<PodEvent>

    fun pairNewPod(): Observable<PodEvent>

    fun disconnect()
}

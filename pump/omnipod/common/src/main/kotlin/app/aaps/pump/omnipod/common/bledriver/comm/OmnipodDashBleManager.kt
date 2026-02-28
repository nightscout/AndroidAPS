package app.aaps.pump.omnipod.common.bledriver.comm

import app.aaps.pump.omnipod.common.bledriver.comm.session.Connection
import app.aaps.pump.omnipod.common.bledriver.comm.session.ConnectionState
import app.aaps.pump.omnipod.common.bledriver.event.PodEvent
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.CountDownLatch
import kotlin.reflect.KClass

interface OmnipodDashBleManager {

    fun sendCommand(cmd: Command, responseType: KClass<out Response>): Observable<PodEvent>

    fun getStatus(): ConnectionState

    // used for sync connections
    fun connect(timeoutMs: Long = Connection.BASE_CONNECT_TIMEOUT_MS * 3): Observable<PodEvent>

    // used for async connections
    fun connect(stopConnectionLatch: CountDownLatch): Observable<PodEvent>

    fun pairNewPod(): Observable<PodEvent>

    fun disconnect(closeGatt: Boolean = false)
    fun removeBond()
}

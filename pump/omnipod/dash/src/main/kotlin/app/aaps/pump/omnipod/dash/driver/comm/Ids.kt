package app.aaps.pump.omnipod.dash.driver.comm

import app.aaps.pump.omnipod.dash.driver.comm.scan.PodScanner
import app.aaps.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager

class Ids(podState: OmnipodDashPodStateManager) {

    val myId = Id.fromInt(OmnipodDashBleManagerImpl.CONTROLLER_ID)
    private val uniqueId = podState.uniqueId
    val podId = uniqueId?.let(Id::fromLong)
        ?: myId.increment() // pod not activated

    companion object {

        fun notActivated(): Id {
            return Id.fromLong(
                PodScanner
                    .POD_ID_NOT_ACTIVATED
            )
        }
    }
}

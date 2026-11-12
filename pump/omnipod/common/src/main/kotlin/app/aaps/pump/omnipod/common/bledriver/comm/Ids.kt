package app.aaps.pump.omnipod.common.bledriver.comm

import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager

/**
 * The controller/pod identity pair used throughout pairing and session establishment.
 *
 * The primary constructor (from [OmnipodDashPodStateManager]) is unchanged from before -
 * every existing Dash call site behaves identically. [forController] is an additive
 * factory for pod types (e.g. Omnipod 5) that don't have a Dash-style pod state manager
 * to derive their controller/pod ids from - O5 derives its controller id from
 * [app.aaps.pump.omnipod.common.bledriver.comm.pair.O5CertificateStore.controllerId]
 * instead.
 */
class Ids private constructor(val myId: Id, val podId: Id) {

    constructor(podState: OmnipodDashPodStateManager) : this(
        myId = Id.fromInt(OmnipodDashBleManagerImpl.CONTROLLER_ID),
        podId = podState.uniqueId?.let(Id::fromLong)
            ?: Id.fromInt(OmnipodDashBleManagerImpl.CONTROLLER_ID).increment()
    )

    companion object {

        fun notActivated(): Id {
            return Id.fromLong(
                PodScanner.POD_ID_NOT_ACTIVATED
            )
        }

        /** For pod types without a Dash-style pod state manager to derive ids from. */
        fun forController(myId: Id, podId: Id): Ids = Ids(myId, podId)
    }
}

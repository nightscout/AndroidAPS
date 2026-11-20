package app.aaps.pump.omnipod.omnipod5.bledriver.comm

import app.aaps.pump.omnipod.common.bledriver.comm.OmnipodBleManager
import io.reactivex.rxjava3.core.Completable

/**
 * Omnipod 5 BLE manager with support for the O5-only AID setup command batch.
 */
interface O5BleManager : OmnipodBleManager {

    /**
     * True while the activation wizard runs a step (pairing, prime, cannula insertion). The wizard
     * talks to the pod outside the command queue, so the queue must not use or close the BLE link
     * during this time (see Dash #5219). Only the wizard sets it, and it must clear it in a `finally`.
     */
    var isActivationRunning: Boolean

    /** Sends the O5-only AID setup command batch (see [app.aaps.pump.omnipod.common
     *  .bledriver.pod.command.aid.O5AidSetupCommands]) over the already-established
     *  session. Must be called after [pairNewPod] completes and before any other
     *  activation command - see that object's class doc for why. */
    fun sendAidSetupCommands(): Completable
}

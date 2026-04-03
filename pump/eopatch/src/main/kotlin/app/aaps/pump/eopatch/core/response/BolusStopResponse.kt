package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode

class BolusStopResponse : BolusResponse {

    val injectedBolusAmount: Int
    val injectingBolusAmount: Int
    val targetBolusAmount: Int

    constructor(id: Int, injectedBolusAmount: Int, injectingBolusAmount: Int, targetBolusAmount: Int) : super(id = id) {
        this.injectedBolusAmount = injectedBolusAmount
        this.injectingBolusAmount = injectingBolusAmount
        this.targetBolusAmount = targetBolusAmount
    }

    constructor(id: Int, resultCode: PatchBleResultCode) : super(resultCode, id) {
        injectedBolusAmount = 0
        injectingBolusAmount = 0
        targetBolusAmount = 0
    }

    val remainBolusAmount: Int
        get() = targetBolusAmount - injectedBolusAmount - injectingBolusAmount

    override fun toString(): String =
        "BolusStopResponse{injected=$injectedBolusAmount, injecting=$injectingBolusAmount, target=$targetBolusAmount, timestamp=$timestamp, resultCode=$resultCode}"
}

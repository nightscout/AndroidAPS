package app.aaps.pump.eopatch.core.response

import app.aaps.pump.eopatch.core.code.PatchBleResultCode

class ComboBolusStopResponse : BolusResponse {

    val injectedBolusAmount: Int
    val injectedExBolusAmount: Int
    val extId: Int

    private val injectingBolusAmount: Int
    private val injectingExBolusAmount: Int

    constructor(
        id: Int, injectedBolusAmount: Int, injectingBolusAmount: Int,
        idExt: Int, injectedExBolusAmount: Int, injectingExBolusAmount: Int
    ) : super(id = id) {
        this.extId = idExt
        this.injectedBolusAmount = injectedBolusAmount
        this.injectingBolusAmount = injectingBolusAmount
        this.injectedExBolusAmount = injectedExBolusAmount
        this.injectingExBolusAmount = injectingExBolusAmount
    }

    constructor(id: Int, resultCode: PatchBleResultCode) : super(resultCode, id) {
        extId = 0
        injectedBolusAmount = 0
        injectingBolusAmount = 0
        injectedExBolusAmount = 0
        injectingExBolusAmount = 0
    }
}

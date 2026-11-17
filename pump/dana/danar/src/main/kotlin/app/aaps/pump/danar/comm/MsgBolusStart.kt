package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import kotlin.math.roundToInt

class MsgBolusStart(
    injector: MetroMemberInjector,
    private val amount: Double
) : MessageBase(injector) {

    init {
        setCommand(0x0102)
        // Amount already constrained in IU (queue) and cU (PumpWithConcentration boundary). Rounded: a cut gave less,
        // 1.15 * 100 is 114.99999..., so a bolus of 1.15 U went to the pump as 1.14 U.
        addParamInt((amount * 100).roundToInt())
        aapsLogger.debug(LTag.PUMPBTCOMM, "Bolus start : $amount")
    }

    override fun handleMessage(bytes: ByteArray) {
        val errorCode = intFromBuff(bytes, 0, 1)
        if (errorCode != 2) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode ERROR!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode OK")
        }
        danaPump.bolusStartErrorCode = errorCode
    }
}
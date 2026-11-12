package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import kotlin.math.roundToInt

class MsgSetExtendedBolusStart(
    injector: MetroMemberInjector,
    private val amount: Double,
    private var halfHours: Byte

) : MessageBase(injector) {

    init {
        setCommand(0x0407)
        aapsLogger.debug(LTag.PUMPBTCOMM, "New message")
        // HARDCODED LIMITS
        if (halfHours < 1) halfHours = 1
        if (halfHours > 16) halfHours = 16
        // Amount already constrained in IU (queue) and cU (PumpWithConcentration boundary). Rounded: a cut gave less,
        // 1.15 * 100 is 114.99999..., so 1.15 U went to the pump as 1.14 U.
        val hundredths = (amount * 100).roundToInt()
        addParamInt(hundredths)
        addParamByte(halfHours)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start: " + hundredths / 100.0 + "U halfHours: " + halfHours.toInt())
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start result: $result ERROR!!!")
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start result: $result")
        }
    }
}
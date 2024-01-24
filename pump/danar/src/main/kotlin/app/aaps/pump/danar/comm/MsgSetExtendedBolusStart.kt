package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.objects.constraints.ConstraintObject
import dagger.android.HasAndroidInjector

class MsgSetExtendedBolusStart(
    injector: HasAndroidInjector,
    private var amount: Double,
    private var halfHours: Byte

) : MessageBase(injector) {

    init {
        setCommand(0x0407)
        aapsLogger.debug(LTag.PUMPBTCOMM, "New message")
        // HARDCODED LIMITS
        if (halfHours < 1) halfHours = 1
        if (halfHours > 16) halfHours = 16
        amount = constraintChecker.applyBolusConstraints(ConstraintObject(amount, aapsLogger)).value()
        addParamInt((amount * 100).toInt())
        addParamByte(halfHours)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start: " + (amount * 100).toInt() / 100.0 + "U halfHours: " + halfHours.toInt())
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
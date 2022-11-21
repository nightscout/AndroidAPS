package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.rx.logging.LTag


class MsgSetExtendedBolusStart(
    injector: HasAndroidInjector,
    private var amount: Double,
    private var halfhours: Byte

) : MessageBase(injector) {

    init {
        setCommand(0x0407)
        aapsLogger.debug(LTag.PUMPBTCOMM, "New message")
        // HARDCODED LIMITS
        if (halfhours < 1) halfhours = 1
        if (halfhours > 16) halfhours = 16
        amount = constraintChecker.applyBolusConstraints(Constraint(amount)).value()
        addParamInt((amount * 100).toInt())
        addParamByte(halfhours)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start: " + (amount * 100).toInt() / 100.0 + "U halfhours: " + halfhours.toInt())
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start result: $result FAILED!!!")
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set extended bolus start result: $result")
        }
    }
}
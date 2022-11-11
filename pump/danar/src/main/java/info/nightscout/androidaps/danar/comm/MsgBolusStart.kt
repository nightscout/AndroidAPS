package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.rx.logging.LTag


class MsgBolusStart(
    injector: HasAndroidInjector,
    private var amount: Double
) : MessageBase(injector) {

    init {
        setCommand(0x0102)
        // HARDCODED LIMIT
        amount = constraintChecker.applyBolusConstraints(Constraint(amount)).value()
        addParamInt((amount * 100).toInt())
        aapsLogger.debug(LTag.PUMPBTCOMM, "Bolus start : $amount")
    }

    override fun handleMessage(bytes: ByteArray) {
        val errorCode = intFromBuff(bytes, 0, 1)
        if (errorCode != 2) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode FAILED!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode OK")
        }
        danaPump.bolusStartErrorCode = errorCode
    }
}
package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.pump.dana.comm.RecordTypes
import info.nightscout.rx.logging.LTag
import java.util.Calendar

class MsgSetCarbsEntry(
    injector: HasAndroidInjector,
    val time: Long,
    val amount: Int
) : MessageBase(injector) {

    init {
        setCommand(0x0402)
        aapsLogger.debug(LTag.PUMPBTCOMM, "New message")
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        addParamByte(RecordTypes.RECORD_TYPE_CARBO)
        addParamByte((calendar[Calendar.YEAR] % 100).toByte())
        addParamByte((calendar[Calendar.MONTH] + 1).toByte())
        addParamByte(calendar[Calendar.DAY_OF_MONTH].toByte())
        addParamByte(calendar[Calendar.HOUR_OF_DAY].toByte())
        addParamByte(calendar[Calendar.MINUTE].toByte())
        addParamByte(calendar[Calendar.SECOND].toByte())
        addParamByte(0x43.toByte()) //??
        addParamInt(amount)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Set carb entry: " + amount + " date " + calendar.time.toString())
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set carb entry result: $result FAILED!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPBTCOMM, "Set carb entry result: $result")
        }
    }
}
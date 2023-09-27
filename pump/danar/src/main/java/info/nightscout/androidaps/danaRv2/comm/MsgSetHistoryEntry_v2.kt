package info.nightscout.androidaps.danaRv2.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.comm.MessageBase
import java.util.Date
import java.util.GregorianCalendar

class MsgSetHistoryEntry_v2(
    injector: HasAndroidInjector,
    type: Int, time: Long, param1: Int, param2: Int
) : MessageBase(injector) {

    init {
        setCommand(0xE004)
        addParamByte(type.toByte())
        val gtime = GregorianCalendar()
        gtime.timeInMillis = time
        addParamDateTime(gtime)
        addParamInt(param1)
        addParamInt(param2)
        aapsLogger.debug(LTag.PUMPCOMM, "Set history entry: type: " + type + " date: " + Date(time).toString() + " param1: " + param1 + " param2: " + param2)
    }

    override fun handleMessage(bytes: ByteArray) {
        val result = intFromBuff(bytes, 0, 1)
        if (result != 1) {
            failed = true
            aapsLogger.debug(LTag.PUMPCOMM, "Set history entry result: $result FAILED!!!")
        } else {
            failed = false
            aapsLogger.debug(LTag.PUMPCOMM, "Set history entry result: $result")
        }
    }
}
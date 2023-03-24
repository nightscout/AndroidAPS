package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.comm.enums.CommandType.SET_TIME_ZONE
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.util.MedtrumTimeUtil
import info.nightscout.shared.utils.DateUtil
import javax.inject.Inject

class SetTimeZonePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    @Inject lateinit var dateUtil: DateUtil

    init {
        opCode = SET_TIME_ZONE.code
    }

    override fun getRequest(): ByteArray {
        val time = MedtrumTimeUtil().getCurrentTimePumpSeconds()
        var offsetMins = dateUtil.getTimeZoneOffsetMinutes(dateUtil.now())
        if (offsetMins < 0) offsetMins += 65536
        return byteArrayOf(opCode) + offsetMins.toByteArray(2) + time.toByteArray(4)
    }
}

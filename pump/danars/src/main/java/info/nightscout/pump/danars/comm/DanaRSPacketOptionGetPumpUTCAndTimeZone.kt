package info.nightscout.pump.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.pump.dana.DanaPump
import info.nightscout.rx.logging.LTag
import org.joda.time.DateTime
import javax.inject.Inject

class DanaRSPacketOptionGetPumpUTCAndTimeZone(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__GET_PUMP_UTC_AND_TIME_ZONE
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting pump UTC time")
    }

    override fun handleMessage(data: ByteArray) {
        val year = byteArrayToInt(getBytes(data, DATA_START, 1))
        val month = byteArrayToInt(getBytes(data, DATA_START + 1, 1))
        val day = byteArrayToInt(getBytes(data, DATA_START + 2, 1))
        val hour = byteArrayToInt(getBytes(data, DATA_START + 3, 1))
        val min = byteArrayToInt(getBytes(data, DATA_START + 4, 1))
        val sec = byteArrayToInt(getBytes(data, DATA_START + 5, 1))
        val zoneOffset = getBytes(data, DATA_START + 6, 1)[0].toInt()
        val time = DateTime(2000 + year, month, day, hour, min, sec)
        danaPump.setPumpTime(time.millis, zoneOffset)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump time ${dateUtil.dateAndTimeAndSecondsString(danaPump.getPumpTime())} ZoneOffset: $zoneOffset")
    }

    override fun handleMessageNotReceived() {
        super.handleMessageNotReceived()
        danaPump.resetPumpTime()
    }

    override val friendlyName: String = "OPTION__GET_PUMP_UTC_AND_TIMEZONE"
}
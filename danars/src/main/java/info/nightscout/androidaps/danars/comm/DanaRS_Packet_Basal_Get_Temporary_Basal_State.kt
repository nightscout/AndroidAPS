package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ceil

class DanaRS_Packet_Basal_Get_Temporary_Basal_State(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__TEMPORARY_BASAL_STATE
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting temporary basal status")
    }

    var isTempBasalInProgress: Boolean = false
    var tempBasalTotalSec: Int = 0
    var tempBasalPercent: Int = 0

    override fun handleMessage(data: ByteArray) {
        val error = byteArrayToInt(getBytes(data, DATA_START, 1))
        isTempBasalInProgress = byteArrayToInt(getBytes(data, DATA_START + 1, 1)) == 0x01
        val isAPSTempBasalInProgress = byteArrayToInt(getBytes(data, DATA_START + 1, 1)) == 0x02
        tempBasalPercent = byteArrayToInt(getBytes(data, DATA_START + 2, 1))
        if (tempBasalPercent > 200) tempBasalPercent = (tempBasalPercent - 200) * 10
        val durationHour = byteArrayToInt(getBytes(data, DATA_START + 3, 1))
        tempBasalTotalSec = if (durationHour == 150) 15 * 60 else if (durationHour == 160) 30 * 60 else durationHour * 60 * 60
        val runningMin = byteArrayToInt(getBytes(data, DATA_START + 4, 2))
        if (error != 0) failed = true
        val tempBasalRemainingMin = (danaPump.tempBasalTotalSec - runningMin * 60) / 60
        val tempBasalStart = if (isTempBasalInProgress) getDateFromTempBasalSecAgo(runningMin * 60) else 0
        if (!isTempBasalInProgress) danaPump.isTempBasalInProgress = false
        aapsLogger.debug(LTag.PUMPCOMM, "Error code: $error")
        aapsLogger.debug(LTag.PUMPCOMM, "Is temp basal running: $isTempBasalInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Is APS temp basal running: $isAPSTempBasalInProgress")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal percent: $tempBasalPercent")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal remaining min: $tempBasalRemainingMin")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal total sec: $tempBasalTotalSec")
        aapsLogger.debug(LTag.PUMPCOMM, "Current temp basal start: " + dateUtil.dateAndTimeString(tempBasalStart))
    }

    override fun getFriendlyName(): String {
        return "BASAL__TEMPORARY_BASAL_STATE"
    }

    private fun getDateFromTempBasalSecAgo(tempBasalAgoSecs: Int): Long {
        return (ceil(System.currentTimeMillis() / 1000.0) - tempBasalAgoSecs).toLong() * 1000
    }
}
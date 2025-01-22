package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils

class CmdSettingSet(
    maxBolus: Double? = null,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    var lowAlarm: Double = 0.0
    var bolusThresholdStep: Int = EquilConst.EQUIL_BOLUS_THRESHOLD_STEP

    init {
        maxBolus?.let { bolusThresholdStep = Utils.decodeSpeedToUH(maxBolus) }
    }

    override fun getFirstData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val equilCmd = byteArrayOf(0x01, 0x05)
        val useTime = Utils.intToBytes(0)
        val autoCloseTime = Utils.intToBytes(0)
        val lowAlarmByte = Utils.intToTwoBytes(1600)
        val fastBolus = Utils.intToTwoBytes(0)
        val occlusion = Utils.intToTwoBytes(2800)
        val insulinUnit = Utils.intToTwoBytes(8)
        val basalThreshold = Utils.intToTwoBytes(240)
        val bolusThreshold = Utils.intToTwoBytes(bolusThresholdStep)
        val data = Utils.concat(
            indexByte, equilCmd, useTime, autoCloseTime,
            lowAlarmByte, fastBolus,
            occlusion, insulinUnit, basalThreshold, bolusThreshold
        )
        pumpReqIndex++
        aapsLogger.debug(
            LTag.PUMPCOMM,
            "CmdSettingSet data===" + Utils.bytesToHex(data) + "====" + lowAlarm + "===" + Utils.decodeSpeedToUH(lowAlarm)
        )
        return data
    }

    override fun getNextData(): ByteArray {
        val indexByte = Utils.intToBytes(pumpReqIndex)
        val data2 = byteArrayOf(0x00, 0x05, 0x01)
        val data = Utils.concat(indexByte, data2)
        pumpReqIndex++
        return data
    }

    override fun decodeConfirmData(data: ByteArray) {
        //val index = data[4].toInt()
        //        int i1 = ((data[15] & 0x0f) << 8) | data[14] & 0xff;
//        int i2 = ((data[17] & 0x0f) << 8) | data[16] & 0xff;
//        int i3 = ((data[19] & 0x0f) << 8) | data[18] & 0xff;
//        int i4 = ((data[21] & 0x0f) << 8) | data[20] & 0xff;
//        int i5 = ((data[23] & 0x0f) << 8) | data[22] & 0xff;
//        int i6 = ((data[25] & 0x0f) << 8) | data[24] & 0xff;
//        lowAlarm = Utils.internalDecodeSpeedToUH(i1);
//        largefastAlarm = Utils.internalDecodeSpeedToUH(i2);
//        stopAlarm = Utils.internalDecodeSpeedToUH(i3);
//        infusionUnit = Utils.internalDecodeSpeedToUH(i4);
//        basalAlarm = Utils.internalDecodeSpeedToUH(i5);
//        largeAlarm = Utils.internalDecodeSpeedToUH(i6);
//        aapsLogger.debug(LTag.PUMPCOMM,
//                "CmdSettingSet===" + Crc.bytesToHex(data) + "====" + lowAlarm);
        synchronized(this) {
            cmdStatus = true
            (this as Object).notify()
        }
    }

    override fun isPairStep(): Boolean = true

    override fun getEventType(): EquilHistoryRecord.EventType? = null
}

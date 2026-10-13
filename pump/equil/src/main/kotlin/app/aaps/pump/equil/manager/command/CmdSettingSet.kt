package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.Utils

class CmdSettingSet(
    maxBolus: Double,
    maxBasal: Double,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    equilManager: EquilManager
) : BaseSetting(System.currentTimeMillis(), aapsLogger, preferences, equilManager) {

    var lowAlarm: Double = 0.0
    var bolusThresholdStep: Int = EquilConst.EQUIL_BOLUS_THRESHOLD_STEP
    var basalThresholdStep: Int = EquilConst.EQUIL_BASAL_THRESHOLD_STEP

    init {
        bolusThresholdStep = Utils.decodeSpeedToUH(maxBolus)
        basalThresholdStep = Utils.decodeSpeedToUH(maxBasal)
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
        val basalThreshold = Utils.intToTwoBytes(basalThresholdStep)
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
        synchronized(this) {
            cmdSuccess = true
            (this as Object).notify()
        }
    }

    override fun isPairStep(): Boolean = true

    override fun getEventType(): EquilHistoryRecord.EventType? = null
}

package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketOptionGetUserOption @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__GET_USER_OPTION
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting user settings")
    }

    override fun handleMessage(data: ByteArray) {
        danaPump.timeDisplayType24 = intFromBuff(data, 0, 1) == 0
        danaPump.buttonScrollOnOff = intFromBuff(data, 1, 1) == 1
        danaPump.beepAndAlarm = intFromBuff(data, 2, 1)
        danaPump.lcdOnTimeSec = intFromBuff(data, 3, 1)
        danaPump.backlightOnTimeSec = intFromBuff(data, 4, 1)
        danaPump.selectedLanguage = intFromBuff(data, 5, 1)
        danaPump.units = intFromBuff(data, 6, 1)
        danaPump.shutdownHour = intFromBuff(data, 7, 1)
        danaPump.lowReservoirRate = intFromBuff(data, 8, 1)
        danaPump.cannulaVolume = intFromBuff(data, 9, 2)
        danaPump.refillAmount = intFromBuff(data, 11, 2)
        val selectableLanguage1 = intFromBuff(data, 13, 1)
        val selectableLanguage2 = intFromBuff(data, 14, 1)
        val selectableLanguage3 = intFromBuff(data, 15, 1)
        val selectableLanguage4 = intFromBuff(data, 16, 1)
        val selectableLanguage5 = intFromBuff(data, 17, 1)
        if (data.size >= 22) // hw 7+
            danaPump.target = intFromBuff(data, 18, 2)
        // Pump's screen on time can't be less than 5
        failed = danaPump.lcdOnTimeSec < 5
        aapsLogger.debug(LTag.PUMPCOMM, "timeDisplayType24: " + danaPump.timeDisplayType24)
        aapsLogger.debug(LTag.PUMPCOMM, "buttonScrollOnOff: " + danaPump.buttonScrollOnOff)
        aapsLogger.debug(LTag.PUMPCOMM, "beepAndAlarm: " + danaPump.beepAndAlarm)
        aapsLogger.debug(LTag.PUMPCOMM, "lcdOnTimeSec: " + danaPump.lcdOnTimeSec)
        aapsLogger.debug(LTag.PUMPCOMM, "backlightOnTimeSec: " + danaPump.backlightOnTimeSec)
        aapsLogger.debug(LTag.PUMPCOMM, "selectedLanguage: " + danaPump.selectedLanguage)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump units: " + if (danaPump.units == DanaPump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger.debug(LTag.PUMPCOMM, "shutdownHour: " + danaPump.shutdownHour)
        aapsLogger.debug(LTag.PUMPCOMM, "lowReservoirRate: " + danaPump.lowReservoirRate)
        aapsLogger.debug(LTag.PUMPCOMM, "cannulaVolume: " + danaPump.cannulaVolume)
        aapsLogger.debug(LTag.PUMPCOMM, "refillAmount: " + danaPump.refillAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage1: $selectableLanguage1")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage2: $selectableLanguage2")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage3: $selectableLanguage3")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage4: $selectableLanguage4")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage5: $selectableLanguage5")
        aapsLogger.debug(LTag.PUMPCOMM, "target: ${if (danaPump.units == DanaPump.UNITS_MGDL) danaPump.target else danaPump.target / 100}")
    }

    override val friendlyName: String = "OPTION__GET_USER_OPTION"
}
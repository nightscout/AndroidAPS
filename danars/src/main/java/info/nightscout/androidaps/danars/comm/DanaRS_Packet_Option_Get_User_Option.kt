package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_Option_Get_User_Option(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_OPTION__GET_USER_OPTION
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting user settings")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        danaPump.timeDisplayType24 = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0
        dataIndex += dataSize
        dataSize = 1
        danaPump.buttonScrollOnOff = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 1
        dataIndex += dataSize
        dataSize = 1
        danaPump.beepAndAlarm = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.lcdOnTimeSec = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.backlightOnTimeSec = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.selectedLanguage = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.units = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.shutdownHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.lowReservoirRate = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaPump.cannulaVolume = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 2
        danaPump.refillAmount = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val selectableLanguage1 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val selectableLanguage2 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val selectableLanguage3 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val selectableLanguage4 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val selectableLanguage5 = byteArrayToInt(getBytes(data, dataIndex, dataSize))
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
        aapsLogger.debug(LTag.PUMPCOMM, "refillAmount: " + danaPump.refillAmount)
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage1: $selectableLanguage1")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage2: $selectableLanguage2")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage3: $selectableLanguage3")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage4: $selectableLanguage4")
        aapsLogger.debug(LTag.PUMPCOMM, "selectableLanguage5: $selectableLanguage5")
    }

    override fun getFriendlyName(): String {
        return "OPTION__GET_USER_OPTION"
    }
}
package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRS_Packet_Bolus_Get_Extended_Menu_Option_State(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_MENU_OPTION_STATE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        val extendedMenuOption = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.isExtendedInProgress = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 0x01
        aapsLogger.debug(LTag.PUMPCOMM, "extendedMenuOption: $extendedMenuOption")
    }

    override fun getFriendlyName(): String {
        return "BOLUS__GET_EXTENDED_MENU_OPTION_STATE"
    }
}
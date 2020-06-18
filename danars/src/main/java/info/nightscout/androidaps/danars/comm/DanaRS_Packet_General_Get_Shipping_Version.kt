package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import java.nio.charset.Charset
import javax.inject.Inject

class DanaRS_Packet_General_Get_Shipping_Version(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_GENERAL__GET_SHIPPING_VERSION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        danaPump.bleModel = data.copyOfRange(DATA_START, data.size).toString(Charset.forName("US-ASCII"))
        failed = false
        aapsLogger.debug(LTag.PUMPCOMM, "BLE Model: " + danaPump.bleModel)
    }

    override fun getFriendlyName(): String {
        return "GENERAL__GET_SHIPPING_VERSION"
    }
}
package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.dana.DanaPump
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketGeneralGetShippingInformation(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 18) {
            failed = true
            return
        } else failed = false
        danaPump.serialNumber = stringFromBuff(data, DATA_START, 10)
        danaPump.shippingCountry = asciiStringFromBuff(data, DATA_START + 10, 3)
        danaPump.shippingDate = dateFromBuff(data, DATA_START + 13)
        aapsLogger.debug(LTag.PUMPCOMM, "Serial number: " + danaPump.serialNumber)
        aapsLogger.debug(LTag.PUMPCOMM, "Shipping date: " + dateUtil.dateAndTimeString(danaPump.shippingDate))
        aapsLogger.debug(LTag.PUMPCOMM, "Shipping country: " + danaPump.shippingCountry)
    }

    override val friendlyName: String = "REVIEW__GET_SHIPPING_INFORMATION"
}
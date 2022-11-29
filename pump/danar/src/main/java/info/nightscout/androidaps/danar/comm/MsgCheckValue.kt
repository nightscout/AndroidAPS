package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.pump.dana.DanaPump
import info.nightscout.rx.logging.LTag


class MsgCheckValue(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0xF0F1)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.isNewPump = true
        aapsLogger.debug(LTag.PUMPCOMM, "New firmware confirmed")
        danaPump.hwModel = intFromBuff(bytes, 0, 1)
        danaPump.protocol = intFromBuff(bytes, 1, 1)
        danaPump.productCode = intFromBuff(bytes, 2, 1)
        if (danaPump.hwModel != DanaPump.EXPORT_MODEL) {
            danaRPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected")
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Model: " + String.format("%02X ", danaPump.hwModel))
        aapsLogger.debug(LTag.PUMPCOMM, "Protocol: " + String.format("%02X ", danaPump.protocol))
        aapsLogger.debug(LTag.PUMPCOMM, "Product Code: " + String.format("%02X ", danaPump.productCode))
    }
}
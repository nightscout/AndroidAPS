package info.nightscout.pump.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.pump.dana.DanaPump
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject

class DanaRSPacketGeneralGetPumpCheck(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_CHECK
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        if (data.size < 5) {
            failed = true
            return
        } else
            failed = false
        var dataIndex = DATA_START
        var dataSize = 1
        danaPump.hwModel = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.protocol = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.productCode = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        aapsLogger.debug(LTag.PUMPCOMM, "Model: " + String.format("%02X ", danaPump.hwModel))
        aapsLogger.debug(LTag.PUMPCOMM, "Protocol: " + String.format("%02X ", danaPump.protocol))
        aapsLogger.debug(LTag.PUMPCOMM, "Product Code: " + String.format("%02X ", danaPump.productCode))
        if (danaPump.productCode < 2) {
            uiInteraction.addNotification(Notification.UNSUPPORTED_FIRMWARE, rh.gs(info.nightscout.pump.dana.R.string.unsupportedfirmware), Notification.URGENT)
        }
    }

    override val friendlyName: String = "REVIEW__GET_PUMP_CHECK"
 }
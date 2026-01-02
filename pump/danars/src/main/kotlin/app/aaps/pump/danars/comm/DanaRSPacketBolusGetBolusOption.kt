package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketBolusGetBolusOption @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val danaPump: DanaPump,
    private val uiInteraction: UiInteraction
) : DanaRSPacket() {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_OPTION
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 1
        danaPump.isExtendedBolusEnabled = byteArrayToInt(getBytes(data, dataIndex, dataSize)) == 1
        dataIndex += dataSize
        dataSize = 1
        danaPump.bolusCalculationOption = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        danaPump.missedBolusConfig = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus01StartHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus01StartMin = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus01EndHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus01EndMin = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus02StartHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus02StartMin = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus02EndHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus02EndMin = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus03StartHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus03StartMin = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus03EndHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus03EndMin = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus04StartHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus04StartMin = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus04EndHour = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        dataIndex += dataSize
        dataSize = 1
        val missedBolus04EndMin = byteArrayToInt(getBytes(data, dataIndex, dataSize))
        if (!danaPump.isExtendedBolusEnabled) {
            uiInteraction.addNotification(Notification.EXTENDED_BOLUS_DISABLED, rh.gs(app.aaps.pump.dana.R.string.danar_enableextendedbolus), Notification.URGENT)
            failed = true
        } else {
            rxBus.send(EventDismissNotification(Notification.EXTENDED_BOLUS_DISABLED))
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Extended bolus enabled: " + danaPump.isExtendedBolusEnabled)
        aapsLogger.debug(LTag.PUMPCOMM, "Missed bolus config: " + danaPump.missedBolusConfig)
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus01StartHour: $missedBolus01StartHour")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus01StartMin: $missedBolus01StartMin")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus01EndHour: $missedBolus01EndHour")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus01EndMin: $missedBolus01EndMin")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus02StartHour: $missedBolus02StartHour")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus02StartMin: $missedBolus02StartMin")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus02EndHour: $missedBolus02EndHour")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus02EndMin: $missedBolus02EndMin")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus03StartHour: $missedBolus03StartHour")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus03StartMin: $missedBolus03StartMin")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus03EndHour: $missedBolus03EndHour")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus03EndMin: $missedBolus03EndMin")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus04StartHour: $missedBolus04StartHour")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus04StartMin: $missedBolus04StartMin")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus04EndHour: $missedBolus04EndHour")
        aapsLogger.debug(LTag.PUMPCOMM, "missedBolus04EndMin: $missedBolus04EndMin")
    }

    override val friendlyName: String = "BOLUS__GET_BOLUS_OPTION"
}
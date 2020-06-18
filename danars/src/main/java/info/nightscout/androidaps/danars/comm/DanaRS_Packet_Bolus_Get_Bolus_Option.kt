package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class DanaRS_Packet_Bolus_Get_Bolus_Option(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var danaPump: DanaPump

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
            val notification = Notification(Notification.EXTENDED_BOLUS_DISABLED, resourceHelper.gs(R.string.danar_enableextendedbolus), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
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

    override fun getFriendlyName(): String {
        return "BOLUS__GET_BOLUS_OPTION"
    }
}
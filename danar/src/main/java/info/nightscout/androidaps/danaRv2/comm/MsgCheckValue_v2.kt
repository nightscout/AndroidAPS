package info.nightscout.androidaps.danaRv2.comm

import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.danar.comm.MessageBase
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.ConfigBuilderInterface
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.resources.ResourceHelper

class MsgCheckValue_v2(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaPump: DanaPump,
    private val danaRPlugin: DanaRPlugin,
    private val danaRKoreanPlugin: DanaRKoreanPlugin,
    private val danaRv2Plugin: DanaRv2Plugin,
    private val configBuilderPlugin: ConfigBuilderInterface,
    private val commandQueue: CommandQueueProvider
) : MessageBase() {


    init {
        SetCommand(0xF0F1)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        danaPump.isNewPump = true
        aapsLogger.debug(LTag.PUMPCOMM, "New firmware confirmed")
        danaPump.hwModel = intFromBuff(bytes, 0, 1)
        danaPump.protocol = intFromBuff(bytes, 1, 1)
        danaPump.productCode = intFromBuff(bytes, 2, 1)
        if (danaPump.hwModel != info.nightscout.androidaps.dana.DanaPump.EXPORT_MODEL) {
            danaPump.reset()
            val notification = Notification(Notification.WRONG_DRIVER, resourceHelper.gs(R.string.pumpdrivercorrected), Notification.NORMAL)
            rxBus.send(EventNewNotification(notification))
            danaRPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to Korean DanaR")
            danaRKoreanPlugin.setPluginEnabled(PluginType.PUMP, true)
            danaRKoreanPlugin.setFragmentVisible(PluginType.PUMP, true)
            danaRPlugin.setPluginEnabled(PluginType.PUMP, false)
            danaRPlugin.setFragmentVisible(PluginType.PUMP, false)
            danaPump.reset() // mark not initialized
            //If profile coming from pump, switch it as well
            configBuilderPlugin.storeSettings("ChangingDanaRv2Driver")
            rxBus.send(EventRebuildTabs())
            commandQueue.readStatus("PumpDriverChange", null) // force new connection
            return
        }
        if (danaPump.protocol != 2) {
            danaPump.reset()
            val notification = Notification(Notification.WRONG_DRIVER, resourceHelper.gs(R.string.pumpdrivercorrected), Notification.NORMAL)
            rxBus.send(EventNewNotification(notification))
            danaRKoreanPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to non APS DanaR")
            danaRv2Plugin.setPluginEnabled(PluginType.PUMP, false)
            danaRv2Plugin.setFragmentVisible(PluginType.PUMP, false)
            danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
            danaRPlugin.setFragmentVisible(PluginType.PUMP, true)
            //If profile coming from pump, switch it as well
            configBuilderPlugin.storeSettings("ChangingDanaRv2Driver")
            rxBus.send(EventRebuildTabs())
            commandQueue.readStatus("PumpDriverChange", null) // force new connection
            return
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Model: " + String.format("%02X ", danaPump.hwModel))
        aapsLogger.debug(LTag.PUMPCOMM, "Protocol: " + String.format("%02X ", danaPump.protocol))
        aapsLogger.debug(LTag.PUMPCOMM, "Product Code: " + String.format("%02X ", danaPump.productCode))
    }
}
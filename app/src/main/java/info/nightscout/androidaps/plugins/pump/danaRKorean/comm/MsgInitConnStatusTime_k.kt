package info.nightscout.androidaps.plugins.pump.danaRKorean.comm

import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper

class MsgInitConnStatusTime_k(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaRPump: DanaRPump,
    private val danaRPlugin: DanaRPlugin,
    private val danaRKoreanPlugin: DanaRKoreanPlugin,
    private val configBuilderPlugin: ConfigBuilderPlugin,
    private val commandQueue: CommandQueueProvider,
    private val dateUtil: DateUtil
) : MessageBase() {

    init {
        SetCommand(0x0301)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 < 10) {
            val notification = Notification(Notification.WRONG_DRIVER, resourceHelper.gs(R.string.pumpdrivercorrected), Notification.NORMAL)
            rxBus.send(EventNewNotification(notification))
            danaRKoreanPlugin.disconnect("Wrong Model")
            aapsLogger.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to export DanaR")
            danaRKoreanPlugin.setPluginEnabled(PluginType.PUMP, false)
            danaRKoreanPlugin.setFragmentVisible(PluginType.PUMP, false)
            danaRPlugin.setPluginEnabled(PluginType.PUMP, true)
            danaRPlugin.setFragmentVisible(PluginType.PUMP, true)
            danaRPump.reset() // mark not initialized
            //If profile coming from pump, switch it as well
            configBuilderPlugin.storeSettings("ChangingKoreanDanaDriver")
            rxBus.send(EventRebuildTabs())
            commandQueue.readStatus("PumpDriverChange", null) // force new connection
            return
        }
        val time = dateTimeSecFromBuff(bytes, 0)
        val versionCode1 = intFromBuff(bytes, 6, 1)
        val versionCode2 = intFromBuff(bytes, 7, 1)
        val versionCode3 = intFromBuff(bytes, 8, 1)
        val versionCode4 = intFromBuff(bytes, 9, 1)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump time: " + dateUtil.dateAndTimeString(time))
        aapsLogger.debug(LTag.PUMPCOMM, "Version code1: $versionCode1")
        aapsLogger.debug(LTag.PUMPCOMM, "Version code2: $versionCode2")
        aapsLogger.debug(LTag.PUMPCOMM, "Version code3: $versionCode3")
        aapsLogger.debug(LTag.PUMPCOMM, "Version code4: $versionCode4")
    }
}
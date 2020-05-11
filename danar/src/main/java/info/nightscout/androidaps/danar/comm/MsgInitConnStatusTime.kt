package info.nightscout.androidaps.danar.comm

import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.ConfigBuilderInterface
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper

class MsgInitConnStatusTime(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaPump: DanaPump,
    private val danaRPlugin: DanaRPlugin,
    private val danaRKoreanPlugin: DanaRKoreanPlugin,
    private val configBuilderPlugin: ConfigBuilderInterface,
    private val commandQueue: CommandQueueProvider,
    private val dateUtil: DateUtil
) : MessageBase() {

    init {
        SetCommand(0x0301)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        if (bytes.size - 10 > 7) {
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
            configBuilderPlugin.storeSettings("ChangingDanaDriver")
            rxBus.send(EventRebuildTabs())
            commandQueue.readStatus("PumpDriverChange", null) // force new connection
            failed = false
            return
        } else {
            failed = true
        }
        val time = dateTimeSecFromBuff(bytes, 0)
        val versionCode = intFromBuff(bytes, 6, 1)
        aapsLogger.debug(LTag.PUMPCOMM, "Pump time: " + dateUtil.dateAndTimeString(time))
        aapsLogger.debug(LTag.PUMPCOMM, "Version code: $versionCode")
    }
}
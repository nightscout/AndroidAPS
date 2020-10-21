package info.nightscout.androidaps.danar.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.R
import info.nightscout.androidaps.events.EventRebuildTabs
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification

class MsgInitConnStatusTime(
    injector: HasAndroidInjector
) : MessageBase(injector) {

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
            configBuilder.storeSettings("ChangingDanaDriver")
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
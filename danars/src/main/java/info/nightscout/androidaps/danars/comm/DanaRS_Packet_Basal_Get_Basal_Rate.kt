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
import java.util.*
import javax.inject.Inject

class DanaRS_Packet_Basal_Get_Basal_Rate(
    injector: HasAndroidInjector
) : DanaRS_Packet(injector) {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var danaPump: DanaPump

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BASAL__GET_BASAL_RATE
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting basal rates")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 2
        danaPump.maxBasal = byteArrayToInt(getBytes(data, DATA_START, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 1
        danaPump.basalStep = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        danaPump.pumpProfiles = Array(4) { Array(48) { 0.0 } }
        var i = 0
        val size = 24
        while (i < size) {
            dataIndex += dataSize
            dataSize = 2
            danaPump.pumpProfiles!![danaPump.activeProfile][i] = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            i++
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Max basal: " + danaPump.maxBasal + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Basal step: " + danaPump.basalStep + " U")
        for (index in 0..23)
            aapsLogger.debug(LTag.PUMPCOMM, "Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + danaPump.pumpProfiles!![danaPump.activeProfile][index])
        if (danaPump.basalStep != 0.01) {
            failed = true
            val notification = Notification(Notification.WRONGBASALSTEP, resourceHelper.gs(R.string.danar_setbasalstep001), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
        } else {
            rxBus.send(EventDismissNotification(Notification.WRONGBASALSTEP))
        }
    }

    override fun getFriendlyName(): String {
        return "BASAL__GET_BASAL_RATE"
    }
}
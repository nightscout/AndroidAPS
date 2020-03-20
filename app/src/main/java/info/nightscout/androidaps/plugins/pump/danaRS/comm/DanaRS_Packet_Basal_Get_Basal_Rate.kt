package info.nightscout.androidaps.plugins.pump.danaRS.comm

import com.cozmo.danar.util.BleCommandUtil
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*

class DanaRS_Packet_Basal_Get_Basal_Rate(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val danaRPump: DanaRPump
) : DanaRS_Packet() {

    init {
        opCode = BleCommandUtil.DANAR_PACKET__OPCODE_BASAL__GET_BASAL_RATE
        aapsLogger.debug(LTag.PUMPCOMM, "Requesting basal rates")
    }

    override fun handleMessage(data: ByteArray) {
        var dataIndex = DATA_START
        var dataSize = 2
        danaRPump.maxBasal = byteArrayToInt(getBytes(data, DATA_START, dataSize)) / 100.0
        dataIndex += dataSize
        dataSize = 1
        danaRPump.basalStep = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
        danaRPump.pumpProfiles =  Array(4) {Array(48) {0.0} }
        var i = 0
        val size = 24
        while (i < size) {
            dataIndex += dataSize
            dataSize = 2
            danaRPump.pumpProfiles!![danaRPump.activeProfile][i] = byteArrayToInt(getBytes(data, dataIndex, dataSize)) / 100.0
            i++
        }
        aapsLogger.debug(LTag.PUMPCOMM, "Max basal: " + danaRPump.maxBasal + " U")
        aapsLogger.debug(LTag.PUMPCOMM, "Basal step: " + danaRPump.basalStep + " U")
        for (index in 0..23)
            aapsLogger.debug(LTag.PUMPCOMM, "Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + danaRPump.pumpProfiles!![danaRPump.activeProfile][index])
        if (danaRPump.basalStep != 0.01) {
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
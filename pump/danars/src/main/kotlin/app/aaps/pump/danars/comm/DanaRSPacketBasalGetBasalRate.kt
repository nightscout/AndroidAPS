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
import java.util.Locale
import javax.inject.Inject

class DanaRSPacketBasalGetBasalRate @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val uiInteraction: UiInteraction,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val danaPump: DanaPump
) : DanaRSPacket() {

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
            uiInteraction.addNotification(Notification.WRONG_BASAL_STEP, rh.gs(app.aaps.pump.dana.R.string.danar_setbasalstep001), Notification.URGENT)
        } else {
            rxBus.send(EventDismissNotification(Notification.WRONG_BASAL_STEP))
        }
    }

    override val friendlyName: String = "BASAL__GET_BASAL_RATE"
}
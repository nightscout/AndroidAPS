package info.nightscout.pump.danars.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danars.encryption.BleEncryption
import info.nightscout.pump.dana.DanaPump
import javax.inject.Inject
import kotlin.math.min

class DanaRSPacketNotifyDeliveryRateDisplay(
    injector: HasAndroidInjector
) : DanaRSPacket(injector) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var danaPump: DanaPump

    init {
        type = BleEncryption.DANAR_PACKET__TYPE_NOTIFY
        opCode = BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_RATE_DISPLAY
    }

    override fun handleMessage(data: ByteArray) {
        val deliveredInsulin = byteArrayToInt(getBytes(data, DATA_START, 2)) / 100.0
        danaPump.bolusProgressLastTimeStamp = System.currentTimeMillis()
        danaPump.bolusingTreatment?.insulin = deliveredInsulin
        val bolusingEvent = EventOverviewBolusProgress
        bolusingEvent.status = rh.gs(app.aaps.core.ui.R.string.bolus_delivering, deliveredInsulin)
        bolusingEvent.t = danaPump.bolusingTreatment
        bolusingEvent.percent = min((deliveredInsulin / danaPump.bolusAmountToBeDelivered * 100).toInt(), 100)
        failed = bolusingEvent.percent < 100
        rxBus.send(bolusingEvent)
        aapsLogger.debug(LTag.PUMPCOMM, "Delivered insulin so far: $deliveredInsulin")
    }

    override val friendlyName: String = "NOTIFY__DELIVERY_RATE_DISPLAY"
}
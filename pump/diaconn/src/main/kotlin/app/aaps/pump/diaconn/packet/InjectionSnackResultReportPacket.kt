package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.diaconn.DiaconnG8Pump
import dev.zacsweers.metro.Inject

/**
 * InjectionSnackResultReportPacket
 */
@Suppress("SpellCheckingInspection")
class InjectionSnackResultReportPacket(injector: MetroMemberInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var bolusProgressData: BolusProgressData

    init {
        msgType = 0xe4.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackResultReportPacket init ")
    }

    override fun handleMessage(data: ByteArray) {
        val defectCheck = defect(data)
        if (defectCheck != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "InjectionSnackResultReportPacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result = getByteToInt(bufferData)
        val setHundredths = getShortToInt(bufferData)
        val givenHundredths = getShortToInt(bufferData)
        val bolusAmountToBeDelivered = setHundredths / 100.0
        val deliveredBolusAmount = givenHundredths / 100.0

        diaconnG8Pump.lastBolusAmount = deliveredBolusAmount
        diaconnG8Pump.lastBolusTime = dateUtil.now()
        bolusProgressData.updateProgress(delivered = PumpInsulin(deliveredBolusAmount))

        // Result 1 is a stop during delivery.
        if (result == 1) {
            diaconnG8Pump.bolusStopped = true
        }
        // From the amounts, not from the result: the result of a bolus the pump stopped itself, as at an occlusion, was
        // never seen on a real pump.
        diaconnG8Pump.bolusGivenInFull = givenHundredths >= setHundredths
        // Set last: the bolus waits for it, and then reads the fields above.
        diaconnG8Pump.bolusDone = true

        aapsLogger.debug(LTag.PUMPCOMM, "Result --> $result")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusAmountToBeDelivered --> $bolusAmountToBeDelivered")
        aapsLogger.debug(LTag.PUMPCOMM, "lastBolusAmount --> ${diaconnG8Pump.lastBolusAmount}")
        aapsLogger.debug(LTag.PUMPCOMM, "lastBolusTime --> ${diaconnG8Pump.lastBolusTime}")
        aapsLogger.debug(LTag.PUMPCOMM, "delivered --> ${bolusProgressData.state.value?.delivered?.cU ?: 0.0}")
        aapsLogger.debug(LTag.PUMPCOMM, "bolusDone --> ${diaconnG8Pump.bolusDone}")
    }

    override val friendlyName = "PUMP_INJECTION_SNACK_REPORT"
}
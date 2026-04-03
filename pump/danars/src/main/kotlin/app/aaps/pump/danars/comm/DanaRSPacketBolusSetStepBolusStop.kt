package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

open class DanaRSPacketBolusSetStepBolusStop @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val bolusProgressData: BolusProgressData,
    private val rh: ResourceHelper,
    private val danaPump: DanaPump
) : DanaRSPacket() {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_STOP
    }

    override fun handleMessage(data: ByteArray) {
        val result = intFromBuff(data, 0, 1)
        @Suppress("LiftReturnOrAssignment")
        if (result == 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "Result OK")
            failed = false
        } else {
            aapsLogger.error("Result Error: $result")
            failed = true
        }
        danaPump.bolusStopped = true
        if (!danaPump.bolusStopForced) {
            // delivery ended without user intervention
            val insulin = bolusProgressData.state.value?.insulin ?: 0.0
            bolusProgressData.updateProgress(100, rh.gs(app.aaps.core.interfaces.R.string.bolus_delivered_successfully, insulin), insulin)
        } else {
            val currentPercent = bolusProgressData.state.value?.percent ?: 0
            bolusProgressData.updateProgress(currentPercent, rh.gs(app.aaps.pump.dana.R.string.overview_bolusprogress_stoped), bolusProgressData.state.value?.delivered ?: 0.0)
        }
    }

    override val friendlyName: String = "BOLUS__SET_STEP_BOLUS_STOP"
}
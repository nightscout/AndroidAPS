package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import dagger.android.HasAndroidInjector

class MsgBolusStop(
    injector: HasAndroidInjector
) : MessageBase(injector) {

    init {
        setCommand(0x0101)
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override fun handleMessage(bytes: ByteArray) {
        aapsLogger.debug(LTag.PUMPCOMM, "Message received")
        danaPump.bolusStopped = true
        if (!danaPump.bolusStopForced) {
            val insulin = bolusProgressData.state.value?.insulin ?: 0.0
            bolusProgressData.updateProgress(100, rh.gs(app.aaps.core.interfaces.R.string.bolus_delivered_successfully, insulin), insulin)
        } else {
            val currentPercent = bolusProgressData.state.value?.percent ?: 0
            bolusProgressData.updateProgress(currentPercent, rh.gs(app.aaps.pump.dana.R.string.overview_bolusprogress_stoped), bolusProgressData.state.value?.delivered ?: 0.0)
        }
    }
}

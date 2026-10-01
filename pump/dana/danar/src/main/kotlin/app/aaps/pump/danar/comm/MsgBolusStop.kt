package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.interfaces.di.MetroMemberInjector

class MsgBolusStop(
    injector: MetroMemberInjector
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
            bolusProgressData.updateProgress(percent = 100)
        } else {
            val currentPercent = bolusProgressData.state.value?.percent ?: 0
            bolusProgressData.updateProgress(currentPercent, TextRef.AndroidRes(app.aaps.pump.dana.R.string.overview_bolusprogress_stoped))
        }
    }
}

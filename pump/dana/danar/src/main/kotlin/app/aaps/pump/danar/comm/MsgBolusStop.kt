package app.aaps.pump.danar.comm

import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.TextRef

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
        // The pump sends this at the end of a bolus and as the answer to a stop. A stop the driver gives up on sets
        // only bolusStopped, so bolusDone tells that the pump itself ended the bolus.
        danaPump.bolusDone = true
        if (!danaPump.bolusStopForced) {
            bolusProgressData.updateProgress(percent = 100)
        } else {
            val currentPercent = bolusProgressData.state.value?.percent ?: 0
            bolusProgressData.updateProgress(currentPercent, TextRef.AndroidRes(app.aaps.pump.dana.R.string.overview_bolusprogress_stoped))
        }
    }
}

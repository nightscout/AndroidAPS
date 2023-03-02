package info.nightscout.pump.medtrum.ui.viewmodel

import info.nightscout.pump.medtrum.ui.MedtrumBaseNavigator
import info.nightscout.pump.medtrum.ui.viewmodel.BaseViewModel
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class MedtrumOverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger
) : BaseViewModel<MedtrumBaseNavigator>() {

    val isPatchActivated : Boolean
        get() = false // TODO
    val isPatchConnected: Boolean
        get() = false // TODO
    val bleStatus : String
        get() ="" //TODO

    init {
        // TODO
    }

    fun onClickActivation(){
        aapsLogger.debug(LTag.PUMP, "Start Patch clicked!")
        // TODO
    }

    fun onClickDeactivation(){
        aapsLogger.debug(LTag.PUMP, "Stop Patch clicked!")
        // TODO
    }
}
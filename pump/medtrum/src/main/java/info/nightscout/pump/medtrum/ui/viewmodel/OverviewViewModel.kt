package info.nightscout.pump.medtrum.ui.viewmodel

import info.nightscout.pump.medtrum.ui.BaseNavigator
import info.nightscout.pump.medtrum.ui.viewmodel.BaseViewModel
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class OverviewViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger
) : BaseViewModel<BaseNavigator>() {

    val isPatchActivated : Boolean
        get() = false // TODO
    val isPatchConnected: Boolean
        get() = false // TODO

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
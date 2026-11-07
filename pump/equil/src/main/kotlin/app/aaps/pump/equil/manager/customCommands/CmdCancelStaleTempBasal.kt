package app.aaps.pump.equil.manager.customCommands

import app.aaps.core.interfaces.queue.CustomCommand

/**
 * After a resume: cancel the temp basal the pump runs again from before the suspend, unless AAPS has
 * set a temp basal again by then. See `EquilPumpPlugin.cancelStaleTempBasal`.
 */
class CmdCancelStaleTempBasal : CustomCommand {

    override val statusDescription: String = "CmdCancelStaleTempBasal"
}

package app.aaps.pump.omnipod.dash.driver.pod.command.base.builder

import app.aaps.pump.omnipod.dash.driver.pod.command.base.Command

interface CommandBuilder<R : Command> {

    fun build(): R
}

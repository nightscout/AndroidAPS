package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command

interface CommandBuilder<R : Command> {

    fun build(): R
}

package app.aaps.pump.omnipod.common.bledriver.pod.command.base.builder

import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command

interface CommandBuilder<R : Command> {

    fun build(): R
}

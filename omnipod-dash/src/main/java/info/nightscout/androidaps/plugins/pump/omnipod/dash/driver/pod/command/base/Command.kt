package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable

interface Command : Encodable {

    val commandType: CommandType
}
package app.aaps.pump.omnipod.dash.driver.pod.command.base

import app.aaps.pump.omnipod.dash.driver.pod.definition.Encodable
import java.io.Serializable

interface Command : Encodable, Serializable {

    val commandType: CommandType
    val sequenceNumber: Short
}

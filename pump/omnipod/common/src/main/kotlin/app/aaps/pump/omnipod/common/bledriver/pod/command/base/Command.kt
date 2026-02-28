package app.aaps.pump.omnipod.common.bledriver.pod.command.base

import app.aaps.pump.omnipod.common.bledriver.pod.definition.Encodable
import java.io.Serializable

interface Command : Encodable, Serializable {

    val commandType: CommandType
    val sequenceNumber: Short
}

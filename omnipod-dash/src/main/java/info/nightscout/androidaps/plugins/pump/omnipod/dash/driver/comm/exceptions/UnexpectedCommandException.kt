package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command.BleCommand
import java.lang.Exception

class UnexpectedCommandException(val cmd: BleCommand): Exception("Unexpected command: ${cmd}") {
}
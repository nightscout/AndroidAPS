package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.status.ConnectionStatus
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response

interface OmnipodDashBleManager {

    fun sendCommand(cmd: Command): Response

    fun getStatus(): ConnectionStatus

    fun connect()

    fun disconnect()

    fun getPodId(): Id
}
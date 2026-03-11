package app.aaps.pump.apex.interfaces

import app.aaps.pump.apex.connectivity.commands.pump.PumpCommand

interface ApexBluetoothCallback {
    fun onConnect()
    fun onDisconnect()
    fun onPumpCommand(command: PumpCommand)
}

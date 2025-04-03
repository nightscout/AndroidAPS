package app.aaps.pump.apex.connectivity.commands.pump

class StatusV2(command: PumpCommand): PumpObjectModel() {
    /** Pump battery voltage */
    val batteryVoltage = command.objectData[4].toUByte().toInt() / 100.0
}

package app.aaps.plugins.eversense.enums

enum class EversenseE3Memory(private val address: Long) {
    BatteryPercentage(0x0000_0406),
    CalibrationReadiness(0x0000_0137),
    IsOneCalibration(0x0000_0496),
    SensorInsertionDate(0x0000_0890),
    CalibrationPhase(0x0000_089C),
    SensorInsertionTime(0x0000_0892),
    LastCalibrationDate(0x0000_08A3),
    LastCalibrationTime(0x0000_08A5),
    SensorFieldCurrentRaw(0x0000_049D),
    TransmitterSoftwareVersion(0x0000_000A),
    TransmitterSoftwareVersionExt(0x0000_00A2),
    MmaFeatures(0x0000_040C),
    AppVersion(0x0000_0B4B),
    BleDisconnect(0x0000_08B2);

    fun getRequestData(): ByteArray {
        // Official app sends address as little-endian (LSB first) with trailing 0x00.
        // Confirmed from logcat: official app sends [96 04 00] for address 0x0496.
        // Format: [addrLSB][addrMSB][0x00]
        return byteArrayOf(
            this.address.toByte(),             // addr LSB
            (this.address shr 8).toByte(),    // addr MSB
            0x00.toByte(),                    // trailing zero
        )
    }
}

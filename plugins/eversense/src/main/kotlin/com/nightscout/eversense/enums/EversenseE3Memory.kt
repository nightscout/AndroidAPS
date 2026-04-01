package com.nightscout.eversense.enums

enum class EversenseE3Memory(private val address: Long) {
    BatteryPercentage(0x0000_0406),
    CalibrationReadiness(0x0000_040A),
    NextCalibrationDate(0x0000_0470),
    NextCalibrationTime(0x0000_0472),
    IsOneCalibration(0x0000_0496),
    SensorInsertionDate(0x0000_0890),
    CalibrationPhase(0x0000_089C),
    SensorInsertionTime(0x0000_0892),
    LastCalibrationDate(0x0000_08A3),
    LastCalibrationTime(0x0000_08A5),
    VibrateMode(0x0000_0902),
    HighGlucoseAlarmEnabled(0x0000_1029),
    HighGlucoseAlarmThreshold(0x0000_110C),
    LowGlucoseAlarmThreshold(0x0000_110A),
    PredictiveAlert(0x0000_1020),
    PredictiveLowTime(0x0000_1021),
    PredictiveHighTime(0x0000_1022),
    PredictiveLowAlert(0x0000_1027),
    PredictiveHighAlert(0x0000_1028),
    PredictiveLowTarget(0x0000_1102),
    PredictiveHighTarget(0x0000_1104),
    RateAlert(0x0000_1010),
    RateFallingAlert(0x0000_1025),
    RateRisingAlert(0x0000_1026),
    RateFallingThreshold(0x0000_1011),
    RateRisingThreshold(0x0000_1012),
    SensorFieldCurrentRaw(0x0000_0874),
    TransmitterSoftwareVersion(0x0000_000A),
    TransmitterSoftwareVersionExt(0x0000_00A2),
    MmaFeatures(0x0000_0137),
    AppVersion(0x0000_0B4B),
    BleDisconnect(0x0000_08B2),
    HighGlucoseAlarmRepeatIntervalDay(0x0000_1033),
    LowGlucoseAlarmRepeatIntervalDay(0x0000_1032),
    HighGlucoseAlarmRepeatIntervalNight(0x0000_110F),
    LowGlucoseAlarmRepeatIntervalNight(0x0000_110E);

    fun getRequestData(): ByteArray {
        return byteArrayOf(
            this.address.toByte(),
            (this.address shr 8).toByte(),
            (this.address shr 16).toByte(),
        )
    }
}
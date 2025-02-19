package app.aaps.pump.apex.connectivity.commands.pump

import app.aaps.pump.apex.connectivity.commands.CommandId

enum class PumpObject(
    val commandId: CommandId = CommandId.GetValue,
    val objectId: Int? = null,
    val valueId: Int? = null,
) {
    Heartbeat(commandId = CommandId.Heartbeat),
    CommandResponse(commandId = CommandId.SetValue),
    StatusV1(objectId = 0x01, valueId = 0x00),
    WizardStatus(objectId = 0x01, valueId = 0x07),
    BasalProfile(objectId = 0x08),
    AlarmEntry(objectId = 0x14),
    TDDEntry(objectId = 0x5c),
    BolusEntry(objectId = 0x80),
    FirmwareEntry(objectId = 0x00, valueId = 0x31);

    companion object {
        fun findObject(commandId: CommandId, objectId: Int, objectData: ByteArray): PumpObject? {
            for (e in entries) {
                if (commandId != e.commandId) continue
                if (e.objectId == null) return e
                if (objectId != e.objectId) continue
                if (e.valueId == null) return e
                if (objectData[0].toInt() != e.valueId) continue
                return e
            }
            return null
        }
    }
}

abstract class PumpObjectModel

enum class BatteryLevel(val raw: Byte, val approximatePercentage: Int) {
    Dead(0, 0),
    Low(1, 25),
    Medium(2, 50),
    High(3, 75),
    Full(4, 100),
}

enum class Language(val raw: Byte) {
    Russian(0),
    English(1),
}

enum class ScreenBrightness(val raw: Byte) {
    P10(0),
    P30(1),
    P50(2),
    P60(3),
    P80(4),
    P100(5),
}

enum class AlarmType(val raw: Byte) {
    Sound(0),
    Vibration(1),
    VibrationAndSound(2),
}

enum class AlarmLength(val raw: Byte) {
    Long(0),
    Medium(1),
    Short(2),
}

enum class BolusDeliverySpeed(val raw: Byte) {
    Standard(0),
    Low(1),
}

enum class Alarm(val raw: Int) {
    Unknown(0xBADC0DE),
    NoError(0x100),
    LowBattery(0x101),
    CheckGlucose(0x102),
    ButtonError(0x103),
    LowReservoir(0x104),
    DeadBattery(0x105),
    BatteryError(0x106),
    TimeError(0x107),
    NoDelivery(0x108),
    ResetError(0x109),
    CommunicationError(0x10a),
    MotorError(0x10b),
    EncoderError(0x10c),
    NoDosage(0x10d),
    TDDLimitTriggered(0x10e),
    NoReservoir(0x10f),
    ScreenError(0x201),
    FRAMError(0x202),
    TimeAnomalyError(0x203),
    ClockError(0x204),
    Reserved1(0x205),
    MotorAbnormal(0x206),
    MotorPowerAbnormal(0x207),
    BolusOrBasalDoseAbnormal(0x208),
    ConnectionAnomaly(0x209),
    Reserved2(0x20a),
    PressureAbnormal(0x20b),
}

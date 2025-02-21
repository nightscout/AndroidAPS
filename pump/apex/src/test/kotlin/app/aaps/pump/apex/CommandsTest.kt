package app.aaps.pump.apex

import app.aaps.pump.apex.connectivity.commands.device.Bolus
import app.aaps.pump.apex.connectivity.commands.pump.AlarmType
import app.aaps.pump.apex.connectivity.commands.pump.BasalProfile
import app.aaps.pump.apex.connectivity.commands.pump.BolusDeliverySpeed
import app.aaps.pump.apex.connectivity.commands.pump.BolusEntry
import app.aaps.pump.apex.connectivity.commands.pump.CommandResponse
import app.aaps.pump.apex.connectivity.commands.pump.PumpCommand
import app.aaps.pump.apex.connectivity.commands.pump.PumpObject
import app.aaps.pump.apex.connectivity.commands.pump.ScreenBrightness
import app.aaps.pump.apex.connectivity.commands.pump.StatusV1
import app.aaps.pump.apex.connectivity.commands.pump.TDDEntry
import app.aaps.pump.apex.connectivity.commands.pump.Version
import app.aaps.pump.apex.interfaces.ApexDeviceInfo
import app.aaps.shared.tests.TestBase
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

class CommandsTest : TestBase() {
    private val info = object : ApexDeviceInfo {
        override var serialNumber = "12345678"
    }

    private fun deserialiseHead(expectedType: PumpObject, data: ByteArray): PumpCommand {
        val command = PumpCommand(data)
        assert(command.isCompleteCommand())
        assert(command.verify())
        assert(PumpObject.findObject(command.id!!, command.objectData) == expectedType)
        println("Processed $command")
        return command
    }

    @Test
    fun pump_commandResponse() {
        val command = deserialiseHead(PumpObject.CommandResponse, ubyteArrayOf(0xaau, 0x0au, 0x00u, 0xa1u, 0xa0u, 0xaau, 0x0cu, 0x00u, 0xdau, 0xf5u).toByteArray())
        val data = CommandResponse(command)
        assert(data.code == CommandResponse.Code.StandardBolusProgress)
        assert(data.dose == 12)

        assert(command.trailing == null)
    }

    @Test
    fun pump_bolusEntry() {
        val command = deserialiseHead(PumpObject.BolusEntry, ubyteArrayOf(0xaau, 0x16u, 0x80u, 0xa3u, 0x21u, 0x00u, 0x25u, 0x01u, 0x25u, 0x17u, 0x52u, 0x59u, 0x09u, 0x00u, 0x09u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x78u).toByteArray())
        val data = BolusEntry(command)
        assert(data.dateTime == DateTime(2025, 1, 25, 17, 52, 59))
        assert(data.extendedDose == 0)
        assert(data.standardDose == 9)

        assert(command.trailing == null)
    }

    @Test
    fun pump_tddEntry() {
        val command = deserialiseHead(PumpObject.TDDEntry, ubyteArrayOf(0xaau, 0x12u, 0x5cu, 0xa3u, 0x06u, 0x00u, 0x88u, 0x00u, 0x56u, 0x01u, 0x0cu, 0x00u, 0x25u, 0x01u, 0x26u, 0x00u, 0x50u, 0xa0u, 0xaau, 0x12u, 0x5cu, 0xa3u, 0x06u, 0x00u, 0x88u, 0x00u, 0x56u, 0x01u, 0x0cu, 0x00u, 0x25u, 0x01u, 0x26u, 0x00u, 0x50u, 0xa0u).toByteArray())
        val data = TDDEntry(command)
        assert(data.dateTime == DateTime(2025, 1, 26, 0, 0))
        assert(data.bolus == 136) { data.bolus }
        assert(data.basal == 342) { data.basal }
        assert(data.temporaryBasal == 12) { data.temporaryBasal }

        val trailing = command.trailing
        assert(trailing != null)
        assert(trailing!!.isCompleteCommand())
        assert(trailing.verify())
        assert(PumpObject.findObject(trailing.id!!, trailing.objectData) == PumpObject.TDDEntry)
    }

    @Test
    fun pump_basalPattern() {
        val command = deserialiseHead(PumpObject.BasalProfile, ubyteArrayOf(0xaau, 0x68u, 0x08u, 0xa3u, 0x08u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x27u, 0x00u, 0x27u, 0x00u, 0x27u, 0x00u, 0x27u, 0x00u, 0x28u, 0x00u, 0x28u, 0x00u, 0x28u, 0x00u, 0x28u, 0x00u, 0x26u, 0x00u, 0x26u, 0x00u, 0x26u, 0x00u, 0x26u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x23u, 0x00u, 0x23u, 0x00u, 0x22u, 0x00u, 0x22u, 0x00u, 0x22u, 0x00u, 0x22u, 0x00u, 0x22u, 0x00u, 0x22u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x24u, 0x00u, 0x40u, 0x6au).toByteArray())
        val data = BasalProfile(command)
        val rates = listOf(
            36, 36,
            39, 39,
            39, 39,
            40, 40,
            40, 40,
            38, 38,
            38, 38,
            36, 36,
            35, 35,
            34, 34,
            34, 34,
            34, 34,
            36, 36,
            36, 36,
            36, 36,
            36, 36,
            36, 36,
            36, 36,
            36, 36,
            36, 36,
            36, 36,
            36, 36,
            36, 36,
            36, 36,
        )
        assert(data.rates == rates)
        assert(data.index == 0)
    }

    @Test
    fun pump_statusV1() {
        val command = deserialiseHead(PumpObject.StatusV1, ubyteArrayOf(0xaau, 0x60u, 0x01u, 0xa3u, 0x00u, 0xaau, 0x04u, 0x01u, 0x00u, 0x00u, 0x06u, 0x01u, 0x00u, 0x01u, 0x14u, 0x04u, 0x00u, 0x00u, 0x00u, 0x00u, 0x2cu, 0x01u, 0xb7u, 0x05u, 0x00u, 0x00u, 0x96u, 0x00u, 0x00u, 0x00u, 0xa0u, 0x00u, 0x0cu, 0x03u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x19u, 0x01u, 0x1cu, 0x15u, 0x0eu, 0x00u, 0x00u, 0x00u, 0x15u, 0x35u, 0x01u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x24u, 0x00u, 0x0du, 0x0eu, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0xc5u, 0xb3u).toByteArray())
        val data = StatusV1(command)
        assert(data.batteryLevel!!.approximatePercentage == 100)
        assert(data.alarmType == AlarmType.Vibration)
        assert(data.deliverySpeed == BolusDeliverySpeed.Standard)
        assert(data.brightness == ScreenBrightness.P10)
        assert(data.keyboardLockEnabled)
        assert(!data.autoSuspendEnabled)
        assert(data.autoSuspendDuration == 1)
        assert(data.lowReservoirThreshold == 20)
        assert(data.lowReservoirTimeLeftThreshold == 4)
        assert(!data.totalDailyDoseLimitEnabled)
        assert(data.screenTimeout == 300)
        assert(data.currentBasalRate == 36)
    }

    @Test
    fun pump_heartbeat() {
        deserialiseHead(PumpObject.Heartbeat, ubyteArrayOf(0xaau, 0x06u, 0x00u, 0xa5u, 0x01u, 0x00u, 0x81u, 0xa2u).toByteArray())
    }

    @Test
    fun pump_version() {
        val command = deserialiseHead(PumpObject.FirmwareEntry, ubyteArrayOf(0xaau, 0x10u, 0x00u, 0xa3u, 0x31u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x06u, 0x19u, 0x04u, 0x0au, 0x30u, 0xcfu).toByteArray())
        val data = Version(command)

        assert(data.firmwareMajor == 6)
        assert(data.firmwareMinor == 25)
        assert(data.protocolMajor == 4)
        assert(data.protocolMinor == 10)
    }

    @Test
    fun device_bolus() {
        val expected = ubyteArrayOf(0x35u, 0x17u, 0x00u, 0xa1u, 0x12u, 0xaau, 0x41u, 0x50u, 0x45u, 0x58u, 0x31u, 0x32u, 0x33u, 0x34u, 0x35u, 0x36u, 0x37u, 0x38u, 0x3cu, 0x00u, 0x00u, 0x6fu, 0x65u).toByteArray()
        val command = Bolus(info, 60)
        assert(expected.contentEquals(command.serialize()))
    }
}

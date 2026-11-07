package app.aaps.pump.omnipod.common.bledriver.pod.command

import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertConfiguration
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertTrigger
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BeepRepetitionType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BeepType
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import app.aaps.pump.omnipod.common.bledriver.pod.util.AlertUtil
import app.aaps.pump.omnipod.common.bledriver.pod.util.MessageUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.EnumSet

/**
 * Byte-level encoding checks for a handful of read-only / non-dosing commands, each
 * verified against the real, production [MessageUtil.createCrc] rather than a
 * re-implementation of it. These commands ([GetVersionCommand], [GetStatusCommand],
 * [SilenceAlertsCommand]) are built on [app.aaps.pump.omnipod.common.bledriver.pod.command
 * .base.HeaderEnabledCommand] / [app.aaps.pump.omnipod.common.bledriver.pod.command.base
 * .NonceEnabledCommand], which have no pod-type-specific assumptions - these tests exist
 * to pin that down with concrete byte checks, not just an architectural argument.
 *
 * Deliberately excludes anything with dosing-affecting logic (bolus, basal, temp basal) -
 * that's out of scope for this test's purpose.
 */
class CommandEncodingTest : TestBase() {


    @Test
    fun `GetVersionCommand encodes to the documented 14-byte layout`() {
        val uniqueId = 0x12345678
        val sequenceNumber: Short = 5
        val cmd = GetVersionCommand.Builder()
            .setUniqueId(uniqueId)
            .setSequenceNumber(sequenceNumber)
            .build()

        val encoded = cmd.encoded

        assertThat(encoded.size).isEqualTo(14)

        val headerUniqueId = readInt(encoded, 0)
        assertThat(headerUniqueId).isEqualTo(uniqueId)

        val bodyUniqueId = readInt(encoded, 8)
        assertThat(bodyUniqueId).isEqualTo(uniqueId)

        assertCrcMatches(encoded, crcOffset = 12)
    }

    @Test
    fun `GetVersionCommand with different sequence numbers produces different encodings`() {
        val uniqueId = 0x12345678
        val cmd1 = GetVersionCommand.Builder().setUniqueId(uniqueId).setSequenceNumber(1).build()
        val cmd2 = GetVersionCommand.Builder().setUniqueId(uniqueId).setSequenceNumber(2).build()

        assertThat(cmd1.encoded).isNotEqualTo(cmd2.encoded)
    }


    @Test
    fun `GetStatusCommand encodes to the documented 11-byte layout`() {
        val uniqueId = 0x0A0B0C0D
        val sequenceNumber: Short = 2

        val cmd = GetStatusCommand.Builder()
            .setUniqueId(uniqueId)
            .setSequenceNumber(sequenceNumber)
            .setStatusResponseType(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)
            .build()

        val encoded = cmd.encoded

        assertThat(encoded.size).isEqualTo(11)
        assertThat(encoded[8]).isEqualTo(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE.value)

        assertCrcMatches(encoded, crcOffset = 9)
    }

    @Test
    fun `GetStatusCommand reflects a different requested status page in its encoding`() {
        val uniqueId = 0x0A0B0C0D
        val sequenceNumber: Short = 2

        val defaultPage = GetStatusCommand.Builder()
            .setUniqueId(uniqueId).setSequenceNumber(sequenceNumber)
            .setStatusResponseType(ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE)
            .build()
        val alarmPage = GetStatusCommand.Builder()
            .setUniqueId(uniqueId).setSequenceNumber(sequenceNumber)
            .setStatusResponseType(ResponseType.StatusResponseType.ALARM_STATUS)
            .build()

        assertThat(defaultPage.encoded).isNotEqualTo(alarmPage.encoded)
        assertThat(alarmPage.encoded[8]).isEqualTo(ResponseType.StatusResponseType.ALARM_STATUS.value)
    }


    @Test
    fun `SilenceAlertsCommand encodes to the documented 15-byte layout`() {
        val uniqueId = 0x2233AABB.toInt()
        val sequenceNumber: Short = 9
        val nonce = 0x0A0B0C0D
        val alertTypes = EnumSet.of(AlertType.LOW_RESERVOIR, AlertType.SUSPEND_ENDED)

        val cmd = SilenceAlertsCommand.Builder()
            .setUniqueId(uniqueId)
            .setSequenceNumber(sequenceNumber)
            .setNonce(nonce)
            .setAlertTypes(alertTypes)
            .build()

        val encoded = cmd.encoded

        assertThat(encoded.size).isEqualTo(15)

        val encodedNonce = readInt(encoded, 8)
        assertThat(encodedNonce).isEqualTo(nonce)

        assertCrcMatches(encoded, crcOffset = 13)
    }

    @Test
    fun `SilenceAlertsCommand alert byte round-trips through AlertUtil`() {
        val alertTypes = EnumSet.of(AlertType.LOW_RESERVOIR, AlertType.SUSPEND_ENDED)
        val cmd = SilenceAlertsCommand.Builder()
            .setUniqueId(0x2233AABB.toInt())
            .setSequenceNumber(9)
            .setNonce(0x0A0B0C0D)
            .setAlertTypes(alertTypes)
            .build()

        val decoded = AlertUtil.decodeAlertSet(cmd.encoded[12])

        assertThat(decoded).isEqualTo(alertTypes)
    }

    @Test
    fun `SilenceAlertsCommand with an empty alert set encodes to zero`() {
        val cmd = SilenceAlertsCommand.Builder()
            .setUniqueId(0x2233AABB.toInt())
            .setSequenceNumber(9)
            .setNonce(0x0A0B0C0D)
            .setAlertTypes(EnumSet.noneOf(AlertType::class.java))
            .build()

        assertThat(cmd.encoded[12]).isEqualTo(0.toByte())
        assertThat(AlertUtil.decodeAlertSet(cmd.encoded[12])).isEmpty()
    }


    @Test
    fun `DeactivateCommand encodes to the documented 14-byte layout`() {
        val uniqueId = 0x33445566.toInt()
        val sequenceNumber: Short = 3
        val nonce = 0x0F0E0D0C

        val cmd = DeactivateCommand.Builder()
            .setUniqueId(uniqueId)
            .setSequenceNumber(sequenceNumber)
            .setNonce(nonce)
            .build()

        val encoded = cmd.encoded

        assertThat(encoded.size).isEqualTo(14)
        assertThat(encoded[6]).isEqualTo(0x1c.toByte())

        val encodedNonce = readInt(encoded, 8)
        assertThat(encodedNonce).isEqualTo(nonce)

        assertCrcMatches(encoded, crcOffset = 12)
    }

    @Test
    fun `DeactivateCommand with different nonces produces different encodings`() {
        val uniqueId = 0x33445566.toInt()
        val cmd1 = DeactivateCommand.Builder().setUniqueId(uniqueId).setSequenceNumber(1).setNonce(100).build()
        val cmd2 = DeactivateCommand.Builder().setUniqueId(uniqueId).setSequenceNumber(1).setNonce(101).build()

        assertThat(cmd1.encoded).isNotEqualTo(cmd2.encoded)
    }


    @Test
    fun `StopDeliveryCommand encodes ALL plus LONG_SINGLE_BEEP to the documented 15-byte layout`() {
        val uniqueId = 0x11223344.toInt()
        val sequenceNumber: Short = 4
        val nonce = 0x0A0B0C0D

        val cmd = StopDeliveryCommand.Builder()
            .setUniqueId(uniqueId)
            .setSequenceNumber(sequenceNumber)
            .setNonce(nonce)
            .setDeliveryType(StopDeliveryCommand.DeliveryType.ALL)
            .setBeepType(BeepType.LONG_SINGLE_BEEP)
            .build()

        val encoded = cmd.encoded

        assertThat(encoded.size).isEqualTo(15)
        assertThat(encoded[6]).isEqualTo(0x1f.toByte())

        val encodedNonce = readInt(encoded, 8)
        assertThat(encodedNonce).isEqualTo(nonce)

        assertThat(encoded[12]).isEqualTo(0x67.toByte())

        assertCrcMatches(encoded, crcOffset = 13)
    }

    @Test
    fun `StopDeliveryCommand DeliveryType BASAL produces a different, correctly-masked encoding`() {
        val uniqueId = 0x11223344.toInt()
        val nonce = 0x0A0B0C0D

        val allCmd = StopDeliveryCommand.Builder()
            .setUniqueId(uniqueId).setSequenceNumber(4).setNonce(nonce)
            .setDeliveryType(StopDeliveryCommand.DeliveryType.ALL).setBeepType(BeepType.LONG_SINGLE_BEEP)
            .build()
        val basalOnlyCmd = StopDeliveryCommand.Builder()
            .setUniqueId(uniqueId).setSequenceNumber(4).setNonce(nonce)
            .setDeliveryType(StopDeliveryCommand.DeliveryType.BASAL).setBeepType(BeepType.SILENT)
            .build()

        assertThat(basalOnlyCmd.encoded[12]).isEqualTo(0x01.toByte())
        assertThat(allCmd.encoded).isNotEqualTo(basalOnlyCmd.encoded)
    }


    @Test
    fun `ProgramAlertsCommand encodes a single AlertConfiguration to the documented 20-byte layout`() {
        val uniqueId = 0x22334455.toInt()
        val sequenceNumber: Short = 6
        val nonce = 0x01020304

        val alertConfig = AlertConfiguration(
            type = AlertType.SUSPEND_ENDED,
            enabled = true,
            durationInMinutes = 0,
            autoOff = false,
            trigger = AlertTrigger.TimerTrigger(20),
            beepType = BeepType.FOUR_TIMES_BIP_BEEP,
            beepRepetition = BeepRepetitionType.EVERY_MINUTE_AND_EVERY_15_MIN
        )

        val cmd = ProgramAlertsCommand.Builder()
            .setUniqueId(uniqueId)
            .setSequenceNumber(sequenceNumber)
            .setNonce(nonce)
            .setAlertConfigurations(listOf(alertConfig))
            .build()

        val encoded = cmd.encoded

        assertThat(encoded.size).isEqualTo(20)
        assertThat(encoded[6]).isEqualTo(0x19.toByte())
        assertThat(encoded[7]).isEqualTo(10.toByte())

        assertThat(readInt(encoded, 8)).isEqualTo(nonce)

        assertThat(encoded[12]).isEqualTo(0x68.toByte())
        assertThat(encoded[13]).isEqualTo(0.toByte())
        assertThat(encoded[14]).isEqualTo(0.toByte())
        assertThat(encoded[15]).isEqualTo(20.toByte())
        assertThat(encoded[16]).isEqualTo(BeepRepetitionType.EVERY_MINUTE_AND_EVERY_15_MIN.value)
        assertThat(encoded[17]).isEqualTo(BeepType.FOUR_TIMES_BIP_BEEP.value)

        assertCrcMatches(encoded, crcOffset = 18)
    }


    @Test
    fun `SuspendDeliveryCommand composes StopDelivery ALL plus embedded ProgramAlerts correctly`() {
        val uniqueId = 0x22334455.toInt()
        val sequenceNumber: Short = 6
        val nonce = 0x01020304

        val cmd = SuspendDeliveryCommand.Builder()
            .setUniqueId(uniqueId)
            .setSequenceNumber(sequenceNumber)
            .setNonce(nonce)
            .setBeepType(BeepType.LONG_SINGLE_BEEP)
            .build()

        val encoded = cmd.encoded

        assertThat(encoded.size).isEqualTo(27)
        assertThat(encoded[6]).isEqualTo(0x1f.toByte())

        assertThat(readInt(encoded, 8)).isEqualTo(nonce)

        assertThat(encoded[12]).isEqualTo(0x67.toByte())

        assertThat(encoded[13]).isEqualTo(0x19.toByte())
        assertThat(encoded[14]).isEqualTo(10.toByte())
        assertThat(readInt(encoded, 15)).isEqualTo(nonce)
        assertThat(encoded[19]).isEqualTo(0x68.toByte())

        assertCrcMatches(encoded, crcOffset = 25)
    }


    @Test
    fun `SetUniqueIdCommand encodes to the documented 29-byte layout`() {
        val targetUniqueId = 0x12345678
        val sequenceNumber: Short = 7
        val lotNumber = 123456
        val podSequenceNumber = 654321

        val cal = java.util.Calendar.getInstance()
        cal.set(2024, java.util.Calendar.MARCH, 15, 10, 30, 0)

        val cmd = SetUniqueIdCommand.Builder()
            .setUniqueId(targetUniqueId)
            .setSequenceNumber(sequenceNumber)
            .setLotNumber(lotNumber)
            .setPodSequenceNumber(podSequenceNumber)
            .setInitializationTime(cal.time)
            .build()

        val encoded = cmd.encoded

        assertThat(encoded.size).isEqualTo(29)

        assertThat(readInt(encoded, 0)).isEqualTo(-1)

        assertThat(encoded[6]).isEqualTo(0x03.toByte())

        assertThat(readInt(encoded, 8)).isEqualTo(targetUniqueId)

        assertThat(encoded[12]).isEqualTo(0x14.toByte())
        assertThat(encoded[13]).isEqualTo(0x04.toByte())

        assertThat(encoded[14]).isEqualTo(3.toByte())
        assertThat(encoded[15]).isEqualTo(15.toByte())
        assertThat(encoded[16]).isEqualTo(24.toByte())
        assertThat(encoded[17]).isEqualTo(10.toByte())
        assertThat(encoded[18]).isEqualTo(30.toByte())

        assertThat(readInt(encoded, 19)).isEqualTo(lotNumber)
        assertThat(readInt(encoded, 23)).isEqualTo(podSequenceNumber)

        assertCrcMatches(encoded, crcOffset = 27)
    }


    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    /** Recomputes the CRC over everything before [crcOffset] using the real, production
     *  [MessageUtil.createCrc] and checks it matches the two CRC bytes at [crcOffset]. */
    private fun assertCrcMatches(encoded: ByteArray, crcOffset: Int) {
        val withoutCrc = encoded.copyOfRange(0, crcOffset)
        val expectedCrc = MessageUtil.createCrc(withoutCrc)
        val embeddedCrc = (((encoded[crcOffset].toInt() and 0xFF) shl 8) or
            (encoded[crcOffset + 1].toInt() and 0xFF)).toShort()
        assertThat(embeddedCrc).isEqualTo(expectedCrc)
    }
}

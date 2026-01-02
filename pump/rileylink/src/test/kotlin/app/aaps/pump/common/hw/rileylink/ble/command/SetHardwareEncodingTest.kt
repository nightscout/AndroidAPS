package app.aaps.pump.common.hw.rileylink.ble.command

import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for SetHardwareEncoding command
 */
class SetHardwareEncodingTest {

    @Test
    fun `getCommandType returns SetHardwareEncoding`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.None)

        assertEquals(RileyLinkCommandType.SetHardwareEncoding, command.getCommandType())
    }

    @Test
    fun `getRaw with None encoding`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.None)
        val raw = command.getRaw()

        assertArrayEquals(
            byteArrayOf(
                RileyLinkCommandType.SetHardwareEncoding.code,
                RileyLinkEncodingType.None.value
            ),
            raw
        )
    }

    @Test
    fun `getRaw with Manchester encoding`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.Manchester)
        val raw = command.getRaw()

        assertArrayEquals(
            byteArrayOf(
                RileyLinkCommandType.SetHardwareEncoding.code,
                RileyLinkEncodingType.Manchester.value
            ),
            raw
        )
    }

    @Test
    fun `getRaw with FourByteSixByteLocal encoding`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteLocal)
        val raw = command.getRaw()

        assertArrayEquals(
            byteArrayOf(
                RileyLinkCommandType.SetHardwareEncoding.code,
                RileyLinkEncodingType.FourByteSixByteLocal.value
            ),
            raw
        )
    }

    @Test
    fun `getRaw with FourByteSixByteRileyLink encoding`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteRileyLink)
        val raw = command.getRaw()

        assertArrayEquals(
            byteArrayOf(
                RileyLinkCommandType.SetHardwareEncoding.code,
                RileyLinkEncodingType.FourByteSixByteRileyLink.value
            ),
            raw
        )
    }

    @Test
    fun `getRaw returns 2 bytes for None`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.None)
        val raw = command.getRaw()

        assertEquals(2, raw.size)
    }

    @Test
    fun `getRaw returns 2 bytes for Manchester`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.Manchester)
        val raw = command.getRaw()

        assertEquals(2, raw.size)
    }

    @Test
    fun `getRaw returns 2 bytes for FourByteSixByteLocal`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteLocal)
        val raw = command.getRaw()

        assertEquals(2, raw.size)
    }

    @Test
    fun `getRaw returns 2 bytes for FourByteSixByteRileyLink`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteRileyLink)
        val raw = command.getRaw()

        assertEquals(2, raw.size)
    }

    @Test
    fun `None encoding has value 0x00`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.None)
        val raw = command.getRaw()

        assertEquals(0x00.toByte(), raw[1])
    }

    @Test
    fun `Manchester encoding has value 0x01`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.Manchester)
        val raw = command.getRaw()

        assertEquals(0x01.toByte(), raw[1])
    }

    @Test
    fun `FourByteSixByteLocal encoding has value 0x00`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteLocal)
        val raw = command.getRaw()

        assertEquals(0x00.toByte(), raw[1])
    }

    @Test
    fun `FourByteSixByteRileyLink encoding has value 0x02`() {
        val command = SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteRileyLink)
        val raw = command.getRaw()

        assertEquals(0x02.toByte(), raw[1])
    }

    @Test
    fun `command type is always first byte`() {
        val encodings = listOf(
            RileyLinkEncodingType.None,
            RileyLinkEncodingType.Manchester,
            RileyLinkEncodingType.FourByteSixByteLocal,
            RileyLinkEncodingType.FourByteSixByteRileyLink
        )

        encodings.forEach { encoding ->
            val command = SetHardwareEncoding(encoding)
            val raw = command.getRaw()

            assertEquals(RileyLinkCommandType.SetHardwareEncoding.code, raw[0])
        }
    }

    @Test
    fun `encoding value is always second byte`() {
        val encodings = listOf(
            RileyLinkEncodingType.None,
            RileyLinkEncodingType.Manchester,
            RileyLinkEncodingType.FourByteSixByteLocal,
            RileyLinkEncodingType.FourByteSixByteRileyLink
        )

        encodings.forEach { encoding ->
            val command = SetHardwareEncoding(encoding)
            val raw = command.getRaw()

            assertEquals(encoding.value, raw[1])
        }
    }

    @Test
    fun `realistic scenario - set encoding for Omnipod`() {
        // Omnipod uses Manchester encoding on RileyLink
        val command = SetHardwareEncoding(RileyLinkEncodingType.Manchester)
        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.SetHardwareEncoding.code, raw[0])
        assertEquals(0x01.toByte(), raw[1])
    }

    @Test
    fun `realistic scenario - set encoding for Medtronic with RileyLink encoding`() {
        // Medtronic can use 4b6b encoding on RileyLink
        val command = SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteRileyLink)
        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.SetHardwareEncoding.code, raw[0])
        assertEquals(0x02.toByte(), raw[1])
    }

    @Test
    fun `realistic scenario - set encoding for Medtronic with local encoding`() {
        // Medtronic can use 4b6b encoding locally (no encoding on RL)
        val command = SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteLocal)
        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.SetHardwareEncoding.code, raw[0])
        assertEquals(0x00.toByte(), raw[1])
    }

    @Test
    fun `realistic scenario - disable encoding`() {
        // Disable hardware encoding
        val command = SetHardwareEncoding(RileyLinkEncodingType.None)
        val raw = command.getRaw()

        assertEquals(RileyLinkCommandType.SetHardwareEncoding.code, raw[0])
        assertEquals(0x00.toByte(), raw[1])
    }

    @Test
    fun `encoding property is accessible`() {
        val encoding = RileyLinkEncodingType.Manchester
        val command = SetHardwareEncoding(encoding)

        // The encoding is private, but we can verify through getRaw()
        val raw = command.getRaw()
        assertEquals(encoding.value, raw[1])
    }

    @Test
    fun `all encoding types can be set`() {
        val allEncodings = RileyLinkEncodingType.entries

        allEncodings.forEach { encoding ->
            val command = SetHardwareEncoding(encoding)
            val raw = command.getRaw()

            assertEquals(2, raw.size)
            assertEquals(RileyLinkCommandType.SetHardwareEncoding.code, raw[0])
            assertEquals(encoding.value, raw[1])
        }
    }

    @Test
    fun `command format is consistent across encoding types`() {
        val encodings = listOf(
            RileyLinkEncodingType.None,
            RileyLinkEncodingType.Manchester,
            RileyLinkEncodingType.FourByteSixByteLocal,
            RileyLinkEncodingType.FourByteSixByteRileyLink
        )

        encodings.forEach { encoding ->
            val command = SetHardwareEncoding(encoding)
            val raw = command.getRaw()

            // All should have same structure: [command_type, encoding_value]
            assertEquals(2, raw.size)
            assertEquals(RileyLinkCommandType.SetHardwareEncoding.code, raw[0])
        }
    }

    @Test
    fun `Manchester and FourByteSixByteRileyLink have different values`() {
        val manchesterCommand = SetHardwareEncoding(RileyLinkEncodingType.Manchester)
        val fourByteSixByteCommand = SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteRileyLink)

        val manchesterRaw = manchesterCommand.getRaw()
        val fourByteSixByteRaw = fourByteSixByteCommand.getRaw()

        // They should have different encoding values
        assert(manchesterRaw[1] != fourByteSixByteRaw[1])
    }

    @Test
    fun `None and FourByteSixByteLocal have same value but different semantics`() {
        val noneCommand = SetHardwareEncoding(RileyLinkEncodingType.None)
        val localCommand = SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteLocal)

        val noneRaw = noneCommand.getRaw()
        val localRaw = localCommand.getRaw()

        // Both use 0x00 (no encoding on RileyLink hardware)
        assertEquals(noneRaw[1], localRaw[1])
        assertEquals(0x00.toByte(), noneRaw[1])
    }

    @Test
    fun `switching between encodings creates different commands`() {
        val commands = listOf(
            SetHardwareEncoding(RileyLinkEncodingType.None),
            SetHardwareEncoding(RileyLinkEncodingType.Manchester),
            SetHardwareEncoding(RileyLinkEncodingType.FourByteSixByteRileyLink)
        )

        val rawCommands = commands.map { it.getRaw() }

        // Manchester should have unique value
        assert(rawCommands[0][1] != rawCommands[1][1] || rawCommands[1][1] != rawCommands[2][1])
    }
}

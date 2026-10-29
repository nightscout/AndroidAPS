package app.aaps.cgm.dexcomg7.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Advertisement and applicator barcode parsing. Ported from Trio's G7AdvertisementTests and G7SensorPackageTests. */
class G7PairingInputsTest {

    /** Manufacturer data as Android gives it: without the 0x00D0 company id. */
    private fun manufacturerData(serial: String, typesInUse: Int): ByteArray {
        val crc = Crc16.xmodem(serial.toByteArray(Charsets.US_ASCII))
        return byteArrayOf(crc.toByte(), (crc shr 8).toByte(), typesInUse.toByte(), 0x04)
    }

    @Test
    fun crcHasTheStandardCheckValue() {
        assertThat(Crc16.xmodem("123456789".toByteArray())).isEqualTo(0x31C3)
        assertThat(Crc16.xmodem(ByteArray(0))).isEqualTo(0)
    }

    @Test
    fun readsSerialChecksumAndSlot() {
        val advertisement = G7Advertisement("DXCM12", manufacturerData("123456789012", 0x02))
        assertThat(advertisement.serialChecksum).isEqualTo(Crc16.xmodem("123456789012".toByteArray()))
        assertThat(advertisement.isSlotHeld()).isTrue()
        assertThat(G7DisplayType.PHONE.typesInUseMask).isEqualTo(0x02)
        assertThat(G7DisplayType.MEDICAL.typesInUseMask).isEqualTo(0x01)
        assertThat(advertisement.isSupportedSensor).isTrue()
    }

    @Test
    fun freeSlot() {
        val advertisement = G7Advertisement("DX0212", manufacturerData("1", 0x00))
        assertThat(advertisement.isSlotHeld()).isFalse()
        assertThat(advertisement.model).isEqualTo(G7SensorModel.ONE_PLUS)
    }

    @Test
    fun matchesSerials() {
        val advertisement = G7Advertisement("DXCM12", manufacturerData("123456789012", 0))
        assertThat(advertisement.couldHaveSerial("123456789012")).isTrue()
        assertThat(advertisement.couldHaveSerial("123456789013")).isFalse()
    }

    @Test
    fun unknownAdvertisementNeverExcludesASensor() {
        for (data in listOf(null, ByteArray(0), byteArrayOf(0x01, 0x02))) {
            val advertisement = G7Advertisement("DXCM12", data)
            assertThat(advertisement.serialChecksum).isNull()
            assertThat(advertisement.isSlotHeld()).isNull()
            assertThat(advertisement.couldHaveSerial("123456789012")).isTrue()
        }
    }

    @Test
    fun nonNumericSerialDoesNotExclude() {
        val advertisement = G7Advertisement("DXCM12", manufacturerData("123", 0))
        assertThat(advertisement.couldHaveSerial("ABC")).isTrue()
        assertThat(G7Advertisement.serialChecksum("ABC")).isNull()
        assertThat(G7Advertisement.serialChecksum("")).isNull()
    }

    @Test
    fun steloAndOthersAreNotSupported() {
        assertThat(G7Advertisement("Dexcom12", null).isSupportedSensor).isFalse()
        assertThat(G7Advertisement("DX0112", null).isSupportedSensor).isFalse()
        assertThat(G7Advertisement("Omnipod", null).isSupportedSensor).isFalse()
    }

    private val gs = "\u001D"
    private val samplePayload get() = "0100386270001863" + "17260531" + "10LOT42" + gs + "21123456789012" + gs + "2401155"

    @Test
    fun readsASensorBox() {
        val box = requireNotNull(G7SensorPackage.parse(samplePayload))
        assertThat(box.gtin).isEqualTo("00386270001863")
        assertThat(box.expiry).isEqualTo("260531")
        assertThat(box.lot).isEqualTo("LOT42")
        assertThat(box.serial).isEqualTo("123456789012")
        assertThat(box.pairingCode).isEqualTo("1155")
        assertThat(box.isDexcom).isTrue()
    }

    @Test
    fun skipsSymbologyIdentifierAndLeadingSeparator() {
        val box = requireNotNull(G7SensorPackage.parse("]d2$gs$samplePayload"))
        assertThat(box.pairingCode).isEqualTo("1155")
        assertThat(box.serial).isEqualTo("123456789012")
    }

    @Test
    fun fieldOrderDoesNotMatter() {
        val box = requireNotNull(G7SensorPackage.parse("2401155" + gs + "21123456789012" + gs + "0100386270001863"))
        assertThat(box.pairingCode).isEqualTo("1155")
        assertThat(box.serial).isEqualTo("123456789012")
        assertThat(box.gtin).isEqualTo("00386270001863")
    }

    @Test
    fun codeAtTheEndNeedsNoSeparator() {
        assertThat(G7SensorPackage.parse("21123456789012" + gs + "2400420")!!.pairingCode).isEqualTo("0420")
    }

    @Test
    fun otherValueInAi240IsNotACode() {
        val box = requireNotNull(G7SensorPackage.parse("0100386270001863" + "240ABC123"))
        assertThat(box.pairingCode).isNull()
        assertThat(box.gtin).isEqualTo("00386270001863")
    }

    @Test
    fun otherManufacturerIsNotDexcom() {
        assertThat(G7SensorPackage.parse("0100123456789012")!!.isDexcom).isFalse()
    }

    @Test
    fun unrelatedBarcodeIsRejected() {
        assertThat(G7SensorPackage.parse("https://example.com")).isNull()
        assertThat(G7SensorPackage.parse("")).isNull()
    }

    @Test
    fun unknownIdentifierKeepsEarlierFields() {
        assertThat(G7SensorPackage.parse("2401155" + gs + "42junk")!!.pairingCode).isEqualTo("1155")
    }

    @Test
    fun longestIdentifierWins() {
        assertThat(Gs1ElementString.parse("2401155")["240"]).isEqualTo("1155")
        assertThat(Gs1ElementString.parse("2401155")["24"]).isNull()
    }
}

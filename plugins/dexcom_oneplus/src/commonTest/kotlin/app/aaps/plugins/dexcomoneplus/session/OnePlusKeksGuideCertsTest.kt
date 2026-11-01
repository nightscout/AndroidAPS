package app.aaps.plugins.dexcomoneplus.session

import jamorham.keks.Plugin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three guide certificates must decode to exactly the bytes they decoded to before.
 *
 * [OnePlusKeksGuideCerts] used `android.util.Base64` while it lived in androidMain and now uses
 * `kotlin.io.encoding.Base64`, which keeps the handshake in shared code. The two
 * decoders are not the same class, and a certificate that came out one byte different would still
 * look like a certificate: nothing in the app reads it, it is handed straight to the sensor, and
 * the sensor's only answer is a pairing that fails. So the sizes are pinned here.
 *
 * The numbers are not invented. Each one is the DER length the certificate declares in its own
 * first bytes: 0x82 0x01 0xEA plus 4 header bytes for part A, 0x82 0x01 0xCD plus 4 for part B,
 * and 0x81 0x87 plus 3 for part C.
 *
 * No test name here may contain a comma: Kotlin/Native rejects one.
 */
class OnePlusKeksGuideCertsTest {

    private val pairingCode = "0000"

    /**
     * `Plugin` keeps one instance per pairing code in a static field. Channel 3 drops it, which is
     * what the driver itself does, so a later test never sees this one's certificates.
     */
    @AfterTest
    fun dropTheSharedInstance() {
        Plugin.getInstance(pairingCode).setPersistence(3, ByteArray(0))
    }

    @Test
    fun theGuideCertificatesDecodeToTheSameLengthsAsBefore() {
        val plugin = Plugin.getInstance(pairingCode)

        OnePlusKeksGuideCerts.install(plugin)

        assertEquals(494, plugin.context.partA?.size, "part A")
        assertEquals(465, plugin.context.partB?.size, "part B")
        assertEquals(138, plugin.context.partC?.size, "part C")
    }

    @Test
    fun theDecodedCertificatesStartWithADerSequence() {
        val plugin = Plugin.getInstance(pairingCode)

        OnePlusKeksGuideCerts.install(plugin)

        // 0x30 is the DER tag for SEQUENCE. A decoder that dropped or shifted a byte would not
        // land on it, so this catches an offset error that a length check alone could miss.
        assertEquals(0x30, plugin.context.partA?.get(0)?.toInt())
        assertEquals(0x30, plugin.context.partB?.get(0)?.toInt())
        assertEquals(0x30, plugin.context.partC?.get(0)?.toInt())
    }

    @Test
    fun libkeksAcceptsTheInstalledCertificatesAsComplete() {
        val plugin = Plugin.getInstance(pairingCode)

        OnePlusKeksGuideCerts.install(plugin)

        // The handshake's own gate: all three present and each over 100 bytes. Without it
        // libkeks raises "Missing QR code" in the middle of an unbonded four-digit pairing.
        assertTrue(plugin.context.validateParts())
    }

    @Test
    fun installGivesTheSensorACopyAndKeepsItsOwn() {
        val plugin = Plugin.getInstance(pairingCode)

        OnePlusKeksGuideCerts.install(plugin)
        plugin.context.partA?.fill(0)
        OnePlusKeksGuideCerts.install(plugin)

        // The second install must hand over untouched bytes. install() copies on the way out for
        // exactly this reason, and it is called again on every connect.
        assertEquals(0x30, plugin.context.partA?.get(0)?.toInt())
    }
}

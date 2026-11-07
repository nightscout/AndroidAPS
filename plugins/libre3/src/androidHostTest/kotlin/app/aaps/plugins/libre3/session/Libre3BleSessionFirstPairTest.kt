package app.aaps.plugins.libre3.session

import app.aaps.plugins.libre3.crypto.Libre3AesCcm
import app.aaps.plugins.libre3.crypto.Libre3EphemeralKeyPair
import app.aaps.plugins.libre3.crypto.Libre3FirstPairEphemeral
import app.aaps.plugins.libre3.crypto.Libre3FirstPairPhase5Source
import app.aaps.plugins.libre3.crypto.Libre3Phase6Response
import app.aaps.plugins.libre3.crypto.Libre3PhoneCert
import app.aaps.plugins.libre3.crypto.Libre3RandomSource
import app.aaps.plugins.libre3.crypto.Libre3SensorCert
import app.aaps.plugins.libre3.gatt.Libre3BluetoothUuids
import app.aaps.plugins.libre3.gatt.Libre3GattClient
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import app.aaps.plugins.libre3.identity.Libre3SessionKeys
import app.aaps.plugins.libre3.identity.Libre3SessionStore
import com.google.common.truth.Truth.assertThat
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import org.junit.jupiter.api.Test

/**
 * The first pairing, run end to end against a written down sensor.
 *
 * The one thing this file exists for is one rule: **the pairing key must
 * reach the disk**. A pairing that seems to work but does not store its key leaves a sensor that
 * this phone owns and can never reconnect to, and the only way out for the user is a fresh NFC
 * scan. So a failed write has to fail the whole pairing, and that is pinned here.
 */
class Libre3BleSessionFirstPairTest {

    private val identity = Libre3SensorIdentity(
        serialNumber = "1M00A1B2C",
        bleAddress = "11:22:33:44:55:66",
        blePin = byteArrayOf(0x32, 0x25, 0xEC.toByte(), 0x72),
        receiverId = 0x6F0D8378,
        generation = 1,
        warmupMinutes = 60,
        wearDurationMinutes = 21600,
        activatedAtMs = 1_700_000_000_000L,
    )

    /** A store that keeps everything in memory and can be told to fail its one blocking write. */
    private class FakeStore(
        private val storedPairingKey: ByteArray? = null,
        private val pairingKeyWriteWorks: Boolean = true,
    ) : Libre3SessionStore {

        var savedPairingKey: ByteArray? = null
        var savedKEnc: ByteArray? = null
        lateinit var identity: Libre3SensorIdentity

        /** Set when the key was written. Used to prove the write happened before Phase 5 went out. */
        var phase5WireAtWriteTime: ByteArray? = null
        var commandsAtWriteTime: List<Byte>? = null
        var writeSeen = false
        var clearSeen = false
        var linkAtWriteTime: (() -> ByteArray?)? = null
        var commandsAtWriteTimeSource: (() -> List<Byte>)? = null

        override fun loadIdentity(): Libre3SensorIdentity = identity

        override fun loadSessionKeys() = Libre3SessionKeys(storedPairingKey, null, null)

        override fun savePhase5RawKeyAndWait(phase5RawKey: ByteArray): Boolean {
            writeSeen = true
            phase5WireAtWriteTime = linkAtWriteTime?.invoke()
            commandsAtWriteTime = commandsAtWriteTimeSource?.invoke()
            if (!pairingKeyWriteWorks) return false
            savedPairingKey = phase5RawKey
            return true
        }

        override fun clearPhase5RawKeyAndWait(): Boolean {
            clearSeen = true
            savedPairingKey = null
            return true
        }

        override fun saveSessionKeys(kEnc: ByteArray, ivEnc: ByteArray) {
            savedKEnc = kEnc
        }
    }

    /**
     * A sensor that answers a first pairing from a script, and writes down every command byte.
     *
     * Its own key material is fixed, so the test is repeatable.
     */
    private class ScriptedSensorLink(private val sensorCert: ByteArray, private val sensorEphemeral: ByteArray) : Libre3GattClient {

        val commandsSent = mutableListOf<Byte>()
        val notifiesTurnedOn = mutableListOf<String>()
        var connected = false
        var disconnects = 0
        var phase5Wire: ByteArray? = null
        var phase6Wire: ByteArray = ByteArray(0)

        /** When set, the sensor answers the Phase 5 message with anything but "accepted". */
        var refusePhase5 = false

        private val certQueue = ArrayDeque(listOf(sensorCert, sensorEphemeral))

        override fun connect(deviceAddress: String) {
            connected = true
        }

        override fun disconnect() {
            connected = false
            disconnects += 1
        }

        override fun isConnected() = connected

        override fun setNotify(characteristic: String, enabled: Boolean): Boolean {
            if (enabled) notifiesTurnedOn.add(characteristic)
            return true
        }

        override fun write(characteristic: String, payload: ByteArray) {
            if (characteristic == Libre3BluetoothUuids.SEC_CHALLENGE_DATA) phase5Wire = payload
        }

        override fun writeRaw(characteristic: String, payload: ByteArray) {
            commandsSent.add(payload[0])
        }

        override fun awaitNotifyRaw(characteristic: String, timeoutMs: Long): ByteArray {
            // The answer the sensor gives to the command that was just sent: the pairing steps,
            // written the other way round.
            val answer = when (commandsSent.last()) {
                Libre3SessionAuth.SEND_CERTIFICATE_LOAD_DONE -> Libre3SessionAuth.CERTIFICATE_ACCEPTED
                Libre3SessionAuth.GET_CERTIFICATE            -> Libre3SessionAuth.CERTIFICATE_READY
                Libre3SessionAuth.SEND_EPHEMERAL_DONE        -> Libre3SessionAuth.EPHEMERAL_READY
                Libre3SessionAuth.START_AUTHORIZATION        -> Libre3SessionAuth.CHALLENGE_LOAD_DONE
                Libre3SessionAuth.SEND_CHALLENGE_LOAD_DONE   ->
                    if (refusePhase5) 0x00 else Libre3SessionAuth.CHALLENGE_LOAD_DONE
                else                                         -> 0x00
            }
            return byteArrayOf(answer)
        }

        override fun awaitNotify(characteristic: String, exactly: Int, timeoutMs: Long): ByteArray = when {
            characteristic == Libre3BluetoothUuids.SEC_CERT_DATA      -> certQueue.removeFirst()
            exactly == Libre3SessionAuth.CHALLENGE_SIZE               -> ByteArray(23) { (it + 0x80).toByte() }
            else                                                      -> phase6Wire
        }

        override fun awaitDataPlaneNotify(timeoutMs: Long): Pair<String, ByteArray>? = null
    }

    /** A source of bytes that always hands back the same draw, so the pairing is repeatable. */
    private class FixedRandom(private val entropy: ByteArray) : Libre3RandomSource {

        override fun nextBytes(out: ByteArray) {
            if (out.size == entropy.size) entropy.copyInto(out) else out.fill(0x5A)
        }
    }

    private val capturedEntropy = hexOf(
        "8987c91f1595e8a060e4cba652368ae8797e9113cfd412bebd0ea1a03783ae59" +
            "ee70d2c947578803b06b275c96632d148b81658bb87a3eabb5755273c40c397" +
            "f7255f3c1d742df608383fbbfff5a9b9fbc11a1ab525382024c85687cf79c2" +
            "a391ca7cc309ff82fe098c2d86e49f8b26364153f0bcb8945c887f5a2a7b5" +
            "4d568daa373a86c85c283fbb6285f35dca2d30263c34ce182c1fc63e6022a" +
            "3c7e6eaebe3a473d3c754bb8f3982172431af66388948aaf5c709f6699b76" +
            "08dcd161811dda99c61b302f46684433e61ef2afa4dd9f8b0f2472f612019" +
            "7cdfc0b940ad5f93ac01fc7497fb355c753df9c65fc68721690c35a09550fb" +
            "3c326e38bcbe37ebb309a680c383967627f58a108e1e94ecd16c5d2bc2f57" +
            "6dabdc7b"
    )

    /** The key pair this test signs its sensor certificates with, standing in for the real one. */
    private val certSigner: KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    private val certSignerPoint: ByteArray = point65Of(certSigner)

    private fun point65Of(pair: KeyPair): ByteArray {
        val point = (pair.public as ECPublicKey).w
        fun fixed(value: BigInteger): ByteArray {
            val raw = value.toByteArray()
            val trimmed = if (raw.size > 32) raw.copyOfRange(raw.size - 32, raw.size) else raw
            return ByteArray(32 - trimmed.size) + trimmed
        }
        return byteArrayOf(0x04) + fixed(point.affineX) + fixed(point.affineY)
    }

    /** A 140 byte sensor certificate whose point is real and whose signature really verifies. */
    private fun sensorCertWith(point65: ByteArray, signed: Boolean = true): ByteArray {
        val raw = ByteArray(Libre3SensorCert.TOTAL_SIZE) { (it and 0xFF).toByte() }
        point65.copyInto(raw, 11)
        if (!signed) return raw
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(certSigner.private)
        signer.update(raw.copyOfRange(0, 76))
        rawFromDer(signer.sign()).copyInto(raw, 76)
        return raw
    }

    /** Turns the tagged form the phone makes back into the plain 64 bytes the wire carries. */
    private fun rawFromDer(der: ByteArray): ByteArray {
        var index = 2
        fun readInteger(): ByteArray {
            index += 1
            val length = der[index].toInt() and 0xFF
            index += 1
            val value = der.copyOfRange(index, index + length)
            index += length
            val trimmed = if (value.size > 32) value.copyOfRange(value.size - 32, value.size) else value
            return ByteArray(32 - trimmed.size) + trimmed
        }
        return readInteger() + readInteger()
    }

    /** What the sensor would send back in the last step, built with the key the phone just made. */
    private fun phase6For(pairingKey: ByteArray, sensorR1: ByteArray, phoneR2: ByteArray): ByteArray {
        val plaintext = phoneR2 + sensorR1 + ByteArray(16) { (it + 1).toByte() } + ByteArray(8) { (it + 0x30).toByte() }
        val nonce = ByteArray(7) { (it + 0x90).toByte() }
        val sealed = Libre3AesCcm.encrypt(
            nonce = nonce,
            plaintext = plaintext,
            tagLength = Libre3Phase6Response.TAG_SIZE,
            aes = Libre3AesCcm.standardAes(pairingKey),
        )
        return sealed.ciphertext + sealed.tag + nonce
    }

    private fun hexOf(text: String) = ByteArray(text.length / 2) {
        text.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }

    @Test
    fun `a first pairing stores its key before it reports success`() {
        val ephemeral = Libre3FirstPairEphemeral.make(FixedRandom(capturedEntropy))
        // The sensor's two points. Any real point of the curve will do for this test.
        val sensorEphemeralPoint = Libre3EphemeralKeyPair.randomForReconnect().publicKey65
        val sensorStaticPoint = Libre3EphemeralKeyPair.randomForReconnect().publicKey65
        // The same static scalar the session uses, which for the `03 03` certificate this build
        // ships is the one the certificate brings, not the one worked out from the entry source.
        val expectedKey = Libre3FirstPairPhase5Source.derive(
            material = ephemeral,
            sensorEphemeralPublicKey65 = sensorEphemeralPoint,
            sensorStaticPublicKey65 = sensorStaticPoint,
            staticScalarWindowOverride = Libre3PhoneCert.bundled()?.phase5StaticScalarWindowOverride,
        ).rawKey

        val link = ScriptedSensorLink(sensorCertWith(sensorStaticPoint), sensorEphemeralPoint)
        val store = FakeStore().also {
            it.identity = identity
            it.linkAtWriteTime = { link.phase5Wire }
            it.commandsAtWriteTimeSource = { link.commandsSent.toList() }
        }
        val session = Libre3BleSession(
            gatt = link,
            store = store,
            pairingBlocks = { key -> Libre3AesCcm.standardAes(key) },
            random = FixedRandom(capturedEntropy),
            sensorCertSigningKeys = listOf(certSignerPoint),
        )
        link.phase6Wire = phase6For(expectedKey, ByteArray(16) { (it + 0x80).toByte() }, ByteArray(16) { 0x5A })

        val result = session.open(firstPairAvailable = true)

        // The session is really up, so the sensor's last answer decrypted, which needs the very key
        // the phone derived.
        assertThat(result).isInstanceOf(Libre3BleSession.Result.Up::class.java)
        assertThat(store.savedPairingKey).isEqualTo(expectedKey)
        assertThat(link.commandsSent.first()).isEqualTo(Libre3SessionAuth.START_AUTHENTICATION)

        // And it was written BEFORE 0x11 and Phase 5 went out, not after the pairing had finished.
        // If the write were moved to the end, this would be the 54 byte Phase 5 message instead of
        // null, and 0x11 would already have been sent.
        assertThat(store.writeSeen).isTrue()
        assertThat(store.phase5WireAtWriteTime).isNull()
        assertThat(store.commandsAtWriteTime).doesNotContain(Libre3SessionAuth.START_AUTHORIZATION)
        assertThat(link.phase5Wire).isNotNull()
        assertThat(link.commandsSent).contains(Libre3SessionAuth.START_AUTHORIZATION)
    }

    @Test
    fun `a first pairing fails when its key cannot be written`() {
        val sensorEphemeralPoint = Libre3EphemeralKeyPair.randomForReconnect().publicKey65
        val sensorStaticPoint = Libre3EphemeralKeyPair.randomForReconnect().publicKey65
        val link = ScriptedSensorLink(sensorCertWith(sensorStaticPoint), sensorEphemeralPoint)
        val store = FakeStore(pairingKeyWriteWorks = false).also { it.identity = identity }
        val session = Libre3BleSession(
            gatt = link,
            store = store,
            pairingBlocks = { key -> Libre3AesCcm.standardAes(key) },
            random = FixedRandom(capturedEntropy),
            sensorCertSigningKeys = listOf(certSignerPoint),
        )

        val result = session.open(firstPairAvailable = true)

        assertThat(result).isInstanceOf(Libre3BleSession.Result.Failed::class.java)
        assertThat(store.savedPairingKey).isNull()
        assertThat(store.savedKEnc).isNull()
        assertThat(link.commandsSent).doesNotContain(Libre3SessionAuth.START_AUTHORIZATION)
        // The link must not be left open behind a failed pairing.
        assertThat(link.isConnected()).isFalse()
    }

    @Test
    fun `a sensor that already has a key never sees the first pairing command`() {
        val link = ScriptedSensorLink(ByteArray(140), ByteArray(65))
        val store = FakeStore(storedPairingKey = ByteArray(16) { it.toByte() }).also { it.identity = identity }
        val session = Libre3BleSession(
            gatt = link,
            store = store,
            pairingBlocks = { key -> Libre3AesCcm.standardAes(key) },
            random = FixedRandom(capturedEntropy),
            sensorCertSigningKeys = listOf(certSignerPoint),
        )

        session.open(firstPairAvailable = true)

        assertThat(link.commandsSent).doesNotContain(Libre3SessionAuth.START_AUTHENTICATION)
        assertThat(link.commandsSent.first()).isEqualTo(Libre3SessionAuth.START_AUTHORIZATION)
    }

    @Test
    fun `a sensor whose certificate is not signed by a key we know is refused`() {
        val sensorEphemeralPoint = Libre3EphemeralKeyPair.randomForReconnect().publicKey65
        val sensorStaticPoint = Libre3EphemeralKeyPair.randomForReconnect().publicKey65
        val link = ScriptedSensorLink(sensorCertWith(sensorStaticPoint, signed = false), sensorEphemeralPoint)
        val store = FakeStore().also { it.identity = identity }
        val session = Libre3BleSession(
            gatt = link,
            store = store,
            pairingBlocks = { key -> Libre3AesCcm.standardAes(key) },
            random = FixedRandom(capturedEntropy),
            sensorCertSigningKeys = listOf(certSignerPoint),
        )

        val result = session.open(firstPairAvailable = true)

        // The pairing stops at the certificate, so no key is derived and none is stored.
        assertThat(result).isInstanceOf(Libre3BleSession.Result.Failed::class.java)
        assertThat(store.writeSeen).isFalse()
        assertThat(store.savedPairingKey).isNull()
        assertThat(link.phase5Wire).isNull()
        assertThat(link.isConnected()).isFalse()
    }

    /**
     * The other half of the same rule.
     *
     * The key is written before Phase 5 goes out, and the test above pins that. But a sensor that
     * then refuses Phase 5 never authorised this phone, so that key is worthless. If it stayed on
     * the disk, every later attempt would take the short reconnect path with a key the sensor has
     * never seen, and no NFC scan could get out of it. So it has to be dropped again.
     */
    @Test
    fun `a key is dropped again when the sensor refuses the phase 5 answer`() {
        val sensorEphemeralPoint = Libre3EphemeralKeyPair.randomForReconnect().publicKey65
        val sensorStaticPoint = Libre3EphemeralKeyPair.randomForReconnect().publicKey65
        val link = ScriptedSensorLink(sensorCertWith(sensorStaticPoint), sensorEphemeralPoint)
        link.refusePhase5 = true
        val store = FakeStore().also { it.identity = identity }
        val session = Libre3BleSession(
            gatt = link,
            store = store,
            pairingBlocks = { key -> Libre3AesCcm.standardAes(key) },
            random = FixedRandom(capturedEntropy),
            sensorCertSigningKeys = listOf(certSignerPoint),
        )

        val result = session.open(firstPairAvailable = true)

        assertThat(result).isInstanceOf(Libre3BleSession.Result.Failed::class.java)
        // Written, then dropped again: the next attempt starts a first pairing, not a reconnect.
        assertThat(store.writeSeen).isTrue()
        assertThat(store.clearSeen).isTrue()
        assertThat(store.savedPairingKey).isNull()
        assertThat(store.savedKEnc).isNull()
        assertThat(link.isConnected()).isFalse()
    }

    @Test
    fun `the phase 6 helper and the response parser agree on the wire size`() {
        assertThat(Libre3Phase6Response.WIRE_SIZE).isEqualTo(67)
    }
}

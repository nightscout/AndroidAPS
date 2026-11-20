package app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair

import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * [O5CertificateStore] - pure crypto/DER logic, no BLE involved, so tested directly against
 * real P-256 key material rather than mocks. [O5RegistrationData] is a process-wide registry
 * keyed by controllerId, so every test uses its own controllerId and cleans up afterward to
 * avoid cross-test pollution.
 */
class O5CertificateStoreTest {

    private val aapsLogger = AAPSLoggerTest()
    private val keyGenerator = P256KeyGenerator()
    private val usedControllerIds = mutableListOf<Long>()

    @AfterEach
    fun tearDown() {
        usedControllerIds.forEach { O5RegistrationData.remove(it) }
        usedControllerIds.clear()
    }

    private fun installValidRegistration(controllerId: Long): O5RegistrationData {
        usedControllerIds.add(controllerId)
        val privateKey = keyGenerator.generatePrivateKey()
        val publicKey = keyGenerator.publicFromPrivate(privateKey)
        val data = O5RegistrationData(
            controllerId = controllerId,
            privateKeyHex = privateKey.toHex(),
            publicKeyHex = publicKey.toHex(),
            intermediateCABase64 = "",
            tlsCertificateBase64 = ""
        )
        O5RegistrationData.install(data, O5RegistrationData.O5RegistrationSource.DOWNLOADED)
        return data
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun `constructor throws when no registration data is installed`() {
        assertThrows(PairingException::class.java) {
            O5CertificateStore(aapsLogger, keyGenerator, 0x11223344L)
        }
    }

    @Test
    fun `constructor succeeds and exposes registration fields when a matching keypair is installed`() {
        val controllerId = 0xAABBCCDDL
        val data = installValidRegistration(controllerId)

        val store = O5CertificateStore(aapsLogger, keyGenerator, controllerId)

        assertThat(store.controllerId).isEqualTo(controllerId)
        assertThat(store.signingPublicKeyRaw).isEqualTo(data.publicKey)
    }

    @Test
    fun `controllerIdData is the big-endian 4-byte representation`() {
        val controllerId = 0x01020304L
        installValidRegistration(controllerId)

        val store = O5CertificateStore(aapsLogger, keyGenerator, controllerId)

        assertThat(store.controllerIdData).isEqualTo(byteArrayOf(0x01, 0x02, 0x03, 0x04))
    }

    @Test
    fun `constructor throws when the stored public key does not match the private key`() {
        val controllerId = 0x99887766L
        usedControllerIds.add(controllerId)
        val privateKey = keyGenerator.generatePrivateKey()
        val unrelatedPublicKey = keyGenerator.publicFromPrivate(keyGenerator.generatePrivateKey())
        O5RegistrationData.install(
            O5RegistrationData(
                controllerId = controllerId,
                privateKeyHex = privateKey.toHex(),
                publicKeyHex = unrelatedPublicKey.toHex(),
                intermediateCABase64 = "",
                tlsCertificateBase64 = ""
            ),
            source = O5RegistrationData.O5RegistrationSource.DOWNLOADED,
        )

        assertThrows(PairingException::class.java) {
            O5CertificateStore(aapsLogger, keyGenerator, controllerId)
        }
    }

    @Test
    fun `signRaw produces a signature verifySignature accepts against the matching public key`() {
        val controllerId = 0x12345678L
        val data = installValidRegistration(controllerId)
        val store = O5CertificateStore(aapsLogger, keyGenerator, controllerId)
        val message = "the quick brown fox".toByteArray()

        val signature = store.signRaw(message)

        assertThat(signature).hasLength(64)
        assertThat(O5CertificateStore.verifySignature(signature, message, data.publicKey)).isTrue()
    }

    @Test
    fun `verifySignature rejects a signature checked against the wrong public key`() {
        val controllerId = 0x22334455L
        installValidRegistration(controllerId)
        val store = O5CertificateStore(aapsLogger, keyGenerator, controllerId)
        val message = "the quick brown fox".toByteArray()
        val signature = store.signRaw(message)
        val wrongPublicKey = keyGenerator.publicFromPrivate(keyGenerator.generatePrivateKey())

        assertThat(O5CertificateStore.verifySignature(signature, message, wrongPublicKey)).isFalse()
    }

    @Test
    fun `verifySignature rejects a signature over different data`() {
        val controllerId = 0x33445566L
        val data = installValidRegistration(controllerId)
        val store = O5CertificateStore(aapsLogger, keyGenerator, controllerId)
        val signature = store.signRaw("original message".toByteArray())

        assertThat(O5CertificateStore.verifySignature(signature, "tampered message".toByteArray(), data.publicKey)).isFalse()
    }

    @Test
    fun `derSignatureToRaw and rawSignatureToDer round-trip a real ECDSA signature`() {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()
        val message = "round trip me".toByteArray()

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update(message)
        val der = signature.sign()

        val raw = O5CertificateStore.derSignatureToRaw(der)
        assertThat(raw).hasLength(64)

        val reDer = O5CertificateStore.rawSignatureToDer(raw)

        val verify = Signature.getInstance("SHA256withECDSA")
        verify.initVerify(keyPair.public)
        verify.update(message)
        assertThat(verify.verify(reDer)).isTrue()
    }

    @Test
    fun `verifySignature accepts a raw 64-byte signature directly, not just DER`() {
        val controllerId = 0x44556677L
        val data = installValidRegistration(controllerId)
        val store = O5CertificateStore(aapsLogger, keyGenerator, controllerId)
        val message = "raw format check".toByteArray()

        val rawSignature = store.signRaw(message)

        assertThat(rawSignature).hasLength(64)
        assertThat(O5CertificateStore.verifySignature(rawSignature, message, data.publicKey)).isTrue()
    }

    @Test
    fun `verifySignature returns false for a garbage public key rather than throwing`() {
        val garbagePublicKey = ByteArray(64) { 0x42 }
        val garbageSignature = ByteArray(64) { 0x01 }

        assertThat(O5CertificateStore.verifySignature(garbageSignature, "data".toByteArray(), garbagePublicKey)).isFalse()
    }


    private val p256SpkiHeader = byteArrayOf(
        0x30, 0x59.toByte(),
        0x30, 0x13,
        0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
        0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07,
        0x03, 0x42,
        0x00,
        0x04
    )

    @Test
    fun `extractP256PublicKey finds the 64-byte key following the SPKI header`() {
        val expectedKey = ByteArray(64) { (it + 1).toByte() }
        val certDER = byteArrayOf(0x11, 0x22, 0x33) + p256SpkiHeader + expectedKey + byteArrayOf(0x44, 0x55)

        val extracted = O5CertificateStore.extractP256PublicKey(certDER)

        assertThat(extracted).isEqualTo(expectedKey)
    }

    @Test
    fun `extractP256PublicKey returns null when the SPKI header is absent`() {
        val certDER = ByteArray(100) { it.toByte() }

        assertThat(O5CertificateStore.extractP256PublicKey(certDER)).isNull()
    }

    @Test
    fun `extractP256PublicKey returns null when there is not enough trailing data for a full key`() {
        val certDER = p256SpkiHeader + ByteArray(10)

        assertThat(O5CertificateStore.extractP256PublicKey(certDER)).isNull()
    }

    @Test
    fun `extractP256PublicKey base64 overload decodes then extracts`() {
        val expectedKey = ByteArray(64) { (it * 3).toByte() }
        val certDER = p256SpkiHeader + expectedKey
        val certDERBase64 = Base64.getEncoder().encodeToString(certDER)

        assertThat(O5CertificateStore.extractP256PublicKey(certDERBase64)).isEqualTo(expectedKey)
    }

    @Test
    fun `extractP256PublicKey base64 overload returns null for invalid base64`() {
        assertThat(O5CertificateStore.extractP256PublicKey("not valid base64!!")).isNull()
    }


    @Test
    fun `extractSerialNumber handles the v3 explicit-version-tag pattern`() {
        val serial = byteArrayOf(0x01, 0x02, 0x03)
        val v3Pattern = byteArrayOf(0xa0.toByte(), 0x03, 0x02, 0x01, 0x02, 0x02)
        val certDER = byteArrayOf(0x30, 0x10) + v3Pattern + byteArrayOf(serial.size.toByte()) + serial + byteArrayOf(0x00, 0x00)

        assertThat(O5CertificateStore.extractSerialNumber(certDER)).isEqualTo(serial)
    }

    @Test
    fun `extractSerialNumber falls back to v1 parsing when there is no version tag`() {
        val serial = byteArrayOf(0x0A, 0x0B)
        val certDER = byteArrayOf(0x30, 0x06, 0x30, 0x04, 0x02, 0x02) + serial

        assertThat(O5CertificateStore.extractSerialNumber(certDER)).isEqualTo(serial)
    }

    @Test
    fun `extractSerialNumber returns null for malformed input`() {
        assertThat(O5CertificateStore.extractSerialNumber(byteArrayOf(0x01, 0x02))).isNull()
    }
}

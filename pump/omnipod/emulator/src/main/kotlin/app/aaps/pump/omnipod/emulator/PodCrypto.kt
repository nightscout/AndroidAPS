package app.aaps.pump.omnipod.emulator

import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.Nonce
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.pod.util.RandomByteGenerator
import app.aaps.pump.omnipod.common.bledriver.pod.util.X25519KeyGenerator
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.macs.CMac
import org.spongycastle.crypto.modes.CCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.KeyAgreement

/**
 * The pod side of the Dash pairing: the same maths as `KeyExchange` in the driver, with the pod's own private key.
 * Both sides end with the same long term key and the same two confirmation values, or the pairing fails.
 */
internal class PodKeyExchange(pdmPublic: ByteArray, pdmNonce: ByteArray) {

    private val x25519 = X25519KeyGenerator()
    private val podPrivate: ByteArray = x25519.generatePrivateKey()
    val podPublic: ByteArray = x25519.publicFromPrivate(podPrivate)
    val podNonce: ByteArray = RandomByteGenerator().nextBytes(NONCE_SIZE)

    val ltk = ByteArray(CMAC_SIZE)
    val pdmConf = ByteArray(CMAC_SIZE)
    val podConf = ByteArray(CMAC_SIZE)

    init {
        val shared = x25519.computeSharedSecret(podPrivate, pdmPublic)
        val firstKey = podPublic.last4() + pdmPublic.last4() + podNonce.last4() + pdmNonce.last4()
        val intermediateKey = ByteArray(CMAC_SIZE)
        aesCmac(firstKey, shared, intermediateKey)
        val tail = MAGIC + podNonce + pdmNonce + byteArrayOf(0, 1)
        aesCmac(intermediateKey, byteArrayOf(2) + tail, ltk)
        val confKey = ByteArray(CMAC_SIZE)
        aesCmac(intermediateKey, byteArrayOf(1) + tail, confKey)
        aesCmac(confKey, PDM_CONF_PREFIX + pdmNonce + podNonce, pdmConf)
        aesCmac(confKey, POD_CONF_PREFIX + podNonce + pdmNonce, podConf)
    }

    private fun ByteArray.last4() = copyOfRange(size - 4, size)

    private fun aesCmac(key: ByteArray, data: ByteArray, result: ByteArray) {
        val mac = CMac(AESEngine())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        mac.doFinal(result, 0)
    }

    companion object {

        const val PUBLIC_KEY_SIZE = 32
        const val NONCE_SIZE = 16
        const val CMAC_SIZE = 16
        private val MAGIC = "TWIt".toByteArray()
        private val PDM_CONF_PREFIX = "KC_2_U".toByteArray()
        private val POD_CONF_PREFIX = "KC_2_V".toByteArray()
    }
}

/**
 * The pod side of the Omnipod 5 pairing: the same maths as `O5KeyExchange` in the driver, with the pod's own P-256 key.
 * The two sides swap public keys and nonces, and both get the long term key and a key for the two encrypted pairing
 * messages from SHA-256 over the shared secret.
 *
 * NOT AS A REAL POD: a real pod sends its certificate and a signature in those two messages. The driver decrypts them
 * and checks neither (see `O5LTKExchanger`), so the emulated pod sends placeholder bytes of a plausible length.
 */
internal class O5PodKeyExchange(pdmPublic: ByteArray, pdmNonce: ByteArray) {

    val podPublic: ByteArray
    val ltk: ByteArray
    private val conf: ByteArray
    private var pdmNonce: ByteArray = pdmNonce.copyOf()
    private var podNonce: ByteArray = RandomByteGenerator().nextBytes(NONCE_SIZE)

    /** The pod's nonce as it was sent to the controller, before any message counted it up. */
    val sentPodNonce: ByteArray = podNonce.copyOf()

    init {
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val own = keyPair.public as ECPublicKey
        podPublic = own.w.affineX.to32Bytes() + own.w.affineY.to32Bytes()
        val peerPoint = ECPoint(BigInteger(1, pdmPublic.copyOfRange(0, 32)), BigInteger(1, pdmPublic.copyOfRange(32, 64)))
        val peer = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(peerPoint, own.params))
        val secret = KeyAgreement.getInstance("ECDH").apply {
            init(keyPair.private)
            doPhase(peer, true)
        }.generateSecret()
        val derived = MessageDigest.getInstance("SHA-256").digest(
            length(FIRMWARE_ID.size) + FIRMWARE_ID + length(4) + ByteArray(4) +
                length(pdmPublic.size) + pdmPublic + length(podPublic.size) + podPublic + length(secret.size) + secret
        )
        conf = derived.copyOfRange(0, 16)
        ltk = derived.copyOfRange(16, 32)
    }

    /** The controller sent one of its encrypted pairing messages: its nonce counts one up. */
    fun controllerSent() {
        pdmNonce = countUp(pdmNonce)
    }

    /** One encrypted pairing message of the pod. The pod's nonce counts one up after it. */
    fun encrypt(plain: ByteArray): ByteArray {
        val nonce = byteArrayOf(0x02) + podNonce.copyOfRange(0, 6) + pdmNonce.copyOfRange(0, 6)
        val cipher = CCMBlockCipher(AESEngine())
        cipher.init(true, AEADParameters(KeyParameter(conf), MAC_SIZE * 8, nonce, ByteArray(0)))
        val encrypted = ByteArray(plain.size + MAC_SIZE)
        cipher.processPacket(plain, 0, plain.size, encrypted, 0)
        podNonce = countUp(podNonce)
        return encrypted
    }

    /** The first 8 bytes of a nonce are a little-endian counter. */
    private fun countUp(nonce: ByteArray): ByteArray = nonce.copyOf().also {
        val counter = ByteBuffer.wrap(it, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
        counter.putLong(0, counter.getLong(0) + 1)
    }

    private fun length(size: Int): ByteArray = ByteBuffer.allocate(8).putLong(size.toLong()).array()

    private fun BigInteger.to32Bytes(): ByteArray {
        val bytes = toByteArray()
        return if (bytes.size >= 32) bytes.copyOfRange(bytes.size - 32, bytes.size) else ByteArray(32 - bytes.size) + bytes
    }

    companion object {

        const val PUBLIC_KEY_SIZE = 64
        const val NONCE_SIZE = 16
        private const val MAC_SIZE = 8

        /** A fixed value of the controller firmware, as in `O5KeyExchange.FIRMWARE_ID`. */
        private val FIRMWARE_ID = byteArrayOf(0x9b.toByte(), 0x0a, 0xb9.toByte(), 0x6a, 0x76, 0xf4.toByte())
    }
}

/**
 * The pod side of `EnDecrypt` in the driver: it decrypts what the controller sent and encrypts what the pod sends. The
 * two sides count the same nonce, one step for each message in either direction, so the pod must decrypt every message
 * it gets, also an empty ACK.
 */
internal class PodEnDecrypt(private val nonce: Nonce, private val ck: ByteArray) {

    private val cipher = CCMBlockCipher(AESEngine())

    fun decrypt(msg: MessagePacket): MessagePacket {
        val header = msg.asByteArray().copyOfRange(0, HEADER_SIZE)
        cipher.init(false, AEADParameters(KeyParameter(ck), MAC_SIZE * 8, nonce.increment(true), header))
        val plain = ByteArray(msg.payload.size - MAC_SIZE)
        cipher.processPacket(msg.payload, 0, msg.payload.size, plain, 0)
        return msg.copy(payload = plain)
    }

    fun encrypt(msg: MessagePacket): MessagePacket {
        val header = msg.asByteArray(true).copyOfRange(0, HEADER_SIZE)
        cipher.init(true, AEADParameters(KeyParameter(ck), MAC_SIZE * 8, nonce.increment(false), header))
        val encrypted = ByteArray(msg.payload.size + MAC_SIZE)
        cipher.processPacket(msg.payload, 0, msg.payload.size, encrypted, 0)
        return msg.copy(payload = encrypted)
    }

    private companion object {

        const val MAC_SIZE = 8
        const val HEADER_SIZE = 16
    }
}

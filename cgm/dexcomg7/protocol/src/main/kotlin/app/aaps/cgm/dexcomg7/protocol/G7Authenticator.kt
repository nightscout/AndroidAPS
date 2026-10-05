package app.aaps.cgm.dexcomg7.protocol

import app.aaps.cgm.dexcomg7.protocol.crypto.G7Aes
import app.aaps.cgm.dexcomg7.protocol.crypto.G7ChallengeSigner
import app.aaps.cgm.dexcomg7.protocol.crypto.G7DexcomCredentials
import app.aaps.cgm.dexcomg7.protocol.crypto.G7JPake
import app.aaps.cgm.dexcomg7.protocol.crypto.G7PCert
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/** Why a handshake failed. */
sealed class G7AuthException(message: String) : Exception(message) {

    class Timeout(val step: String) : G7AuthException("Timed out waiting for the sensor during $step")

    class UnexpectedResponse(val step: String, val response: ByteArray) : G7AuthException("Unexpected response during $step: ${response.toHex()}")

    /**
     * The sensor finished the key exchange and still refused the session. Final for this connection:
     * trying again the same way cannot help, and four refusals in a row lock the sensor for a while.
     * [G7AuthFailureCode.NO_APP_KEY] is the one case the session fixes by itself, by pairing again
     * with the stored code.
     */
    class Rejected(val authStatus: Int, val failureCode: G7AuthFailureCode?) : G7AuthException("Sensor refused the session (status $authStatus, reason $failureCode)")

    /**
     * The sensor's answer to our challenge does not match our key: the code belongs to another sensor,
     * or a stored key is out of date.
     */
    class ChallengeMismatch : G7AuthException("The sensor's answer does not match our key")

    /**
     * A reconnect where the sensor accepted our key but did not report the bond (`bond=2`). Seen once
     * on hardware, with the next connection back to normal: not a refusal, so not worth a pause.
     */
    class NotBonded(val bondStatus: Int) : G7AuthException("The sensor accepted the key but reported bond status $bondStatus")

    class NoCredentials : G7AuthException("No pairing code and no stored key")
}

/**
 * Runs the handshake that makes this phone a display the sensor will talk to.
 *
 * Two paths, chosen by whether a key for this sensor is stored:
 *
 * - **First pairing.** An EC-JPAKE exchange over the 4-digit code gives a shared key. Then we prove we
 *   have it, send Dexcom's certificates, sign the sensor's key challenge, and let the sensor bond.
 * - **Reconnect.** With the key stored, all of that becomes one AES challenge and answer.
 *
 * Ported from Trio's G7SensorKit (G7Authenticator.swift, MIT, LoopKit Authors), which follows DexKit by
 * Erik Tolboom. The blocking waits there are suspending waits here.
 */
class G7Authenticator(
    private val pairingCode: String?,
    private val storedSharedKey: ByteArray?,
    private val stepTimeoutMs: Long,
    private val displayType: G7DisplayType = G7DisplayType.PHONE,
    private val random: (Int) -> ByteArray = G7JPake::secureRandomBytes,
    /** Short description of each step, for the communication log. Never carries the code or a key. */
    private val log: (String) -> Unit = {}
) {

    class Result(
        val sharedKey: ByteArray,
        /** True when this run did the key exchange, false when it reused a stored key. */
        val didExchangeKeys: Boolean
    )

    /** A failure is thrown, not logged: the caller logs it once, with what it does about it. */
    suspend fun authenticate(link: G7Link): Result {
        try {
            return run(link)
        } finally {
            link.setListener(G7Characteristic.AUTHENTICATION, null)
            link.setListener(G7Characteristic.CERTIFICATE, null)
        }
    }

    private suspend fun run(link: G7Link): Result {
        if (storedSharedKey == null && pairingCode == null) throw G7AuthException.NoCredentials()
        log(if (storedSharedKey != null) "Authenticating with the saved key" else "Pairing: running the key exchange")

        try {
            link.enableNotifications(G7Characteristic.CERTIFICATE)
            link.enableNotifications(G7Characteristic.AUTHENTICATION)
        } catch (e: G7LinkException) {
            if (e.insufficientAuthentication) log("The link could not be encrypted; the phone's bond with this sensor may be out of date")
            throw e
        }

        // Collect authentication traffic for the whole handshake. An answer can arrive while our own
        // write is still pending, and a wait started after the write would miss it.
        val authentication = MessageBuffer()
        link.setListener(G7Characteristic.AUTHENTICATION) { authentication.append(it) }

        val sharedKey: ByteArray
        val didExchangeKeys: Boolean
        if (storedSharedKey != null) {
            sharedKey = storedSharedKey
            didExchangeKeys = false
        } else {
            sharedKey = exchangeKeys(link)
            didExchangeKeys = true
        }

        val status = proveSharedKey(link, authentication, sharedKey)

        // The shortcut is for a reconnect with a stored key only. After a fresh exchange the sensor can
        // still say "bonded" (an old bond on this phone), but that is not the same as it holding the key
        // we just made. The official app always runs the certificate part when pairing; so do we.
        if (!didExchangeKeys && status.isAuthenticated && status.isBonded) {
            log("Already authenticated and bonded")
            return Result(sharedKey, didExchangeKeys)
        }
        if (!didExchangeKeys && status.isAuthenticated) throw G7AuthException.NotBonded(status.bondStatus)
        if (!didExchangeKeys) {
            // A reconnect that is not authenticated here has a key the sensor no longer accepts.
            throw G7AuthException.UnexpectedResponse("reconnect", byteArrayOf(0x05, status.authStatus.toByte(), status.bondStatus.toByte()))
        }

        log("Running the certificate exchange (auth=${status.authStatus} bond=${status.bondStatus})")
        exchangeCertificates(link, authentication)
        answerKeyChallenge(link, authentication)
        finalizeAndBond(link, authentication)
        return Result(sharedKey, didExchangeKeys)
    }

    // ---------------------------------------------------------------------------------------- EC-JPAKE

    private suspend fun exchangeKeys(link: G7Link): ByteArray {
        val code = pairingCode ?: throw G7AuthException.NoCredentials()
        val jpake = G7JPake(code, random)

        val sensorRound1 = requestSensorRound(link, 0).also { writeCertificateBytes(link, jpake.makeRound1()) }
        val sensorRound2 = requestSensorRound(link, 1).also { writeCertificateBytes(link, jpake.makeRound2()) }
        val sensorRound3 = requestSensorRound(link, 2)

        // Advisory only: the AES challenge below is the real check. On hardware the sensor's proofs do
        // not verify under our transcript format (which the sensor accepts from us), so the sensor hashes
        // something differently. Noted, not acted on.
        val proofsVerified = jpake.validateRound1Or2(sensorRound1) && jpake.validateRound1Or2(sensorRound2) &&
            jpake.validateRound3(sensorRound1, sensorRound3)
        if (!proofsVerified) log("Key exchange: the sensor's proofs do not match our format; this is normal, the key challenge is the real check")

        // Derive before sending our round 3: the sensor may drop the link as soon as it has what it needs.
        val secret = jpake.deriveSharedSecret(sensorRound2, sensorRound3)
        writeCertificateBytes(link, jpake.makeRound3(sensorRound1, sensorRound2))
        log("Key exchange complete")
        return secret.copyOf(16)
    }

    /** Asks for one round of the sensor's exchange and collects the streamed reply. */
    private suspend fun requestSensorRound(link: G7Link, round: Int): G7PCert {
        val step = "key exchange round ${round + 1}"
        // Installed before the request: the sensor starts streaming before its acknowledgement.
        val buffer = installCertificateBuffer(link)
        link.write(G7Characteristic.AUTHENTICATION, byteArrayOf(0x0A, round.toByte()), withResponse = true)
        val data = waitForCertificateBytes(link, buffer, G7PCert.BYTE_COUNT, step)
        log("$step: received the sensor's ${data.size}-byte round")
        return G7PCert.decode(data)
    }

    // ----------------------------------------------------------------------------------- AES challenge

    private suspend fun proveSharedKey(link: G7Link, authentication: MessageBuffer, sharedKey: ByteArray): AuthStatusMessage {
        val step = "challenge"
        val challenge = random(8)
        log("Challenge: sending ours as display type $displayType")
        link.write(G7Characteristic.AUTHENTICATION, byteArrayOf(0x02) + challenge + byteArrayOf(displayType.code.toByte()), withResponse = true)

        val response = waitForAuthentication(authentication, 0x03, step)
        if (response.size < 17) throw G7AuthException.UnexpectedResponse(step, response)
        if (!G7Aes.encryptChallenge(challenge, sharedKey).contentEquals(response.copyOfRange(1, 9))) {
            log("Challenge: the sensor's answer does not match our key")
            throw G7AuthException.ChallengeMismatch()
        }
        log("Challenge: the sensor's answer checked out")

        val answer = G7Aes.encryptChallenge(response.copyOfRange(9, 17), sharedKey)
        link.write(G7Characteristic.AUTHENTICATION, byteArrayOf(0x04) + answer, withResponse = true)

        val verdict = waitForAuthentication(authentication, 0x05, step)
        val status = AuthStatusMessage.parse(verdict) ?: throw G7AuthException.UnexpectedResponse(step, verdict)
        log("Challenge: verdict auth=${status.authStatus} bond=${status.bondStatus}")
        if (status.isRejected) throw G7AuthException.Rejected(status.authStatus, status.failureCode)
        return status
    }

    // ---------------------------------------------------------------------------- certificate exchange

    private suspend fun exchangeCertificates(link: G7Link, authentication: MessageBuffer) {
        val certificates = G7DexcomCredentials.certificates
        certificates.forEachIndexed { index, certificate -> exchangeCertificate(link, authentication, index, certificate) }

        // Terminator: one entry past the last, declared zero length.
        log("Certificates: sending the terminator")
        link.write(G7Characteristic.AUTHENTICATION, byteArrayOf(0x0B, certificates.size.toByte()) + 0L.u32leBytes(), withResponse = true)
        waitForAuthentication(authentication, 0x0B, "certificate terminator")
    }

    private suspend fun exchangeCertificate(link: G7Link, authentication: MessageBuffer, index: Int, certificate: ByteArray) {
        val step = "certificate $index"
        // Installed before the request: the sensor streams its certificate ahead of the acknowledgement.
        val buffer = installCertificateBuffer(link)
        link.write(G7Characteristic.AUTHENTICATION, byteArrayOf(0x0B, index.toByte()) + certificate.size.toLong().u32leBytes(), withResponse = true)

        val acknowledgement = waitForAuthentication(authentication, 0x0B, step)
        // `0B 00 <index> <length u16 LE>`. The sensor's certificates are not the size of ours.
        val expectedLength = if (acknowledgement.size >= 5) {
            acknowledgement.u16le(3).takeIf { it > 0 } ?: certificate.size
        } else {
            log("$step: short acknowledgement ${acknowledgement.toHex()}; using our own length")
            certificate.size
        }
        val theirs = waitForCertificateBytes(link, buffer, expectedLength, step)
        log("$step: received ${theirs.size} bytes, sending ours (${certificate.size} bytes)")
        writeCertificateBytes(link, certificate)
    }

    // ----------------------------------------------------------------------------------- key challenge

    private suspend fun answerKeyChallenge(link: G7Link, authentication: MessageBuffer) {
        val step = "key challenge"
        log("$step: sending a nonce")
        val buffer = installCertificateBuffer(link)
        link.write(G7Characteristic.AUTHENTICATION, byteArrayOf(0x0C) + random(16), withResponse = true)

        val acknowledgement = waitForAuthentication(authentication, 0x0C, step)
        if (acknowledgement.size < 18) throw G7AuthException.UnexpectedResponse(step, acknowledgement)

        // The sensor's own 64-byte block comes alongside. We do not check it: the sensor is checking us.
        waitForCertificateBytes(link, buffer, 64, step)

        val signature = G7ChallengeSigner.sign(acknowledgement)
        log("$step: sending our signature")
        writeCertificateBytes(link, signature)
    }

    // ---------------------------------------------------------------------------------------- finalize

    private suspend fun finalizeAndBond(link: G7Link, authentication: MessageBuffer) {
        log("Finalizing")
        link.write(G7Characteristic.AUTHENTICATION, byteArrayOf(0x06, 0x1E), withResponse = true)
        waitForAuthentication(authentication, 0x06, "finalize")

        log("Finalizing: the sensor starts Bluetooth bonding now; Android may ask to pair")
        link.write(G7Characteristic.AUTHENTICATION, byteArrayOf(0x07), withResponse = true)
        waitForAuthentication(authentication, 0x07, "bond request")
        link.onBondRequested()

        // The sensor confirms once the link is encrypted. A silent sensor counts as success: the key
        // and certificates are already accepted. The sensor has been seen to drop the link a few seconds
        // after 07 instead, so wait in short slices and stop when the link is gone.
        val deadline = System.currentTimeMillis() + BOND_CONFIRMATION_TIMEOUT_MS
        var confirmation: ByteArray? = null
        while (confirmation == null && System.currentTimeMillis() < deadline && link.isConnected) {
            confirmation = authentication.waitFor(1000) { it.isNotEmpty() && it.u8(0) == 0x08 }?.first
        }
        when {
            confirmation != null -> log("Bonded (${confirmation.toHex()})")
            !link.isConnected    -> log("The sensor dropped the link after the bond request; the key is in place and the next connection uses it")
            else                 -> log("No bond confirmation arrived; going on, because the handshake already succeeded")
        }
    }

    // ----------------------------------------------------------------------------------------- waiting

    private suspend fun waitForAuthentication(buffer: MessageBuffer, prefix: Int, step: String): ByteArray {
        val (match, discarded) = buffer.waitFor(stepTimeoutMs) { it.isNotEmpty() && it.u8(0) == prefix } ?: throw G7AuthException.Timeout(step)
        // The 0A acknowledgements of our round requests arrive after the bytes we wait for. Skipping them is normal.
        discarded.filter { it.isEmpty() || it.u8(0) != 0x0A }.forEach { log("Skipping an unexpected message: ${it.toHex()}") }
        return match
    }

    private fun installCertificateBuffer(link: G7Link): ChunkBuffer {
        val buffer = ChunkBuffer()
        link.setListener(G7Characteristic.CERTIFICATE) { buffer.append(it) }
        return buffer
    }

    private suspend fun waitForCertificateBytes(link: G7Link, buffer: ChunkBuffer, count: Int, step: String): ByteArray {
        try {
            return buffer.waitFor(count, stepTimeoutMs) ?: run {
                log("$step: timed out with ${buffer.size} of $count bytes")
                throw G7AuthException.Timeout(step)
            }
        } finally {
            link.setListener(G7Characteristic.CERTIFICATE, null)
        }
    }

    /** Sends [data] on the certificate characteristic in the piece size the sensor expects. */
    private suspend fun writeCertificateBytes(link: G7Link, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + CERTIFICATE_CHUNK_SIZE, data.size)
            link.write(G7Characteristic.CERTIFICATE, data.copyOfRange(offset, end), withResponse = false)
            offset = end
            // Without a pause a long payload outruns the sensor's reassembly.
            if (offset < data.size) delay(CERTIFICATE_CHUNK_INTERVAL_MS)
        }
    }

    companion object {

        /** Per-step deadline for a reconnect. The link is up and the sensor answers at once or not at all. */
        const val RECONNECT_STEP_TIMEOUT_MS = 10_000L

        /** Per-step deadline while pairing. Generous; the pairing run has its own overall limit. */
        const val PAIRING_STEP_TIMEOUT_MS = 60_000L

        const val BOND_CONFIRMATION_TIMEOUT_MS = 20_000L

        /** The sensor rebuilds data by byte count; 20 is the classic ATT size and a larger MTU buys nothing. */
        const val CERTIFICATE_CHUNK_SIZE = 20
        const val CERTIFICATE_CHUNK_INTERVAL_MS = 40L
    }
}

/**
 * Separate messages from the authentication characteristic. Each notification is one message, and a
 * message that arrives out of turn is skipped rather than read as the one a step waits for.
 */
internal class MessageBuffer {

    private val channel = Channel<ByteArray>(Channel.UNLIMITED)

    fun append(message: ByteArray) {
        channel.trySend(message)
    }

    /** Waits for the first message matching [predicate]. Returns it with the older messages skipped on the way. */
    suspend fun waitFor(timeoutMs: Long, predicate: (ByteArray) -> Boolean): Pair<ByteArray, List<ByteArray>>? {
        val discarded = mutableListOf<ByteArray>()
        return withTimeoutOrNull(timeoutMs) {
            var message = channel.receive()
            while (!predicate(message)) {
                discarded.add(message)
                message = channel.receive()
            }
            message to discarded.toList()
        }
    }
}

/** Collects the certificate characteristic's byte stream, which comes in 20-byte pieces with no framing. */
internal class ChunkBuffer {

    private val storage = ByteArrayOutputStream()
    private val count = MutableStateFlow(0)

    val size: Int get() = count.value

    fun append(chunk: ByteArray) {
        synchronized(storage) {
            storage.write(chunk)
            count.value = storage.size()
        }
    }

    suspend fun waitFor(byteCount: Int, timeoutMs: Long): ByteArray? {
        withTimeoutOrNull(timeoutMs) { count.first { it >= byteCount } } ?: return null
        return synchronized(storage) { storage.toByteArray().copyOf(byteCount) }
    }
}

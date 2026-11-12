package app.aaps.plugins.libre3.session

import app.aaps.plugins.libre3.crypto.Libre3AesCcm
import app.aaps.plugins.libre3.crypto.Libre3Phase6Response
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The command clock, played against a written down sensor. No radio, no Android.
 *
 * The order of the commands comes from LibreCRKit `PairingFlow.swift` at pin `a86b92f`.
 */
class Libre3SessionAuthTest {

    private val blePin = byteArrayOf(0x32, 0x25, 0xEC.toByte(), 0x72)
    private val phoneR2 = ByteArray(16) { (it + 0x40).toByte() }
    private val sensorR1 = ByteArray(16) { (it + 0x80).toByte() }
    private val nonce = ByteArray(7) { (it + 0x10).toByte() }

    /**
     * A sensor that answers from a script. It writes down every command byte it was sent, so the
     * test can prove which commands went out and, just as important, which never did.
     */
    private class ScriptedSensor(
        private val commandAnswers: MutableList<ByteArray> = mutableListOf(),
        private val certAnswers: MutableList<ByteArray> = mutableListOf(),
        private val challengeAnswers: MutableList<ByteArray> = mutableListOf(),
    ) : Libre3HandshakeTransport {

        val commandsSent = mutableListOf<Byte>()
        val writes = mutableListOf<Pair<Libre3HandshakeChannel, Int>>()

        fun answerCommand(vararg first: Byte) = apply { first.forEach { commandAnswers.add(byteArrayOf(it)) } }
        fun answerCert(payload: ByteArray) = apply { certAnswers.add(payload) }
        fun answerChallenge(payload: ByteArray) = apply { challengeAnswers.add(payload) }

        override fun writeCommand(command: Byte) {
            commandsSent.add(command)
        }

        override fun write(channel: Libre3HandshakeChannel, payload: ByteArray) {
            writes.add(channel to payload.size)
        }

        override fun awaitNotify(channel: Libre3HandshakeChannel, expectedSize: Int?): ByteArray {
            val queue = when (channel) {
                Libre3HandshakeChannel.COMMAND   -> commandAnswers
                Libre3HandshakeChannel.CERT      -> certAnswers
                Libre3HandshakeChannel.CHALLENGE -> challengeAnswers
            }
            if (queue.isEmpty()) throw Libre3HandshakeException("the written down sensor has nothing left to say")
            return queue.removeAt(0)
        }
    }

    /** The block maker of the pairing plane. Ordinary AES here, which is enough for the clock. */
    private val phase5Block = Libre3AesCcm.standardAes(ByteArray(16) { it.toByte() })

    /** Builds what a sensor would send back in the last step. */
    private fun phase6Wire(echoedR2: ByteArray = phoneR2, echoedR1: ByteArray = sensorR1): ByteArray {
        val kEnc = ByteArray(16) { (it + 0x20).toByte() }
        val ivEnc = ByteArray(8) { (it + 0x30).toByte() }
        val sealed = Libre3AesCcm.encrypt(nonce, echoedR2 + echoedR1 + kEnc + ivEnc, tagLength = 4, aes = phase5Block)
        return sealed.ciphertext + sealed.tag + nonce
    }

    @Test
    fun `a reconnect sends the short command and never the first pairing command`() {
        val sensor = ScriptedSensor()
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(sensorR1 + nonce)
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(phase6Wire())

        Libre3SessionAuth(sensor).runCachedReconnect(blePin, phase5Block, phoneR2)

        assertThat(sensor.commandsSent).containsExactly(
            Libre3SessionAuth.START_AUTHORIZATION,
            Libre3SessionAuth.SEND_CHALLENGE_LOAD_DONE,
        ).inOrder()
        assertThat(sensor.commandsSent).doesNotContain(Libre3SessionAuth.START_AUTHENTICATION)
    }

    @Test
    fun `a reconnect gives back the keys of the new session`() {
        val sensor = ScriptedSensor()
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(sensorR1 + nonce)
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(phase6Wire())

        val material = Libre3SessionAuth(sensor).runCachedReconnect(blePin, phase5Block, phoneR2)

        assertThat(material.kEnc).hasLength(16)
        assertThat(material.ivEnc).hasLength(8)
    }

    @Test
    fun `the Phase 5 message goes out as 54 bytes on the challenge channel`() {
        val sensor = ScriptedSensor()
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(sensorR1 + nonce)
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(phase6Wire())

        Libre3SessionAuth(sensor).runCachedReconnect(blePin, phase5Block, phoneR2)

        assertThat(sensor.writes).containsExactly(Libre3HandshakeChannel.CHALLENGE to 54)
    }

    @Test
    fun `a reconnect that a sensor refuses does not go on to the first pairing command`() {
        val sensor = ScriptedSensor().answerCommand(0x07)

        assertThrows<Libre3HandshakeException> {
            Libre3SessionAuth(sensor).runCachedReconnect(blePin, phase5Block, phoneR2)
        }

        // The whole point: the failure stops here. It never turns into a first pairing.
        assertThat(sensor.commandsSent).containsExactly(Libre3SessionAuth.START_AUTHORIZATION)
        assertThat(sensor.commandsSent).doesNotContain(Libre3SessionAuth.START_AUTHENTICATION)
    }

    @Test
    fun `a sensor that echoes the wrong random part is refused`() {
        val sensor = ScriptedSensor()
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(sensorR1 + nonce)
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(phase6Wire(echoedR2 = ByteArray(16) { 0x55 }))

        assertThrows<Libre3HandshakeException> {
            Libre3SessionAuth(sensor).runCachedReconnect(blePin, phase5Block, phoneR2)
        }
    }

    @Test
    fun `the first pairing follows the command order up to the two public points`() {
        val sensor = ScriptedSensor()
            .answerCommand(Libre3SessionAuth.CERTIFICATE_ACCEPTED)
            .answerCommand(Libre3SessionAuth.CERTIFICATE_READY)
            .answerCert(ByteArray(140))
            .answerCommand(Libre3SessionAuth.EPHEMERAL_READY)
            .answerCert(ByteArray(65))

        val afterEph = Libre3SessionAuth(sensor).runFirstPairUntilEphemeral(ByteArray(162), ByteArray(72))

        assertThat(sensor.commandsSent).containsExactly(
            Libre3SessionAuth.START_AUTHENTICATION,
            Libre3SessionAuth.LOAD_CERTIFICATE,
            Libre3SessionAuth.SEND_CERTIFICATE_LOAD_DONE,
            Libre3SessionAuth.GET_CERTIFICATE,
            Libre3SessionAuth.VALIDATE_CERTIFICATE,
            Libre3SessionAuth.SEND_EPHEMERAL_DONE,
        ).inOrder()
        assertThat(sensor.commandsSent).doesNotContain(Libre3SessionAuth.START_AUTHORIZATION)
        assertThat(afterEph.sensorCert).hasLength(140)
        assertThat(afterEph.sensorEphemeralPublicKey).hasLength(65)
    }

    @Test
    fun `after the two public points the last step is the short 0x11 path`() {
        val sensor = ScriptedSensor()
            .answerCommand(Libre3SessionAuth.CERTIFICATE_ACCEPTED)
            .answerCommand(Libre3SessionAuth.CERTIFICATE_READY)
            .answerCert(ByteArray(140))
            .answerCommand(Libre3SessionAuth.EPHEMERAL_READY)
            .answerCert(ByteArray(65))
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(sensorR1 + nonce)
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(phase6Wire())

        val auth = Libre3SessionAuth(sensor)
        auth.runFirstPairUntilEphemeral(ByteArray(162), ByteArray(72))
        auth.runAuthorization(blePin, phase5Block, phoneR2)

        assertThat(sensor.commandsSent).containsExactly(
            Libre3SessionAuth.START_AUTHENTICATION,
            Libre3SessionAuth.LOAD_CERTIFICATE,
            Libre3SessionAuth.SEND_CERTIFICATE_LOAD_DONE,
            Libre3SessionAuth.GET_CERTIFICATE,
            Libre3SessionAuth.VALIDATE_CERTIFICATE,
            Libre3SessionAuth.SEND_EPHEMERAL_DONE,
            Libre3SessionAuth.START_AUTHORIZATION,
            Libre3SessionAuth.SEND_CHALLENGE_LOAD_DONE,
        ).inOrder()
    }

    @Test
    fun `the first pairing writes the certificate and the padded public point`() {
        val sensor = ScriptedSensor()
            .answerCommand(Libre3SessionAuth.CERTIFICATE_ACCEPTED)
            .answerCommand(Libre3SessionAuth.CERTIFICATE_READY)
            .answerCert(ByteArray(140))
            .answerCommand(Libre3SessionAuth.EPHEMERAL_READY)
            .answerCert(ByteArray(65))

        Libre3SessionAuth(sensor).runFirstPairUntilEphemeral(ByteArray(162), ByteArray(72))

        assertThat(sensor.writes).containsExactly(
            Libre3HandshakeChannel.CERT to 162,
            Libre3HandshakeChannel.CERT to 72,
        ).inOrder()
    }

    @Test
    fun `a first pairing stops as soon as the sensor refuses a step`() {
        val sensor = ScriptedSensor().answerCommand(0x06)

        assertThrows<Libre3HandshakeException> {
            Libre3SessionAuth(sensor).runFirstPairUntilEphemeral(ByteArray(162), ByteArray(72))
        }

        assertThat(sensor.commandsSent).containsExactly(
            Libre3SessionAuth.START_AUTHENTICATION,
            Libre3SessionAuth.LOAD_CERTIFICATE,
            Libre3SessionAuth.SEND_CERTIFICATE_LOAD_DONE,
        ).inOrder()
    }

    @Test
    fun `a certificate or a public point of the wrong size is refused before anything is sent`() {
        val sensor = ScriptedSensor()

        assertThrows<IllegalArgumentException> {
            Libre3SessionAuth(sensor).runFirstPairUntilEphemeral(ByteArray(161), ByteArray(72))
        }
        assertThrows<IllegalArgumentException> {
            Libre3SessionAuth(sensor).runFirstPairUntilEphemeral(ByteArray(162), ByteArray(65))
        }
        assertThat(sensor.commandsSent).isEmpty()
    }

    @Test
    fun `a sensor challenge of the wrong size is refused`() {
        val sensor = ScriptedSensor()
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(ByteArray(22))

        assertThrows<Libre3HandshakeException> {
            Libre3SessionAuth(sensor).runCachedReconnect(blePin, phase5Block, phoneR2)
        }
    }

    @Test
    fun `a Phase 6 answer of the wrong size is refused`() {
        val sensor = ScriptedSensor()
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(sensorR1 + nonce)
            .answerCommand(Libre3SessionAuth.CHALLENGE_LOAD_DONE)
            .answerChallenge(ByteArray(Libre3Phase6Response.WIRE_SIZE - 1))

        assertThrows<Exception> {
            Libre3SessionAuth(sensor).runCachedReconnect(blePin, phase5Block, phoneR2)
        }
    }
}

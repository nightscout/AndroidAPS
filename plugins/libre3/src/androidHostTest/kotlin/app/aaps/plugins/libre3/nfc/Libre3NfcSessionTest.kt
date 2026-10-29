package app.aaps.plugins.libre3.nfc

import app.aaps.plugins.libre3.identity.Libre3IdentityStore
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The whole NFC step with a fake tag. No Android radio, no `NfcAdapter`, no real settings file.
 *
 * The answers are the captured frames from the LibreCRKit tests at pin `a86b92f`.
 */
class Libre3NfcSessionTest {

    private companion object {

        const val FRESH_SENSOR = "00a50001000200010060541e020401040c0130525243393839415151ff17"
        const val RUNNING_SENSOR = "00a50001000200010060541e020401040c04305252433938394151c6ca"
        const val ACTIVATION_ANSWER = "00a50058f9b8df22cc3225ec7200000000ad06"
        const val TEST_RECEIVER_ID = 0x6F0D8378
        /** One second after the pinned vector time, because the sensor is sent `now - 1`. */
        const val NOW_MS = 1_777_216_509_000L

        /** Answer of a sensor that was already running when it was taken over. */
        const val TAKEOVER_ANSWER = "00a50058f9b8df22cc02bafbbbfc2bee698e6e"
    }

    /** Remembers what was written and how many times, so the order of the steps can be checked. */
    private class FakeStore(
        private val storeWrites: Boolean = true,
        private val receiverIdFails: Boolean = false,
    ) : Libre3IdentityStore {

        var saved: Libre3SensorIdentity? = null
        var saveCount = 0

        override fun receiverId(): Int {
            check(!receiverIdFails) { "the receiver id of this phone could not be stored" }
            return TEST_RECEIVER_ID
        }

        override fun saveIdentityAndWait(identity: Libre3SensorIdentity): Boolean {
            saved = identity
            saveCount++
            return storeWrites
        }
    }

    /** Answers a fixed list of frames and keeps every frame that was sent to it. */
    private class FakeTag(private vararg val answers: String) : Libre3NfcTransceiver {

        val sent = mutableListOf<String>()
        private var index = 0

        override fun transceive(frame: ByteArray): ByteArray {
            sent.add(frame.joinToString("") { "%02x".format(it) })
            if (index >= answers.size) throw Libre3NfcException("the fake tag has no answer left")
            return answers[index++].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }

    private fun session(store: Libre3IdentityStore) = Libre3NfcSession(store, nowMs = { NOW_MS })

    private fun vetoedSession(store: Libre3IdentityStore, veto: (String) -> Boolean) =
        Libre3NfcSession({ store }, nowMs = { NOW_MS }, veto = veto)

    @Test
    fun `a vetoed sensor is refused before the activation command is sent`() {
        // The whole point of the veto: one step further the PIN would already be replaced and
        // the store would already have dropped the other slot's keys.
        val store = FakeStore()
        val tag = FakeTag(FRESH_SENSOR, ACTIVATION_ANSWER)

        val thrown = assertThrows<Libre3NfcException> {
            vetoedSession(store) { true }.scan(tag)
        }

        assertThat(thrown.failure).isEqualTo(Libre3NfcFailure.SAME_SENSOR_OTHER_SLOT)
        // Only the patch info frame went out, and nothing was written.
        assertThat(tag.sent).hasSize(1)
        assertThat(tag.sent[0]).isEqualTo("02a17a")
        assertThat(store.saveCount).isEqualTo(0)
        assertThat(store.saved).isNull()
    }

    @Test
    fun `the veto is asked with the serial read from the sensor`() {
        val store = FakeStore()
        val tag = FakeTag(FRESH_SENSOR, ACTIVATION_ANSWER)
        val asked = mutableListOf<String>()

        vetoedSession(store) { serial -> asked.add(serial); false }.scan(tag)

        assertThat(asked).containsExactly("0RRC989AQ")
        assertThat(store.saveCount).isEqualTo(1)
    }

    @Test
    fun `the store supplier is read once per scan, so a slot change cannot split a scan`() {
        val store = FakeStore()
        val tag = FakeTag(FRESH_SENSOR, ACTIVATION_ANSWER)
        var supplied = 0

        Libre3NfcSession({ supplied++; store }, nowMs = { NOW_MS }).scan(tag)

        assertThat(supplied).isEqualTo(1)
    }

    @Test
    fun `a fresh sensor gets the activate command`() {
        val store = FakeStore()
        val tag = FakeTag(FRESH_SENSOR, ACTIVATION_ANSWER)

        val result = session(store).scan(tag)

        assertThat(tag.sent[0]).isEqualTo("02a17a")
        assertThat(result.commandUsed).isEqualTo(Libre3NfcUids.CMD_ACTIVATE)
        assertThat(tag.sent[1]).isEqualTo("02a07afc2bee6978830d6fb471")
    }

    @Test
    fun `a running sensor gets the switch receiver command, never the activate command`() {
        val store = FakeStore()
        val tag = FakeTag(RUNNING_SENSOR, ACTIVATION_ANSWER)

        val result = session(store).scan(tag)

        assertThat(result.commandUsed).isEqualTo(Libre3NfcUids.CMD_SWITCH_RECEIVER)
        assertThat(tag.sent[1]).isEqualTo("02a87afc2bee6978830d6fb471")
        assertThat(tag.sent[1]).doesNotContain("02a07a")
    }

    @Test
    fun `every frame carries the sensor manufacturer code`() {
        val store = FakeStore()
        val tag = FakeTag(FRESH_SENSOR, ACTIVATION_ANSWER)

        session(store).scan(tag)

        tag.sent.forEach { frame -> assertThat(frame.substring(4, 6)).isEqualTo("7a") }
    }

    @Test
    fun `the sensor is stored with its address, PIN and warm-up before Bluetooth is allowed`() {
        val store = FakeStore()

        val result = session(store).scan(FakeTag(FRESH_SENSOR, ACTIVATION_ANSWER))

        val saved = store.saved
        assertThat(saved).isNotNull()
        assertThat(store.saveCount).isEqualTo(1)
        assertThat(saved!!.serialNumber).isEqualTo("0RRC989AQ")
        assertThat(saved.bleAddress).isEqualTo("CC:22:DF:B8:F9:58")
        assertThat(saved.blePin.joinToString("") { "%02x".format(it) }).isEqualTo("3225ec72")
        assertThat(saved.warmupMinutes).isEqualTo(60)
        assertThat(saved.wearDurationMinutes).isEqualTo(21600)
        assertThat(saved.generation).isEqualTo(1)
        assertThat(saved.activatedAtMs).isEqualTo(NOW_MS)
        assertThat(result.readyForBle).isTrue()
    }

    @Test
    fun `a sensor taken over keeps its own activation time, not the time of this scan`() {
        val store = FakeStore()

        val result = session(store).scan(FakeTag(RUNNING_SENSOR, TAKEOVER_ANSWER))

        // The sensor answered with 0x69ee2bfc, which is 1777216508 seconds since 1970. Using the
        // scan time here would push every sample of a sensor that is days old into the future.
        assertThat(result.activation.activationEpochSeconds).isEqualTo(1_777_216_508L)
        assertThat(store.saved!!.activatedAtMs).isEqualTo(1_777_216_508_000L)
        assertThat(store.saved!!.activatedAtMs).isNotEqualTo(NOW_MS)
    }

    @Test
    fun `a sensor activated now uses the phone clock, because it reports no time of its own`() {
        val store = FakeStore()

        val result = session(store).scan(FakeTag(FRESH_SENSOR, ACTIVATION_ANSWER))

        assertThat(result.activation.activationEpochSeconds).isEqualTo(0L)
        assertThat(store.saved!!.activatedAtMs).isEqualTo(NOW_MS)
    }

    @Test
    fun `the sensor is given a time one second behind the phone clock`() {
        val tag = FakeTag(FRESH_SENSOR, ACTIVATION_ANSWER)

        session(FakeStore()).scan(tag)

        // NOW_MS is 1777216509000, so the time on the wire is 1777216508, the pinned vector.
        assertThat(tag.sent[1]).isEqualTo("02a07afc2bee6978830d6fb471")
    }

    @Test
    fun `nothing is sent to the sensor when this phone cannot store its receiver id`() {
        val store = FakeStore(receiverIdFails = true)
        val tag = FakeTag(FRESH_SENSOR, ACTIVATION_ANSWER)

        assertThrows<Libre3NfcException> { session(store).scan(tag) }

        // Nothing at all went out. The receiver id is read before the tag is touched, so a phone
        // that cannot store its id never even sends the patch info read, and no sensor is bound to
        // an id we could not rebuild. This used to be one frame, the A1 read, before the id was
        // moved ahead of it to keep the gap between A1 and A8 short.
        assertThat(tag.sent).isEmpty()
        assertThat(store.saveCount).isEqualTo(0)
    }

    @Test
    fun `Bluetooth stays blocked when the sensor could not be written to disk`() {
        val store = FakeStore(storeWrites = false)

        val result = session(store).scan(FakeTag(FRESH_SENSOR, ACTIVATION_ANSWER))

        assertThat(result.readyForBle).isFalse()
    }

    @Test
    fun `each failure carries a reason the screen can translate, never a ready made sentence`() {
        val notASensor = runCatching { session(FakeStore()).scan(FakeTag("000102030405060708090a0b0c0d0e0f")) }
        val brokenAnswer = runCatching { session(FakeStore()).scan(FakeTag(FRESH_SENSOR, "00a50058f9b8df22cc")) }
        val phoneFailed = runCatching { session(FakeStore(receiverIdFails = true)).scan(FakeTag(FRESH_SENSOR)) }

        assertThat((notASensor.exceptionOrNull() as Libre3NfcException).failure)
            .isEqualTo(Libre3NfcFailure.NOT_A_LIBRE3_SENSOR)
        assertThat((brokenAnswer.exceptionOrNull() as Libre3NfcException).failure)
            .isEqualTo(Libre3NfcFailure.SENSOR_REFUSED)
        assertThat((phoneFailed.exceptionOrNull() as Libre3NfcException).failure)
            .isEqualTo(Libre3NfcFailure.PHONE_STORAGE_FAILED)
    }

    @Test
    fun `a tag that is not a Libre 3 stops the scan before any activation command`() {
        val store = FakeStore()
        // Some other ISO 15693 tag answering something that is not a patch info frame.
        val tag = FakeTag("000102030405060708090a0b0c0d0e0f")

        assertThrows<Libre3NfcException> { session(store).scan(tag) }

        assertThat(tag.sent).hasSize(1)
        assertThat(store.saveCount).isEqualTo(0)
    }

    @Test
    fun `a broken activation answer stores nothing`() {
        val store = FakeStore()
        val tag = FakeTag(FRESH_SENSOR, "00a50058f9b8df22cc")

        assertThrows<Libre3NfcException> { session(store).scan(tag) }

        assertThat(store.saveCount).isEqualTo(0)
    }
}

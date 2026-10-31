package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.utils.MasterDocSigning
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * What a client takes from the master's settings docs. A client token may write to `settings`, so a doc the master did
 * not sign must not change settings, the client list, or the "master is alive" sign.
 */
internal class MasterDocVerifierTest {

    private val preferences = mock<Preferences>()
    private val strings = mutableMapOf<String, String>()
    private val longs = mutableMapOf<String, Long>()
    private lateinit var sut: MasterDocVerifier

    private val master = MasterDocSigning.newKeyPair()

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(any<StringNonKey>())).thenAnswer { strings[(it.arguments[0] as StringNonKey).key] ?: "" }
        whenever(preferences.put(any<StringNonKey>(), any<String>())).thenAnswer { strings[(it.arguments[0] as StringNonKey).key] = it.arguments[1] as String; Unit }
        whenever(preferences.get(any<LongNonKey>())).thenAnswer { longs[(it.arguments[0] as LongNonKey).key] ?: 0L }
        whenever(preferences.put(any<LongNonKey>(), any<Long>())).thenAnswer { longs[(it.arguments[0] as LongNonKey).key] = it.arguments[1] as Long; Unit }
        whenever(preferences.get(StringKey.NsClientUrl)).thenAnswer { strings[StringKey.NsClientUrl.key] ?: "" }
        strings[StringKey.NsClientUrl.key] = "https://ns.example"
        sut = MasterDocVerifier(preferences, mock<NSClientRepository>(), mock<AAPSLogger>())
    }

    private fun text(signedAt: Long, version: String = "4.0") = """{"signedAt":$signedAt,"runningConfig":{"version":"$version"}}"""

    private fun doc(identifier: String, text: String, signature: String? = MasterDocSigning.sign(master.privateKey, identifier, text)): JsonObject =
        buildJsonObject {
            put("identifier", identifier)
            put("schemaVersion", 2)
            put(MasterDocVerifier.SIGNED, text)
            signature?.let { put(MasterDocVerifier.SIGNATURE, it) }
        }

    @Test
    fun `a doc the master signed is taken with its signing time`() {
        sut.keepKey(master.publicKey)

        val opened = sut.open(SettingsIdentifiers.COLD, doc(SettingsIdentifiers.COLD, text(1_000L)))

        assertThat(opened?.configuration?.version).isEqualTo("4.0")
        assertThat(opened?.signedAt).isEqualTo(1_000L)
    }

    @Test
    fun `a changed doc is refused`() {
        sut.keepKey(master.publicKey)
        val genuine = doc(SettingsIdentifiers.COLD, text(1_000L))
        val changed = JsonObject(genuine + (MasterDocVerifier.SIGNED to JsonPrimitive(text(1_000L, version = "9.9"))))

        assertThat(sut.open(SettingsIdentifiers.COLD, changed)).isNull()
    }

    /** With the key known, an unsigned doc is somebody else's: the old shape too, which has no signature at all. */
    @Test
    fun `an unsigned doc is refused once the key is known`() {
        sut.keepKey(master.publicKey)
        val old = buildJsonObject {
            put("identifier", SettingsIdentifiers.COLD)
            put("runningConfig", buildJsonObject { put("version", "9.9") })
        }

        assertThat(sut.open(SettingsIdentifiers.COLD, old)).isNull()
        assertThat(sut.open(SettingsIdentifiers.COLD, doc(SettingsIdentifiers.COLD, text(1_000L), signature = null))).isNull()
    }

    @Test
    fun `a doc signed by another key is refused`() {
        sut.keepKey(master.publicKey)
        val other = MasterDocSigning.newKeyPair()
        val t = text(1_000L)

        assertThat(sut.open(SettingsIdentifiers.COLD, doc(SettingsIdentifiers.COLD, t, MasterDocSigning.sign(other.privateKey, SettingsIdentifiers.COLD, t)))).isNull()
    }

    /** A genuine but older doc put back by somebody else: refused. The same doc again (the catch-up load) is fine. */
    @Test
    fun `an older doc is refused as a replay and the same one again is taken`() {
        sut.keepKey(master.publicKey)
        val older = doc(SettingsIdentifiers.COLD, text(1_000L))
        val newer = doc(SettingsIdentifiers.COLD, text(2_000L))

        assertThat(sut.open(SettingsIdentifiers.COLD, newer)).isNotNull()
        assertThat(sut.open(SettingsIdentifiers.COLD, newer)).isNotNull()
        assertThat(sut.open(SettingsIdentifiers.COLD, older)).isNull()
    }

    /** The cold and the hot doc each keep their own order. */
    @Test
    fun `the hot doc has its own order`() {
        sut.keepKey(master.publicKey)
        sut.open(SettingsIdentifiers.COLD, doc(SettingsIdentifiers.COLD, text(2_000L)))

        assertThat(sut.open(SettingsIdentifiers.STATE, doc(SettingsIdentifiers.STATE, text(1_000L)))).isNotNull()
    }

    /** Without a key (a phone set up by hand, or before the first pong) a doc is taken as before, also the old shape. */
    @Test
    fun `without a key a doc is taken as before`() {
        val old = buildJsonObject {
            put("identifier", SettingsIdentifiers.COLD)
            put("runningConfig", buildJsonObject { put("version", "4.0") })
        }

        assertThat(sut.open(SettingsIdentifiers.COLD, old)?.signedAt).isNull()
        assertThat(sut.open(SettingsIdentifiers.COLD, doc(SettingsIdentifiers.COLD, text(1_000L), signature = null))?.configuration?.version).isEqualTo("4.0")
    }

    /** A key belongs to the Nightscout it was given for: with another address it is another master. */
    @Test
    fun `a key for another Nightscout is not used`() {
        sut.keepKey(master.publicKey)
        strings[StringKey.NsClientUrl.key] = "https://other.example"
        val old = buildJsonObject {
            put("identifier", SettingsIdentifiers.COLD)
            put("runningConfig", buildJsonObject { put("version", "4.0") })
        }

        assertThat(sut.open(SettingsIdentifiers.COLD, old)).isNotNull()
    }

    /** A new key is a new master with its own clock: its first doc is not refused for the old one's times. */
    @Test
    fun `a new key starts the order again`() {
        sut.keepKey(master.publicKey)
        sut.open(SettingsIdentifiers.COLD, doc(SettingsIdentifiers.COLD, text(5_000L)))
        val next = MasterDocSigning.newKeyPair()
        sut.keepKey(next.publicKey)
        val t = text(1_000L)

        assertThat(sut.open(SettingsIdentifiers.COLD, doc(SettingsIdentifiers.COLD, t, MasterDocSigning.sign(next.privateKey, SettingsIdentifiers.COLD, t)))).isNotNull()
    }
}

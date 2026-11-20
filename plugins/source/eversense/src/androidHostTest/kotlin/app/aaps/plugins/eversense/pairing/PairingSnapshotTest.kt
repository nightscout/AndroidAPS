package app.aaps.plugins.eversense.pairing

import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.util.StorageKeys
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/** What the pairing shows, read from the driver's stored JSON object, and what the user is asked to fix. */
class PairingSnapshotTest {

    private fun deviceState(
        username: String = "",
        password: String = "",
        model: String = "",
        address: String? = null,
        name: String? = null,
        refused: Boolean? = null
    ): String {
        val secure = EversenseSecureState().also {
            it.username = username
            it.password = password
            it.model = model
        }
        val values = buildMap {
            put(StorageKeys.SECURE_STATE, JsonPrimitive(Json.encodeToString(secure)))
            address?.let { put(StorageKeys.REMOTE_DEVICE_KEY, JsonPrimitive(it)) }
            name?.let { put(StorageKeys.REMOTE_DEVICE_NAME, JsonPrimitive(it)) }
            refused?.let { put(StorageKeys.LOGIN_REFUSED, JsonPrimitive(it)) }
        }
        return JsonObject(values).toString()
    }

    @Test fun `nothing stored is nothing paired`() {
        val snapshot = PairingSnapshot.of("{}")
        assertThat(snapshot.account).isNull()
        assertThat(snapshot.transmitter).isNull()
        assertThat(cgmAttention(snapshot.account, snapshot.transmitter, snapshot.loginRefused)).isEqualTo(CgmAttention.NOT_PAIRED)
    }

    @Test fun `a stored login and transmitter are paired`() {
        val snapshot = PairingSnapshot.of(deviceState("user", "secret", "E365_OTHER", "00:11:22:33:44:55", "T123456"))
        assertThat(snapshot.account).isEqualTo(EversenseAccount("user", EversenseModel.E365_OTHER))
        assertThat(snapshot.transmitter).isEqualTo("T123456")
        assertThat(cgmAttention(snapshot.account, snapshot.transmitter, snapshot.loginRefused)).isNull()
    }

    /** A transmitter paired before the name was kept shows its address; a login from before the model was asked has none. */
    @Test fun `older pairings show what is known`() {
        val snapshot = PairingSnapshot.of(deviceState("user", "secret", address = "00:11:22:33:44:55"))
        assertThat(snapshot.transmitter).isEqualTo("00:11:22:33:44:55")
        assertThat(snapshot.account).isEqualTo(EversenseAccount("user", null))
    }

    /** No login, no start: a transmitter alone is not paired either, a 365 cannot connect without the login. */
    @Test fun `a transmitter without a login is not paired`() {
        val snapshot = PairingSnapshot.of(deviceState(address = "00:11:22:33:44:55", name = "T123456"))
        assertThat(cgmAttention(snapshot.account, snapshot.transmitter, snapshot.loginRefused)).isEqualTo(CgmAttention.NOT_PAIRED)
    }

    @Test fun `a refused login asks to sign in again`() {
        val snapshot = PairingSnapshot.of(deviceState("user", "secret", "E3", "00:11:22:33:44:55", "T123456", refused = true))
        assertThat(cgmAttention(snapshot.account, snapshot.transmitter, snapshot.loginRefused)).isEqualTo(CgmAttention.SIGN_IN_FAILED)
    }

    /** Not paired comes first: there is no transmitter to sign in for. */
    @Test fun `not paired wins over a refused login`() {
        assertThat(cgmAttention(null, null, signInRefused = true)).isEqualTo(CgmAttention.NOT_PAIRED)
    }

    @Test fun `a broken store reads as nothing paired`() {
        assertThat(PairingSnapshot.of("not json").account).isNull()
    }
}

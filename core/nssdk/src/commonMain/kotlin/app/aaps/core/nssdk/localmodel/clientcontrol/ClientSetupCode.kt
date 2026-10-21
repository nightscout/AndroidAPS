package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * What the QR code for a new client holds: the Nightscout address and the client's own token, made by the master. When
 * the code pairs the phone too, it also holds the [pairing].
 *
 * The whole code is SENSITIVE: whoever reads it gets a working Nightscout token, and with [pairing] the right to send
 * commands until the master replaces the secret. The master shows it with screenshots blocked.
 */
@Serializable
data class ClientSetupCode(
    /** Marks the text as an AAPS set-up code, so the scanner can refuse a QR code of anything else. */
    val type: String = TYPE,
    /** Master and client must run the same version: a code of another version is refused, not guessed at. */
    val v: Int = VERSION,
    /** The Nightscout address as the master has it, for example `https://my.nightscout.host`. */
    val url: String,
    /** SENSITIVE: the client's Nightscout token. */
    val token: String,
    /** SENSITIVE: the pairing, when the code also pairs the client with the master. */
    val pairing: PairingPayload? = null,
    /** The name this phone was added with on the master. The client takes it as its instance name when it has none. */
    val name: String? = null
) {

    /** Hides the token and the secret, so a log line that prints a code cannot leak them. */
    override fun toString(): String = "ClientSetupCode(v=$v, url=$url, token=****, pairing=${if (pairing == null) "none" else "****"}, name=$name)"

    sealed interface Decoded {
        data class Ok(val code: ClientSetupCode) : Decoded

        /** A QR code, but not one made by AAPS. */
        data object NotACode : Decoded

        /** An AAPS code of another version: the master and this phone do not run the same AAPS. */
        data object OtherVersion : Decoded
    }

    companion object {

        const val TYPE = "aaps-client-setup"
        const val VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        fun encode(code: ClientSetupCode): String = json.encodeToString(serializer(), code)

        fun decode(text: String): Decoded {
            val tree = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return Decoded.NotACode
            // Read from the text, not from the decoded object: there the defaults would fill in a missing type or version.
            if ((tree["type"] as? JsonPrimitive)?.contentOrNull != TYPE) return Decoded.NotACode
            if ((tree["v"] as? JsonPrimitive)?.intOrNull != VERSION) return Decoded.OtherVersion
            val code = runCatching { json.decodeFromJsonElement(serializer(), tree) }.getOrNull() ?: return Decoded.NotACode
            if (code.url.isBlank() || code.token.isBlank()) return Decoded.NotACode
            return Decoded.Ok(code)
        }
    }
}

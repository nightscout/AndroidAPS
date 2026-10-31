package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.Serializable

/**
 * The master's answer to a hello that carries a public key (`ClientControlMessage.Hello.publicKey`), written to
 * `aaps_clientcontrol_key_<clientId>` and signed with the pairing's OLD secret, the only one the client has yet. See
 * `ClientControlKeyExchangeCrypto`.
 *
 * The master deletes the document as soon as the client writes with the new secret, so the encrypted token stays in
 * Nightscout only for seconds. It could not be read anyway without the client's private key.
 */
@Serializable
data class KeyExchangeEnvelope(
    val clientId: String,
    /** The client's public key from the hello, sent back: a client ignores an answer to another hello of its own. */
    val clientPublicKey: String,
    val masterPublicKey: String,
    /** The client's own Nightscout token, encrypted with the token key of the exchange. Null: the client keeps its token. */
    val tokenIv: String? = null,
    val token: String? = null,
    val timestamp: Long,
    val signature: String
) {

    /** HMAC input over every field except the signature. Wire contract: do not change. */
    fun canonicalString(): String = "$clientId|$clientPublicKey|$masterPublicKey|${tokenIv ?: ""}|${token ?: ""}|$timestamp"
}

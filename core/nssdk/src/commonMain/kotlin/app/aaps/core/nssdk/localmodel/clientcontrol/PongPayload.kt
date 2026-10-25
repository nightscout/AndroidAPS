package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.Serializable

/**
 * The payload of the master's answer to a ping, as JSON in [AckEnvelope.payload], which the pairing secret signs.
 *
 * [signingKey] is the master's public key for its settings docs (`MasterDocSigning`). Every paired client pings on
 * each connect, so this is how it learns the key, also a client paired before the docs were signed.
 */
@Serializable
data class PongPayload(val signingKey: String? = null)

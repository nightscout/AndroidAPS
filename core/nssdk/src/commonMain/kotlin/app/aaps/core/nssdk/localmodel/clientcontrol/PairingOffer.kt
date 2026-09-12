package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.Serializable

/**
 * NS-mediated wrapper that lets a client complete pairing by typing an 8-digit PIN
 * instead of scanning. Published by the master to the NS `settings` collection under
 * `aaps_clientcontrol_offer_<clientId>` and deleted on dismiss / pair-complete / expiry.
 *
 * The wrapped bytes are the AES-256-GCM ciphertext of the canonical
 * `PairingPayload` JSON (see [ClientControlPairingCrypto.wrap]). Auth tag is implicit
 * in the ciphertext bytes — wrong PIN fails `unwrap` cleanly without a separate MAC.
 *
 * `kdfSaltB64` / `ivB64` / `wrappedB64` are Base64 (no-wrap) to survive JSON transport.
 */
@Serializable
data class PairingOffer(
    val schemaVersion: Int = 1,
    val clientId: String,
    val expiresAt: Long,
    val kdfSaltB64: String,
    val ivB64: String,
    val wrappedB64: String
)

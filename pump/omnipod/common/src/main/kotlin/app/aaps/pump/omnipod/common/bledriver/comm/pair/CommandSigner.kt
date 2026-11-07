package app.aaps.pump.omnipod.common.bledriver.comm.pair

/**
 * Signs a command payload for the shared BLE [app.aaps.pump.omnipod.common.bledriver.comm
 * .session.Session]. This keeps the shared driver free of any Omnipod 5 type: only O5
 * connections pass a non-null signer (Dash never signs), and the O5 module supplies the
 * concrete implementation (`O5CertificateStore`).
 */
interface CommandSigner {

    /** Sign [data] with the secondary key; returns the raw signature (r || s, 64 bytes). */
    fun signRaw(data: ByteArray): ByteArray
}

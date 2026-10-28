package app.aaps.plugins.dexcomoneplus.identity

/**
 * Juggluco-aligned ADV name / candidate matching for ONE+ / G7 family.
 *
 * Juggluco `isG7`: local name starts with `DX` + (`CM`|`02`|`01`).
 * Once a device name is known from a successful session, prefer exact match.
 *
 * Do **not** treat marketing names like `Dexcom65` / `DexcomONE` as ONE+ candidates —
 * field logs showed pre-connect latching on a G6-style `Dexcom65` while the real
 * transmitter was `DX02aS`.
 *
 * What this file can and cannot do:
 * - The 4-digit code is the KEKS password. It is used at Connect and nowhere else. It is **not**
 *   part of the advertisement, so nothing here can pick one transmitter out of several by code.
 * - The serial may raise a score when it happens to appear in the ADV name. It must never filter:
 *   the ADV encoding is opaque and a real sensor often advertises as a short `DX02*` name.
 */
object OnePlusAdvCandidate {

    fun isG7FamilyName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.trim()
        if (n.length < 4) return false
        if (!n.startsWith("DX", ignoreCase = true)) return false
        val rest = n.substring(2, 4)
        return rest.equals("CM", ignoreCase = true) ||
            rest.equals("02", ignoreCase = true) ||
            rest.equals("01", ignoreCase = true)
    }

    /**
     * Open-scan name filter: G7/ONE+ family only (`DX02` / `DX01` / `DXCM`).
     * Intentionally excludes `Dex*` marketing names (G6 / phone companions).
     */
    fun nameMatchesSoft(name: String?): Boolean = isG7FamilyName(name)

    /**
     * Whether this ADV should be offered / preferred for [session].
     * - Exact stored ADV name wins (reconnect sticky).
     * - Stored MAC wins even if name is missing/odd.
     * - Else G7-family name only.
     */
    fun isCandidate(
        name: String?,
        address: String?,
        session: OnePlusStoredSession?,
    ): Boolean {
        val storedName = session?.lastDeviceName
        if (!storedName.isNullOrBlank() && name != null && name.equals(storedName, ignoreCase = true)) {
            return true
        }
        val storedMac = session?.lastMac
        if (!storedMac.isNullOrBlank() && address != null &&
            address.equals(storedMac, ignoreCase = true)
        ) {
            return true
        }
        if (!isG7FamilyName(name)) return false
        val serial = session?.identity?.serial
        if (!serial.isNullOrBlank() && !name.isNullOrBlank()) {
            // Prefer ADV that embeds serial characters when present; do not hard-reject
            // when serial encoding in ADV is opaque (G7 often uses short DX02* names).
            if (name.contains(serial, ignoreCase = true)) return true
        }
        return true
    }

    /**
     * Stored session to rank **this** scan with.
     *
     * The start screen used to rank every scan with whatever the slot store held. When the user was
     * starting a new sensor, that pushed the sensor being replaced to the top of the list and the
     * code on screen was ignored. The code cannot name a transmitter, but it can say "this is not
     * the stored one any more", and that is enough to stop the old MAC and the old ADV name from
     * winning.
     *
     * @param stored what the slot store holds, or null on a first pairing.
     * @param onScreen identity read from the code or QR the user has in front of them.
     * @return [stored] when both describe the same sensor (a reconnect or a repair), otherwise a
     *   session carrying the on-screen identity only: no MAC and no name, so the serial hint still
     *   applies as a boost while the sticky boost of the old transmitter is gone.
     */
    fun scanHintFor(
        stored: OnePlusStoredSession?,
        onScreen: OnePlusSensorIdentity?,
    ): OnePlusStoredSession? {
        if (onScreen == null) return stored
        val storedIdentity = stored?.identity ?: return OnePlusStoredSession(identity = onScreen)
        return if (isSameSensor(storedIdentity, onScreen)) stored else OnePlusStoredSession(identity = onScreen)
    }

    /**
     * Whether two identities describe the same sensor.
     *
     * The serial decides whenever both sides carry one: it is the only part that is unique. The
     * 4-digit code is not — two sensors may carry the same four digits — so it only decides when a
     * serial is missing.
     */
    private fun isSameSensor(stored: OnePlusSensorIdentity, onScreen: OnePlusSensorIdentity): Boolean {
        val storedSerial = stored.serial?.trim()?.takeIf { it.isNotEmpty() }
        val screenSerial = onScreen.serial?.trim()?.takeIf { it.isNotEmpty() }
        if (storedSerial != null && screenSerial != null) return storedSerial.equals(screenSerial, ignoreCase = true)
        return stored.pin.trim() == onScreen.pin.trim()
    }

    /**
     * Score used to sort the scan list. Higher is shown first.
     *
     * Every part is a boost, never a filter — see the note on this object. Feed it the session from
     * [scanHintFor], not the raw store, or a new sensor is ranked with the old sensor's fingerprint.
     */
    fun rankScore(
        name: String?,
        address: String?,
        rssi: Int,
        session: OnePlusStoredSession?,
    ): Int {
        var score = rssi
        if (!session?.lastDeviceName.isNullOrBlank() &&
            name.equals(session?.lastDeviceName, ignoreCase = true)
        ) {
            score += 10_000
        }
        if (!session?.lastMac.isNullOrBlank() &&
            address.equals(session?.lastMac, ignoreCase = true)
        ) {
            score += 5_000
        }
        val serial = session?.identity?.serial
        if (!serial.isNullOrBlank() && name?.contains(serial, ignoreCase = true) == true) {
            score += 1_000
        }
        if (isG7FamilyName(name)) score += 100
        return score
    }
}

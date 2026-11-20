package app.aaps.pump.omnipod.common.bledriver.pod.definition

/**
 * Unified pod type, identified by the productId byte returned in a pod's VersionResponse.
 *
 * Ported from OmnipodKit's PodType.swift (loopandlearn/OmnipodKit) to support Eros, Dash,
 * and Omnipod 5 pods under a single model so BLE scanning/pairing/command logic can branch
 * on pod type instead of assuming Dash-only, as the existing bledriver package did.
 */
enum class PodType(val value: Int) {

    /** Can be used before the actual pod type is known. */
    UNKNOWN(0x0),

    /** Omnipod Eros (PM=PI=2.x.y), AKA "Omnipod Classic (gen 3)". Uses RileyLink. */
    EROS(0x2),


    /** Omnipod DASH: either TWI board (firmware 4.x.y) or NXP BLE (firmware 3.x.y). */
    DASH(0x4),

    /** Omnipod 5. Value still needs independent verification against real pod hardware. */
    OMNIPOD_5(0x5);

    companion object {

        private const val NEAR_ZERO_BASAL_RATE = 0.01

        fun fromValue(value: Int): PodType =
            entries.firstOrNull { it.value == value } ?: UNKNOWN
    }

    val briefName: String
        get() = when (this) {
            UNKNOWN   -> ""
            EROS      -> "Eros"
            DASH      -> "DASH"
            OMNIPOD_5 -> "O5"
        }

    val fullName: String
        get() = when (this) {
            UNKNOWN   -> "Omnipod"
            EROS      -> "Omnipod Classic"
            DASH      -> "Omnipod DASH"
            OMNIPOD_5 -> "Omnipod 5"
        }

    /** DASH uses a blue needle tab; both Eros and O5 pods use a clear tab. */
    val tabColor: String
        get() = if (this == DASH) "blue" else "clear"

    /** Only Eros pods use a RileyLink for communication. */
    val usesRileyLink: Boolean
        get() = this == EROS

    /** Only Eros pods report an RSSI value in DetailedStatus. */
    val reportsRSSI: Boolean
        get() = this == EROS

    /**
     * Non-Eros pods use a small near-zero basal rate for pulse timing purposes
     * when the actual programmed basal rate is zero.
     */
    val zeroBasalRate: Double
        get() = if (this == EROS) 0.0 else NEAR_ZERO_BASAL_RATE

    /**
     * Top byte used when constructing the fixed 32-bit controller Id:
     * - Eros PDMs use 0x1F for the top byte.
     * - Dash PDMs use 0x17, with the bottom 5 nibbles derived from the PDM serial number << 2.
     * - Omnipod 5 PDMs appear to follow the same SN-based scheme as Dash; the top byte value
     *   below is a placeholder pending confirmation against real O5 pairing traffic.
     */
    val topIdByte: Int
        get() = when (this) {
            EROS      -> 0x1F
            DASH      -> 0x17
            OMNIPOD_5 -> 0x00
            UNKNOWN   -> 0x00
        }

    val isEros: Boolean get() = this == EROS
    val isDash: Boolean get() = this == DASH
    val isO5: Boolean get() = this == OMNIPOD_5
}

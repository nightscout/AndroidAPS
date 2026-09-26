package app.aaps.cgm.dexcomg7.protocol

/**
 * The sensor models this driver supports, told apart by the prefix of the advertised name.
 *
 * Stelo ("DX01") is deliberately not here. It reads every 15 minutes and is not made for dosing, so
 * it is never offered as a pairing candidate.
 */
enum class G7SensorModel(val advertisedPrefix: String, val displayName: String) {

    G7("DXCM", "Dexcom G7"),
    ONE_PLUS("DX02", "Dexcom ONE+");

    companion object {

        /** Prefix of the unsupported Stelo, kept so a log can say why a sensor was skipped. */
        const val STELO_PREFIX = "DX01"

        fun fromAdvertisedName(name: String?): G7SensorModel? =
            name?.let { n -> entries.firstOrNull { n.startsWith(it.advertisedPrefix) } }

        /**
         * True for any name a supported sensor uses: the advertised "DXCMxx" / "DX02xx", or the
         * "Dexcomxx" it reports once connected.
         */
        fun isFamilyName(name: String?): Boolean = fromAdvertisedName(name) != null || name?.startsWith("Dexcom") == true

        /**
         * Whether two names belong to the same sensor. The name changes after connecting (advertised
         * "DXCMxx", connected "Dexcomxx"), and only the last two characters stay the same.
         */
        fun sameSensor(a: String?, b: String?): Boolean =
            a != null && b != null && a.length >= 2 && b.length >= 2 && a.takeLast(2) == b.takeLast(2)
    }
}

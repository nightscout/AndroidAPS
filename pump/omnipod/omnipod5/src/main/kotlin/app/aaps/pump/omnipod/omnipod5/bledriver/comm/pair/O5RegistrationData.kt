package app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair

import java.util.Base64
import java.util.Random
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * PKI registration material for one Omnipod 5 controller identity: a secondary P-256
 * signing keypair plus the certificate chain issued for it, all keyed by controllerId.
 *
 * This is deliberately just a data holder + in-memory registry - this open-source project
 * doesn't ship real Insulet-issued PKI material. Real registration data can be added at
 * runtime three ways, mirroring OmnipodKit's own [O5RegistrationSource] distinction:
 * - [Installer] discovered via [ServiceLoader] ([O5RegistrationSource.BUILT_IN])
 * - [installPacked] / [fromJson] ([O5RegistrationSource.IMPORTED]) - for a user pasting a
 *   credential string or importing a keypair file through some future settings UI
 * - [install] called directly by other code, e.g. after a network fetch
 *   ([O5RegistrationSource.DOWNLOADED]) - note OmnipodKit's own automatic-download flow is
 *   built on Apple's DeviceCheck/App Attest, which has no Android equivalent; this source
 *   value exists for API parity, not because that specific flow is portable
 *
 * Ported from OmnipodKit's O5RegistrationData.swift (loopandlearn/OmnipodKit). The Swift
 * original's built-in loading uses a `dlsym`-based dynamic symbol lookup, which has no
 * portable JVM equivalent; [ServiceLoader] is the idiomatic JVM analogue.
 */
data class O5RegistrationData(
    /** The 32-bit controller id this registration applies to (kept as Long to avoid
     *  unsigned-overflow pitfalls; valid values fit in the low 32 bits). */
    val controllerId: Long,
    val privateKeyHex: String,
    val publicKeyHex: String,
    val intermediateCABase64: String,
    val tlsCertificateBase64: String
) {

    val privateKey: ByteArray get() = hexToBytes(privateKeyHex)
    val publicKey: ByteArray get() = hexToBytes(publicKeyHex)

    val intermediateCA: ByteArray? get() = decodeBase64OrNull(intermediateCABase64)
    val tlsCertificate: ByteArray? get() = decodeBase64OrNull(tlsCertificateBase64)

    /** The controllerId as its 4-byte big-endian representation. */
    val controllerIdData: ByteArray
        get() {
            val id = controllerId and 0xFFFFFFFFL
            return byteArrayOf(
                ((id shr 24) and 0xFF).toByte(),
                ((id shr 16) and 0xFF).toByte(),
                ((id shr 8) and 0xFF).toByte(),
                (id and 0xFF).toByte()
            )
        }

    /**
     * The shape matches OmnipodKit's "o5keypair" file format, so persisted entries and
     * imported files/strings share one representation.
     */
    fun toJsonMap(): Map<String, String> = mapOf(
        "controllerId" to controllerId.toString(),
        "privateKey" to privateKeyHex,
        "publicKey" to publicKeyHex,
        "intermediateCA" to intermediateCABase64,
        "tlsCertificate" to tlsCertificateBase64
    )

    /**
     * Implement and register via `META-INF/services` (or call [O5RegistrationData.install]
     * directly at app startup) to supply real O5 PKI data from an optional module that
     * isn't part of this open-source repository.
     */
    interface Installer {
        fun install()
    }

    /** Where a given registration entry originally came from - for diagnostics/UI display. */
    enum class O5RegistrationSource { BUILT_IN, IMPORTED, DOWNLOADED }

    companion object {

        private val registry = ConcurrentHashMap<Long, O5RegistrationData>()
        private val sources = ConcurrentHashMap<Long, O5RegistrationSource>()
        private val random = Random()

        @Volatile
        private var optionalDataLoaded = false

        fun install(value: O5RegistrationData, source: O5RegistrationSource) {
            registry[value.controllerId] = value
            sources[value.controllerId] = source
        }

        fun markSource(controllerId: Long, source: O5RegistrationSource) {
            sources[controllerId] = source
        }

        fun source(forControllerId: Long): O5RegistrationSource? {
            loadOptionalRegistrationDataOnce()
            return sources[forControllerId]
        }

        fun get(controllerId: Long): O5RegistrationData? {
            loadOptionalRegistrationDataOnce()
            return registry[controllerId]
        }

        fun getRandom(): O5RegistrationData? {
            loadOptionalRegistrationDataOnce()
            val values = registry.values.toList()
            return if (values.isEmpty()) null else values[random.nextInt(values.size)]
        }

        val allValues: List<O5RegistrationData>
            get() {
                loadOptionalRegistrationDataOnce()
                return registry.values.toList()
            }

        val isEmpty: Boolean
            get() {
                loadOptionalRegistrationDataOnce()
                return registry.isEmpty()
            }

        fun contains(controllerId: Long): Boolean = get(controllerId) != null

        fun remove(controllerId: Long) {
            registry.remove(controllerId)
            sources.remove(controllerId)
        }

        /** Randomly picks an available O5 controllerId, or 0 if none is available. */
        val pickControllerId: Long
            get() = getRandom()?.controllerId ?: 0L

        /**
         * Parses a compact delimited credential string: `"controllerId|privB64|pubB64|icaB64|tlsB64"`
         * (matching OmnipodKit's own `install(packed:)` format, so a credential exported from
         * one can be pasted directly into the other), converts the base64-encoded private/public
         * keys to hex internally, and installs it with source [O5RegistrationSource.IMPORTED].
         *
         * Returns true if the string parsed and installed successfully, false if it was
         * malformed (wrong field count, unparseable controllerId) - this never throws, since
         * it's meant to validate arbitrary user-pasted input.
         */
        fun installPacked(packed: String): Boolean {
            val parts = packed.trim().split("|")
            if (parts.size != 5) return false
            val controllerId = parts[0].toLongOrNull() ?: return false

            val privateKeyHex = base64ToHexOrNull(parts[1]) ?: return false
            val publicKeyHex = base64ToHexOrNull(parts[2]) ?: return false

            install(
                O5RegistrationData(
                    controllerId = controllerId,
                    privateKeyHex = privateKeyHex,
                    publicKeyHex = publicKeyHex,
                    intermediateCABase64 = parts[3],
                    tlsCertificateBase64 = parts[4]
                ),
                source = O5RegistrationSource.IMPORTED
            )
            return true
        }

        /**
         * Builds the packed string form of [data], the inverse of [installPacked] - useful
         * for exporting/backing up an installed credential.
         */
        fun toPacked(data: O5RegistrationData): String {
            val privB64 = Base64.getEncoder().encodeToString(data.privateKey)
            val pubB64 = Base64.getEncoder().encodeToString(data.publicKey)
            return "${data.controllerId}|$privB64|$pubB64|${data.intermediateCABase64}|${data.tlsCertificateBase64}"
        }

        /**
         * Parses an "o5keypair" JSON-shaped map (as produced by [O5RegistrationData.toJsonMap],
         * or read from a JSON file/object elsewhere) into an [O5RegistrationData]. Returns null
         * if any required field is missing or the controllerId isn't a valid number - this is
         * a pure parse function; callers decide whether/how to [install] the result.
         */
        fun fromJsonMap(json: Map<String, String?>): O5RegistrationData? {
            val controllerId = json["controllerId"]?.toLongOrNull() ?: return null
            val privateKeyHex = json["privateKey"] ?: return null
            val publicKeyHex = json["publicKey"] ?: return null
            val intermediateCABase64 = json["intermediateCA"] ?: return null
            val tlsCertificateBase64 = json["tlsCertificate"] ?: return null
            return O5RegistrationData(
                controllerId = controllerId,
                privateKeyHex = privateKeyHex,
                publicKeyHex = publicKeyHex,
                intermediateCABase64 = intermediateCABase64,
                tlsCertificateBase64 = tlsCertificateBase64
            )
        }

        /**
         * Visible for testing: forces the ServiceLoader-based optional-data lookup to run
         * again on the next registry access, e.g. after registering a fake Installer.
         */
        internal fun resetOptionalDataLoadedForTesting() {
            optionalDataLoaded = false
        }

        private fun loadOptionalRegistrationDataOnce() {
            if (optionalDataLoaded) return
            synchronized(this) {
                if (optionalDataLoaded) return
                try {
                    ServiceLoader.load(Installer::class.java).forEach {
                        it.install()
                    }
                } catch (_: Throwable) {
                }
                optionalDataLoaded = true
            }
        }

        private fun hexToBytes(hex: String): ByteArray {
            val clean = hex.trim()
            require(clean.length % 2 == 0) { "Hex string must have an even length" }
            return ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }

        private fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }

        private fun base64ToHexOrNull(base64: String): String? =
            try {
                bytesToHex(Base64.getDecoder().decode(base64))
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun decodeBase64OrNull(value: String): ByteArray? =
            try {
                Base64.getDecoder().decode(value)
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}

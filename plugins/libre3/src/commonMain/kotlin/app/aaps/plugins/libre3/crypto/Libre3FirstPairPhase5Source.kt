package app.aaps.plugins.libre3.crypto

import app.aaps.plugins.libre3.crypto.firstpair.builder633fa8StaticScalarWindowFromEntrySource
import app.aaps.plugins.libre3.crypto.firstpair.builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints
import app.aaps.plugins.libre3.crypto.firstpair.bundled6388f0LowSeedEntrySource
import app.aaps.plugins.libre3.crypto.firstpair.deriveFrom6388f0FirstPairStreamSeeds

/**
 * The pairing key of a first pairing.
 *
 * Ported from LibreCRKit `Pairing/SessionKey.deriveFirstPairPhase5Material` at pin `a86b92f`.
 *
 * A first pairing has to build its own Phase 5 key, because there is nothing stored yet. The key
 * comes from four things: the entropy the phone drew for its own key pair, a fixed entry source,
 * the sensor's session point, and the point in the sensor's certificate.
 *
 * The key that comes out **must be stored**. Without it a reconnect has nothing to reuse, and the
 * only way back is a fresh NFC scan of the sensor.
 */
object Libre3FirstPairPhase5Source {

    /** The 66 byte source, and the 16 byte key it makes. */
    class Material(val source66: ByteArray, val rawKey: ByteArray)

    /**
     * The scalar of the static branch when the certificate does not bring its own.
     *
     * It comes from a fixed entry source, so it is the same for every sensor and every pairing.
     * Working it out costs three rounds of the low seed path, so it is done once. It is only the
     * fallback, see the override parameter of [derive].
     */
    private val staticScalarWindowFromEntrySource: ByteArray by lazy {
        builder633fa8StaticScalarWindowFromEntrySource(bundled6388f0LowSeedEntrySource)
    }

    /**
     * @param material what [Libre3FirstPairEphemeral.make] returned for this pairing.
     * @param sensorEphemeralPublicKey65 the sensor's session point, 65 bytes, starting with `0x04`.
     * @param sensorStaticPublicKey65 the point in the sensor's certificate, same shape.
     * @param staticScalarWindowOverride the static scalar that belongs to the phone certificate of
     *   this pairing, from `Libre3PhoneCert.phase5StaticScalarWindowOverride`. The `03 03` family
     *   brings its own and a live sensor only accepts that one; null falls back to the entry
     *   source, which is what the `03 00` family uses.
     * @throws Libre3CryptoException when a point is the wrong shape, or a table is missing.
     */
    fun derive(
        material: Libre3FirstPairMaterial,
        sensorEphemeralPublicKey65: ByteArray,
        sensorStaticPublicKey65: ByteArray,
        staticScalarWindowOverride: ByteArray? = null,
    ): Material {
        val row0Point = pointXYBigEndian(sensorEphemeralPublicKey65, "the sensor session point")
        val row59Point = pointXYBigEndian(sensorStaticPublicKey65, "the sensor certificate point")

        val entrySource = bundled6388f0LowSeedEntrySource
        val seeds = builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints(
            entrySource = entrySource,
            nullScalarWindow = material.scalarWindow,
            staticScalarWindow = staticScalarWindowOverride ?: staticScalarWindowFromEntrySource,
            row0SensorPointXYBE = row0Point,
            row59SensorPointXYBE = row59Point,
            nullEntropy11A = material.entropy11A,
            nullAttempts = material.attempts,
        )
        val source66 = deriveFrom6388f0FirstPairStreamSeeds(seeds)
        return Material(source66, Libre3Phase5KeySchedule.deriveRawKey(source66))
    }

    /** Drops the `0x04` prefix, leaving X then Y, high byte first. */
    private fun pointXYBigEndian(point65: ByteArray, label: String): ByteArray {
        if (point65.size != Libre3EphemeralKeyPair.PUBLIC_KEY_SIZE || point65[0] != 0x04.toByte()) {
            throw Libre3CryptoException("$label must be 65 bytes and start with 0x04, not ${point65.size} bytes")
        }
        return point65.copyOfRange(1, point65.size)
    }
}

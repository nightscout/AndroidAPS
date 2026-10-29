package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.libre3P256ScalarMultiply

/** The two coordinates of the product point, each in a seventy byte window, low byte first. */
internal class Builder5bcf98P256Outputs(val xOutput70: ByteArray, val yOutput70: ByteArray)

/**
 * Multiplies the sensor's point by the scalar of the seventy byte window.
 *
 * The arithmetic itself is `libre3P256ScalarMultiply`, which each platform supplies because
 * Kotlin has no shared big whole number type. Everything the scheme does with the answer is here.
 *
 * @param scalarWindowLE the scalar, low byte first, at least seventy bytes.
 * @param sensorPointXYBE the sensor's point as X then Y, high byte first, at least sixty four bytes.
 */
internal fun builder5bcf98P256Outputs(scalarWindowLE: ByteArray, sensorPointXYBE: ByteArray): Builder5bcf98P256Outputs {
    val product = libre3P256ScalarMultiply(scalarWindowLE, sensorPointXYBE)
    return Builder5bcf98P256Outputs(product.x70, product.y70)
}

/** The point multiply, then the two high seeds it feeds. */
internal fun builder6388f0HighSeedStreamStartSeedsFromScalarP256(
    scalarWindowLE: ByteArray,
    sensorPointXYBE: ByteArray,
    x1Source: ByteArray? = null,
    x2Source: ByteArray? = null,
    scalar: ULong? = null,
): Builder6388f0HighSeedStreamStartSeeds {
    val outputs = builder5bcf98P256Outputs(scalarWindowLE, sensorPointXYBE)
    return builder6388f0HighSeedStreamStartSeedsFrom5bcf98Outputs(
        outputs.xOutput70, outputs.yOutput70, x1Source, x2Source, scalar,
    )
}

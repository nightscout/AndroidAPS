package app.aaps.plugins.libre3.crypto.firstpair

/*
 * The eleven seeds that a first pairing feeds into the `6388f0` stream.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 *
 * Three of them come from the fixed entry source through the low seed path. The other eight come
 * from two point multiplies: the phone's own scalar against the sensor's session point, and the
 * static scalar against the point in the sensor's certificate.
 */
internal class Builder6388f0FirstPairStreamSeeds(
    val nullScalarWindow: ByteArray,
    val staticScalarWindow: ByteArray,
    val nullEntropy11A: ByteArray,
    val nullAttempts: Int,
    val row0Out4: ByteArray,
    val row0Out3: ByteArray,
    val row0Out2: ByteArray,
    val row0Out1: ByteArray,
    val row0Out0: ByteArray,
    val row59Out1: ByteArray,
    val row59Out0: ByteArray,
)

/**
 * The seeds of a first pairing, from the two scalars and the two sensor points.
 *
 * @param entrySource the fixed entry source of the low seed path.
 * @param nullScalarWindow the phone's own scalar, from its accepted draw of entropy.
 * @param staticScalarWindow the scalar of the static branch.
 * @param row0SensorPointXYBE the sensor's session point, X then Y, high byte first.
 * @param row59SensorPointXYBE the point in the sensor's certificate, X then Y, high byte first.
 */
@Suppress("LongParameterList")
internal fun builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints(
    entrySource: ByteArray,
    nullScalarWindow: ByteArray,
    staticScalarWindow: ByteArray,
    row0SensorPointXYBE: ByteArray,
    row59SensorPointXYBE: ByteArray,
    nullEntropy11A: ByteArray = ByteArray(0),
    nullAttempts: Int = 0,
    x1Source: ByteArray? = null,
    x2Source: ByteArray? = null,
    scalar: ULong? = null,
): Builder6388f0FirstPairStreamSeeds {
    val low = builder6388f0Row0LowSeedPreimagesFromEntrySource(entrySource)
    val row0High = builder6388f0HighSeedStreamStartSeedsFromScalarP256(
        nullScalarWindow, row0SensorPointXYBE, x1Source, x2Source, scalar,
    )
    val row59High = builder6388f0HighSeedStreamStartSeedsFromScalarP256(
        staticScalarWindow, row59SensorPointXYBE, x1Source, x2Source, scalar,
    )
    return Builder6388f0FirstPairStreamSeeds(
        nullScalarWindow = nullScalarWindow,
        staticScalarWindow = staticScalarWindow,
        nullEntropy11A = nullEntropy11A,
        nullAttempts = nullAttempts,
        row0Out4 = low.out4,
        row0Out3 = low.out3,
        row0Out2 = low.out2,
        row0Out1 = row0High.out1,
        row0Out0 = row0High.out0,
        row59Out1 = row59High.out1,
        row59Out0 = row59High.out0,
    )
}

/** The same, from one accepted draw of entropy instead of a ready scalar. */
@Suppress("LongParameterList")
internal fun builder6388f0FirstPairStreamSeedsFromEntropyAndSensorPoints(
    entrySource: ByteArray,
    nullEntropy11A: ByteArray,
    row0SensorPointXYBE: ByteArray,
    row59SensorPointXYBE: ByteArray,
    x1Source: ByteArray? = null,
    x2Source: ByteArray? = null,
    scalar: ULong? = null,
): Builder6388f0FirstPairStreamSeeds = builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints(
    entrySource = entrySource,
    nullScalarWindow = builder633fa8NullScalarWindowFromEntropy(nullEntropy11A),
    staticScalarWindow = builder633fa8StaticScalarWindowFromEntrySource(entrySource),
    row0SensorPointXYBE = row0SensorPointXYBE,
    row59SensorPointXYBE = row59SensorPointXYBE,
    nullEntropy11A = nullEntropy11A,
    nullAttempts = 1,
    x1Source = x1Source,
    x2Source = x2Source,
    scalar = scalar,
)

/** The same, drawing entropy until the scheme accepts it. */
@Suppress("LongParameterList")
internal fun builder6388f0FirstPairStreamSeedsFromEntropySourceAndSensorPoints(
    entrySource: ByteArray,
    row0SensorPointXYBE: ByteArray,
    row59SensorPointXYBE: ByteArray,
    maxAttempts: Int = 64,
    x1Source: ByteArray? = null,
    x2Source: ByteArray? = null,
    scalar: ULong? = null,
    entropySource: (Int) -> ByteArray,
): Builder6388f0FirstPairStreamSeeds {
    val nullResult = builder633fa8NullScalarWindowFromEntropySource(maxAttempts, entropySource)
    return builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints(
        entrySource = entrySource,
        nullScalarWindow = nullResult.scalarWindow,
        staticScalarWindow = builder633fa8StaticScalarWindowFromEntrySource(entrySource),
        row0SensorPointXYBE = row0SensorPointXYBE,
        row59SensorPointXYBE = row59SensorPointXYBE,
        nullEntropy11A = nullResult.entropy11A,
        nullAttempts = nullResult.attempts,
        x1Source = x1Source,
        x2Source = x2Source,
        scalar = scalar,
    )
}

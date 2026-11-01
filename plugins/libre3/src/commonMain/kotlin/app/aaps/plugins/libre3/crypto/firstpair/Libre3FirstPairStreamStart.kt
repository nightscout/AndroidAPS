package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException

/*
 * The three arguments that one row of the `6388f0` stream feeds to the `63c278` builder.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`.
 */

/** The three arguments of one `642f60` call. */
internal class Builder6388f0Next642f60Inputs(val x0: ByteArray, val x1: ByteArray, val x2: ByteArray)

internal fun builder6388f0Next642f60InputsFrom64cd40Outputs(
    first64cd40Output: ByteArray,
    second64cd40Output: ByteArray,
    third64cd40Output: ByteArray,
): Builder6388f0Next642f60Inputs {
    val tables = Libre3FirstPairTables.get()
    return Builder6388f0Next642f60Inputs(
        u32AffineBytes63c278(
            first64cd40Output, builder6388f0Pre63Arg1MulTable, builder6388f0Pre63Arg1AddTable,
            "first 64cd40 output", tables,
        ),
        u32AffineBytes63c278(
            second64cd40Output, builder6388f0Next642X1MulTable, builder6388f0Next642X1AddTable,
            "second 64cd40 output", tables,
        ),
        u32AffineBytes63c278(
            third64cd40Output, builder6388f0Pre63Arg2MulTable, builder6388f0Pre63Arg2AddTable,
            "third 64cd40 output", tables,
        ),
    )
}

internal fun builder6388f0StreamStart642f60X0FromOut0Seed(out0Seed: ByteArray): ByteArray =
    u32AffineBytes63c278(
        out0Seed, builder6388f0StreamStartOut0To642f60X0MulTable, builder6388f0StreamStartOut0To642f60X0AddTable,
        "6388f0 stream-start out0 seed", Libre3FirstPairTables.get(),
    )

internal fun builder6388f0StreamStart642f60X1FromOut1Seed(out1Seed: ByteArray): ByteArray =
    u32AffineBytes63c278(
        out1Seed, builder6388f0StreamStartOut1To642f60X1MulTable, builder6388f0StreamStartOut1To642f60X1AddTable,
        "6388f0 stream-start out1 seed", Libre3FirstPairTables.get(),
    )

/** Undoes [builder6388f0StreamStart642f60X0FromOut0Seed]. Used by the vector tests. */
internal fun builder6388f0RecoverStreamStartOut0SeedFrom642f60X0(x0Source: ByteArray): ByteArray =
    u32AffineInverseBytes63c278(
        x0Source, builder6388f0StreamStartOut0To642f60X0MulTable, builder6388f0StreamStartOut0To642f60X0AddTable,
        "6388f0 stream-start 642f60 x0 source", Libre3FirstPairTables.get(),
    )

/** Undoes [builder6388f0StreamStart642f60X1FromOut1Seed]. Used by the vector tests. */
internal fun builder6388f0RecoverStreamStartOut1SeedFrom642f60X1(x1Source: ByteArray): ByteArray =
    u32AffineInverseBytes63c278(
        x1Source, builder6388f0StreamStartOut1To642f60X1MulTable, builder6388f0StreamStartOut1To642f60X1AddTable,
        "6388f0 stream-start 642f60 x1 source", Libre3FirstPairTables.get(),
    )

internal fun builder6388f0StreamStart642f60Inputs(
    out0Seed: ByteArray,
    out1Seed: ByteArray,
    x2Source: ByteArray? = null,
): Builder6388f0Next642f60Inputs {
    val resolvedX2 = x2Source ?: streamStart642f60X2Source
    if (resolvedX2.size < builder63c278VectorBytes) {
        throw Libre3CryptoException(
            "the 6388f0 stream start x2 source must be at least $builder63c278VectorBytes bytes, not ${resolvedX2.size}"
        )
    }
    return Builder6388f0Next642f60Inputs(
        builder6388f0StreamStart642f60X0FromOut0Seed(out0Seed),
        builder6388f0StreamStart642f60X1FromOut1Seed(out1Seed),
        resolvedX2.copyOfRange(0, builder63c278VectorBytes),
    )
}

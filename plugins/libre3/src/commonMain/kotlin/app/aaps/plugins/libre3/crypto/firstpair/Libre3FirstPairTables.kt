package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException
import app.aaps.plugins.libre3.crypto.Libre3RuntimeTables

/**
 * The twenty five fixed tables that the first pairing scheme reads.
 *
 * Ported from LibreCRKit `Crypto/FirstPairSourceSlice.swift` at pin `a86b92f`, where the same
 * tables are held in a private `SourceTables` value.
 *
 * The names are addresses inside the sensor maker's own library and mean nothing on their own.
 * They are kept as they are so that the Kotlin can be read next to the Swift.
 *
 * The tables are loaded once and their sizes are checked, because a short table would not fail
 * loudly, it would quietly make a wrong key.
 */
internal class Libre3FirstPairTables private constructor(
    val sbox19: ByteArray,
    val prog64e2b8: ByteArray,
    val prog638840: ByteArray,
    val lowSeedStatics6388f0: ByteArray,
    val lowLoopStatics6388f0: ByteArray,
    val laneTables6388f0: ByteArray,
    val selectorMul6388f0: ByteArray,
    val selectorAdd6388f0: ByteArray,
    val u32Tables63c278: ByteArray,
    val foldTables63c278: ByteArray,
    val tailFoldTables633fa8: ByteArray,
    val tailU32LowTables633fa8: ByteArray,
    val nullTables633fa8: ByteArray,
    val nullNibble633fa8: ByteArray,
    val process2PublicTables: ByteArray,
    val prog67cc18: ByteArray,
    val ttableBExt: ByteArray,
    val finalLenTables: ByteArray,
    val df80RoundTables: ByteArray,
    val finalizerTables: ByteArray,
    val seedTables679f48: ByteArray,
    val reducer67ea28Nibble: ByteArray,
    val prog67076c: ByteArray,
    val sharedContext6388f0: ByteArray,
    val callerLoopInterleaved6388f0: ByteArray,
) {

    companion object {

        const val SBOX19 = "sbox_19bit_lib_986819.bin"
        const val PROG_64E2B8 = "firstpair_prog_64e2b8_3041b4.bin"
        const val PROG_638840 = "firstpair_prog_638840_2f5046.bin"
        const val LOW_SEED_STATICS_6388F0 = "firstpair_6388f0_low_seed_statics_2f4d28.bin"
        const val LOW_LOOP_STATICS_6388F0 = "firstpair_6388f0_low_loop_statics_2fe600.bin"
        const val LANE_TABLES_6388F0 = "firstpair_6388f0_lane_tables_302678.bin"
        const val SELECTOR_MUL_6388F0 = "firstpair_6388f0_selector_mul_116968.bin"
        const val SELECTOR_ADD_6388F0 = "firstpair_6388f0_selector_add_119788.bin"
        const val U32_TABLES_63C278 = "firstpair_63c278_u32_tables_112588.bin"
        const val FOLD_TABLES_63C278 = "firstpair_63c278_fold_tables_2feb18.bin"
        const val TAIL_FOLD_TABLES_633FA8 = "firstpair_633fa8_tail_fold_tables_2fe798.bin"
        const val TAIL_U32_LOW_TABLES_633FA8 = "firstpair_633fa8_tail_u32_low_tables_112528.bin"
        const val NULL_TABLES_633FA8 = "firstpair_633fa8_null_tables_2fd1f1.bin"
        const val NULL_NIBBLE_633FA8 = "firstpair_633fa8_null_nibble_303a14.bin"
        const val PROCESS2_PUBLIC_TABLES = "firstpair_process2_public_tables_3038c0.bin"
        const val PROG_67CC18 = "firstpair_prog_67cc18_369862.bin"
        const val TTABLE_B_EXT = "child23_ttable_b_ext_976ea8_100000.bin"
        const val FINAL_LEN_TABLES = "firstpair_final_len_tables_372102.bin"
        const val DF80_ROUND_TABLES = "firstpair_df80_round_tables_37120e.bin"
        const val FINALIZER_TABLES = "firstpair_finalizer_tables_370e30.bin"
        const val SEED_TABLES_679F48 = "firstpair_679f48_seed_tables_37075e.bin"
        const val REDUCER_67EA28_NIBBLE = "firstpair_reducer67ea28_nibble_373cf4.bin"
        const val PROG_67076C = "firstpair_prog_67076c_35d3ef.bin"
        const val SHARED_CONTEXT_6388F0 = "firstpair_6388f0_shared_context_2cdae1.bin"
        const val CALLER_LOOP_INTERLEAVED_6388F0 = "firstpair_6388f0_caller_loop_interleaved_2cdfa9.bin"

        /** Every file this scheme needs, with the size each one must have. */
        val REQUIRED: Map<String, Int> = linkedMapOf(
            SBOX19 to 0x80000,
            PROG_64E2B8 to prog64e2b8Length,
            PROG_638840 to prog638840Length,
            LOW_SEED_STATICS_6388F0 to lowSeedStatics6388f0Length,
            LOW_LOOP_STATICS_6388F0 to lowLoopStatics6388f0Length,
            LANE_TABLES_6388F0 to laneTables6388f0Length,
            SELECTOR_MUL_6388F0 to selectorTables6388f0Length,
            SELECTOR_ADD_6388F0 to selectorTables6388f0Length,
            U32_TABLES_63C278 to u32Tables63c278Length,
            FOLD_TABLES_63C278 to foldTables63c278Length,
            TAIL_FOLD_TABLES_633FA8 to tailFoldTables633fa8Length,
            TAIL_U32_LOW_TABLES_633FA8 to tailU32LowTables633fa8Length,
            NULL_TABLES_633FA8 to nullTables633fa8Length,
            NULL_NIBBLE_633FA8 to nullNibble633fa8Length,
            PROCESS2_PUBLIC_TABLES to process2P5PublicTableLength,
            PROG_67CC18 to prog67cc18Length,
            TTABLE_B_EXT to ttableBExtLength,
            FINAL_LEN_TABLES to finalLenTablesLength,
            DF80_ROUND_TABLES to df80RoundTablesLength,
            FINALIZER_TABLES to finalizerTablesLength,
            SEED_TABLES_679F48 to seedTables679f48Length,
            REDUCER_67EA28_NIBBLE to reducer67ea28NibbleLength,
            PROG_67076C to prog67076cLength,
            SHARED_CONTEXT_6388F0 to builder6388f0SharedContextLength,
            CALLER_LOOP_INTERLEAVED_6388F0 to builder6388f0CallerLoopInterleavedLength,
        )

        /**
         * Names of the files that this build does not carry. Empty when the scheme can run.
         *
         * This only asks whether each entry exists. Reading them would be about 1.9 MB, and the
         * answer is wanted on every session start, including reconnects that never touch a single
         * one of these tables.
         */
        fun missing(): List<String> = REQUIRED.keys.filterNot { Libre3RuntimeTables.isPresent(it) }

        /** True when every table this scheme needs really ships with this build. */
        fun present(): Boolean = allPresent

        private val allPresent: Boolean by lazy { missing().isEmpty() }

        private val cached: Libre3FirstPairTables by lazy { loadAll() }

        /** The tables, loaded once. */
        fun get(): Libre3FirstPairTables = cached

        private fun read(name: String): ByteArray {
            val bytes = Libre3RuntimeTables.load(name)
                ?: throw Libre3CryptoException("the first pairing table $name does not ship with this build")
            val expected = REQUIRED.getValue(name)
            if (bytes.size != expected) {
                throw Libre3CryptoException("the first pairing table $name must be $expected bytes, not ${bytes.size}")
            }
            return bytes
        }

        private fun loadAll(): Libre3FirstPairTables = Libre3FirstPairTables(
            sbox19 = read(SBOX19),
            prog64e2b8 = read(PROG_64E2B8),
            prog638840 = read(PROG_638840),
            lowSeedStatics6388f0 = read(LOW_SEED_STATICS_6388F0),
            lowLoopStatics6388f0 = read(LOW_LOOP_STATICS_6388F0),
            laneTables6388f0 = read(LANE_TABLES_6388F0),
            selectorMul6388f0 = read(SELECTOR_MUL_6388F0),
            selectorAdd6388f0 = read(SELECTOR_ADD_6388F0),
            u32Tables63c278 = read(U32_TABLES_63C278),
            foldTables63c278 = read(FOLD_TABLES_63C278),
            tailFoldTables633fa8 = read(TAIL_FOLD_TABLES_633FA8),
            tailU32LowTables633fa8 = read(TAIL_U32_LOW_TABLES_633FA8),
            nullTables633fa8 = read(NULL_TABLES_633FA8),
            nullNibble633fa8 = read(NULL_NIBBLE_633FA8),
            process2PublicTables = read(PROCESS2_PUBLIC_TABLES),
            prog67cc18 = read(PROG_67CC18),
            ttableBExt = read(TTABLE_B_EXT),
            finalLenTables = read(FINAL_LEN_TABLES),
            df80RoundTables = read(DF80_ROUND_TABLES),
            finalizerTables = read(FINALIZER_TABLES),
            seedTables679f48 = read(SEED_TABLES_679F48),
            reducer67ea28Nibble = read(REDUCER_67EA28_NIBBLE),
            prog67076c = read(PROG_67076C),
            sharedContext6388f0 = read(SHARED_CONTEXT_6388F0),
            callerLoopInterleaved6388f0 = read(CALLER_LOOP_INTERLEAVED_6388F0),
        )
    }
}

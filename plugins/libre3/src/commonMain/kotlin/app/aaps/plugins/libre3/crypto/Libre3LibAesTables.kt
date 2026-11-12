package app.aaps.plugins.libre3.crypto

/**
 * The fixed tables the pairing block maker reads.
 *
 * They ship with the app under `resources/libre3` and never change, so they are read once and kept.
 * All of them come from the MIT LibreCRKit tree, see `plugins/libre3/NOTICE`.
 */
class Libre3LibAesTables private constructor(
    val phase5Round1Tables: ByteArray,
    val phase5RoundTables: UIntArray,
    val keyexpTables: ByteArray,
    val keyexpConsts: ByteArray,
    val finalKeyTables: ByteArray,
    val finalTableIndex: ByteArray,
    val finalTableMap: ByteArray,
    val finalTableWords: ByteArray,
) {

    companion object {

        /**
         * Read once and kept, the same way `Libre3FirstPairTables` keeps its own set.
         *
         * `by lazy` is the shared form of the `@Volatile` field with a `@Synchronized` loader this
         * used to be, and it behaves the same way on a failure: a throw leaves nothing cached, so
         * the next call tries again rather than handing out a half built set for ever.
         */
        private val cached: Libre3LibAesTables by lazy { readAll() }

        /**
         * @throws Libre3CryptoException when a table does not ship with this build. Nothing is
         *   made up in its place: a wrong table would produce messages the sensor cannot read.
         */
        fun load(): Libre3LibAesTables = cached

        private fun readAll(): Libre3LibAesTables {
            val finalTableWords = read("libaes_final_table_words_270624.bin")
            // The same bytes, read as whole words. This is what the rounds actually index.
            val roundWords = UIntArray(finalTableWords.size / 4) { i ->
                (finalTableWords[i * 4].toUInt() and 0xFFu) or
                    ((finalTableWords[i * 4 + 1].toUInt() and 0xFFu) shl 8) or
                    ((finalTableWords[i * 4 + 2].toUInt() and 0xFFu) shl 16) or
                    ((finalTableWords[i * 4 + 3].toUInt() and 0xFFu) shl 24)
            }
            return Libre3LibAesTables(
                phase5Round1Tables = read("libaes_5defec_round1_tables_26f621.bin"),
                phase5RoundTables = roundWords,
                keyexpTables = read("libaes_keyexp_tables_275bbb.bin"),
                keyexpConsts = read("libaes_keyexp_consts_276bbc.bin"),
                finalKeyTables = read("libaes_final_key_tables_276cfc.bin"),
                finalTableIndex = read("libaes_final_table_index_277cfc.bin"),
                finalTableMap = read("libaes_final_table_map_277d3c.bin"),
                finalTableWords = finalTableWords,
            )
        }

        private fun read(name: String): ByteArray =
            Libre3RuntimeTables.load(name)
                ?: throw Libre3CryptoException("this build does not ship the table $name")
    }
}

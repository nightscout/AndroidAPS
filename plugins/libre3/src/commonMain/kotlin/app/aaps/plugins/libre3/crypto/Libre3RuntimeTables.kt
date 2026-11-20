package app.aaps.plugins.libre3.crypto

/**
 * Loads the fixed byte tables that ship with the app.
 *
 * The bytes live once in the repository, under `src/commonMain/resources/libre3/`.
 *
 * Android packages them as classpath resources, not as Android assets. That choice is deliberate:
 * a classpath resource can also be read by a plain unit test, so the tables can be checked against
 * the published vectors without a device.
 *
 * Where the bytes come from is the only platform specific part, see [libre3ReadTable]. Which files
 * are needed, and what happens when one is missing, is shared.
 *
 * All of these files come from the MIT LibreCRKit tree. See `plugins/libre3/NOTICE` for where each
 * one comes from.
 */
object Libre3RuntimeTables {

    /** Path of the folder the tables sit in, as the platform sees it. */
    private const val FOLDER = "/libre3"

    /** The phone certificate used when a sensor is paired for the first time. */
    const val PHONE_CERT = "phone_cert_162b.bin"

    /** The table banks of the block maker that the pairing messages use. */
    val LIB_AES_TABLES = listOf(
        "libaes_round1_tables_278dc2.bin",
        "libaes_round2_9_tables_279dc4.bin",
        "libaes_5defec_round1_tables_26f621.bin",
        "libaes_keyexp_tables_275bbb.bin",
        "libaes_keyexp_consts_276bbc.bin",
        "libaes_final_key_tables_276cfc.bin",
        "libaes_final_table_index_277cfc.bin",
        "libaes_final_table_map_277d3c.bin",
        "libaes_final_table_words_270624.bin",
    )

    /** The static region the Phase 5 key schedule reads. */
    const val PHASE5_KEY_SCHEDULE = "phase5_keysched_region_274000.bin"

    /**
     * @return the bytes of one table, or null when the file does not ship with this build.
     *
     * Callers must treat null as "this part of the driver cannot run", never as a reason to make
     * up their own bytes.
     */
    fun load(name: String): ByteArray? = libre3ReadTable("$FOLDER/$name")

    /**
     * True when a table file really ships with this build.
     *
     * This asks the class loader for the entry only. It does **not** read the bytes, because some
     * of these files are hundreds of kilobytes and the answer is needed on every session start.
     */
    fun isPresent(name: String): Boolean = libre3HasTable("$FOLDER/$name")

    /** True when every file the pairing needs is really in this build. */
    fun pairingTablesPresent(): Boolean =
        isPresent(PHONE_CERT) && isPresent(PHASE5_KEY_SCHEDULE) && LIB_AES_TABLES.all { isPresent(it) }

    /** Names of the files that are missing, for a clear message instead of a silent failure. */
    fun missingTables(): List<String> =
        (listOf(PHONE_CERT, PHASE5_KEY_SCHEDULE) + LIB_AES_TABLES).filterNot { isPresent(it) }
}

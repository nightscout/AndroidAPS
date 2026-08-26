package app.aaps.database.di

/**
 * Which database the app opens.
 *
 * The instrumented tests run against an in-memory copy instead of the file on disk. That is the one
 * deliberate deviation from the production graph, and it is expressed here rather than by swapping a
 * DI module, because `AppDatabase` is internal to this module and so cannot be handed across a graph
 * boundary. The test build replaces the binding of this small public type instead.
 */
data class DatabaseConfig(
    val inMemory: Boolean,
    val fileName: String
) {

    companion object {

        val PRODUCTION = DatabaseConfig(inMemory = false, fileName = "androidaps.db")
        val IN_MEMORY = DatabaseConfig(inMemory = true, fileName = "")
    }
}

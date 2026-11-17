package app.aaps.plugins.constraints.storage

/** How much space is left where the app keeps its data. */
interface FreeStorage {

    /** The free space in MB. */
    fun availableMb(): Long
}

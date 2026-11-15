package app.aaps.core.interfaces.sync

interface Sync {

    val hasWritePermission: Boolean
    val connected: Boolean
    val status: String
}
package info.nightscout.interfaces

interface Sync {

    val hasWritePermission: Boolean
    val connected: Boolean
    val status: String
}
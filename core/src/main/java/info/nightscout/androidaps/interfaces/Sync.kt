package info.nightscout.androidaps.interfaces

interface Sync {

    val hasWritePermission: Boolean
    val connected: Boolean
    val status: String
}
package info.nightscout.androidaps.db

interface DbObjectBase {

    fun getDate(): Long
    fun getPumpId(): Long
}
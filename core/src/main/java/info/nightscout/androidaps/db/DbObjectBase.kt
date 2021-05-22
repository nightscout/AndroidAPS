package info.nightscout.androidaps.db

@Deprecated("This class is not needed for new database anymore")
interface DbObjectBase {

    fun getDate(): Long
    fun getPumpId(): Long
}
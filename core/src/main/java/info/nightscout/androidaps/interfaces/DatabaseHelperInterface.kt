package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.db.BgReading

@Deprecated("Remove with new DB")
interface DatabaseHelperInterface {
    fun getAllBgreadingsDataFromTime(mills : Long, ascending: Boolean): List<BgReading>
}
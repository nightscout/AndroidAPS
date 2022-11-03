package info.nightscout.androidaps.plugins.pump.medtronic.comm.history

/**
 * Created by andy on 7/24/18.
 */
interface MedtronicHistoryEntryInterface {

    val entryTypeName: String
    fun setData(listRawData: MutableList<Byte>, doNotProcess: Boolean)
    val dateLength: Int
}
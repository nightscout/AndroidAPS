package app.aaps.pump.medtronic.comm.history

/**
 * Created by andy on 3/10/19.
 */
interface MedtronicHistoryDecoderInterface<T> {

    fun decodeRecord(record: T): RecordDecodeStatus?
    fun createRecords(dataClearInput: MutableList<Byte>): MutableList<T>
}
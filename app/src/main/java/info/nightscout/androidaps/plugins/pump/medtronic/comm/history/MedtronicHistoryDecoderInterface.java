package info.nightscout.androidaps.plugins.pump.medtronic.comm.history;

import java.util.List;

/**
 * Created by andy on 3/10/19.
 */

public interface MedtronicHistoryDecoderInterface<T> {

    RecordDecodeStatus decodeRecord(T record);

    List<T> createRecords(List<Byte> dataClear);

}

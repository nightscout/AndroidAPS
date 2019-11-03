package info.nightscout.androidaps.plugins.pump.medtronic.comm.history;

import java.util.List;

/**
 * Created by andy on 7/24/18.
 */
public interface MedtronicHistoryEntryInterface {

    String getEntryTypeName();

    void setData(List<Byte> listRawData, boolean doNotProcess);

    int getDateLength();

}

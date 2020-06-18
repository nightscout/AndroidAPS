package info.nightscout.androidaps.interfaces;

import androidx.collection.LongSparseArray;

import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData;

public interface IobCobCalculatorInterface {
    LongSparseArray<AutosensData> getAutosensDataTable();
    IobTotal[] calculateIobArrayInDia(Profile profile);
    String lastDataTime();
    AutosensData getAutosensData(long toTime);
}

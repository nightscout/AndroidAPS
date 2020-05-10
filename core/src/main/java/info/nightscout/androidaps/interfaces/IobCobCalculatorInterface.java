package info.nightscout.androidaps.interfaces;

import androidx.collection.LongSparseArray;

import info.nightscout.androidaps.plugins.iob.iobCobCalculator.data.AutosensData;

public interface IobCobCalculatorInterface {
    LongSparseArray<AutosensData> getAutosensDataTable();
    String lastDataTime();
    AutosensData getAutosensData(long toTime);
}

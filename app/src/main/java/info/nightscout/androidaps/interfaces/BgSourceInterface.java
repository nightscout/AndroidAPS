package info.nightscout.androidaps.interfaces;

import android.os.Bundle;

import java.util.List;

import info.nightscout.androidaps.db.BgReading;

/**
 * Created by mike on 20.06.2016.
 */
public interface BgSourceInterface {
    List<BgReading> processNewData(Bundle bundle);
}

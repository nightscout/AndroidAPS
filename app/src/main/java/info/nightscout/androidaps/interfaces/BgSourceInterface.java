package info.nightscout.androidaps.interfaces;

import android.content.Intent;

/**
 * Created by mike on 20.06.2016.
 */
public interface BgSourceInterface {
    boolean advancedFilteringSupported();

    void handleNewData(Intent intent);
}

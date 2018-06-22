package info.nightscout.androidaps.interfaces;

import android.os.Bundle;

/**
 * Created by mike on 20.06.2016.
 */
public interface BgSourceInterface {
    void processNewData(Bundle bundle);
}

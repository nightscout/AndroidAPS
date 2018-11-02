package info.nightscout.androidaps.events;

import android.support.annotation.Nullable;

import info.nightscout.androidaps.db.BgReading;

/**
 * Created by mike on 05.06.2016.
 */
public class EventNewBG extends EventLoop {
    @Nullable
    public final BgReading bgReading;

    public EventNewBG(BgReading bgReading) {
        this.bgReading = bgReading;
    }
}

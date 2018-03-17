package info.nightscout.androidaps.events;

import android.support.annotation.Nullable;

import info.nightscout.androidaps.db.BgReading;

/**
 * Created by mike on 05.06.2016.
 */
public class EventNewBG extends EventLoop {
    @Nullable
    public final BgReading bgReading;
    public final boolean isNew;
    public final boolean isActiveBgSource;

    public EventNewBG(@Nullable BgReading bgReading, boolean isNew, boolean isActiveBgSource) {
        this.bgReading = bgReading;
        this.isNew = isNew;
        this.isActiveBgSource = isActiveBgSource;
    }
}

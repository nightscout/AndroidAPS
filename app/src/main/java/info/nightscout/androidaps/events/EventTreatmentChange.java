package info.nightscout.androidaps.events;

import android.support.annotation.Nullable;

import info.nightscout.androidaps.plugins.treatments.Treatment;

/**
 * Created by mike on 04.06.2016.
 */
public class EventTreatmentChange extends EventLoop {
    @Nullable
    public final Treatment treatment;

    public EventTreatmentChange(Treatment treatment) {
        this.treatment = treatment;
    }
}

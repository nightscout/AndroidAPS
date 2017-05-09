package info.nightscout.androidaps.events;

import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;

/**
 * Created by mike on 04.06.2016.
 */
public class EventNewBasalProfile {
    public NSProfile newNSProfile = null;
    public String from = "";

    public EventNewBasalProfile(NSProfile newProfile, String from) {
        newNSProfile = newProfile;
        this.from = from;
    }
}

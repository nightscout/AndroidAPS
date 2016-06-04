package info.nightscout.androidaps.data;

/**
 * Created by mike on 04.06.2016.
 */
public abstract class Pump {

    // Upload to pump new basal profile from MainApp.getNSProfile()
    public abstract void setNewBasalProfile();
}

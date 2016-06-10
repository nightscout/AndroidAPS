package info.nightscout.androidaps.data;

/**
 * Created by mike on 04.06.2016.
 */
public abstract class Pump {

    boolean tempBasalInProgress = false;

    // Upload to pump new basal profile from MainApp.getNSProfile()
    public abstract void setNewBasalProfile();

    public abstract double getBaseBasalRate(); // base basal rate, not temp basal
    public abstract double getTempBasalAbsoluteRate();
    public abstract double getTempBasalRemainingMinutes();
}

package info.nightscout.androidaps.plugins.pump.insight.descriptors;

public class ActiveBasalRate {

    private BasalProfile activeBasalProfile;
    private String activeBasalProfileName;
    private double activeBasalRate;

    public BasalProfile getActiveBasalProfile() {
        return this.activeBasalProfile;
    }

    public String getActiveBasalProfileName() {
        return this.activeBasalProfileName;
    }

    public double getActiveBasalRate() {
        return this.activeBasalRate;
    }

    public void setActiveBasalProfile(BasalProfile activeBasalProfile) {
        this.activeBasalProfile = activeBasalProfile;
    }

    public void setActiveBasalProfileName(String activeBasalProfileName) {
        this.activeBasalProfileName = activeBasalProfileName;
    }

    public void setActiveBasalRate(double activeBasalRate) {
        this.activeBasalRate = activeBasalRate;
    }
}

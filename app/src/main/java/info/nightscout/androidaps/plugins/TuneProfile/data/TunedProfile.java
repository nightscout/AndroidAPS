package info.nightscout.androidaps.plugins.TuneProfile.data;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.general.tidepool.elements.ProfileElement;

public class TunedProfile extends Profile {
    private Profile profile;
    private ProfileValue pv;

    TunedProfile (Profile currentProfile) {
        profile=currentProfile;

    }

    public void setCR(int cr) {

    }

    public void setISF(Double isf) {


    }

    double getAvgISF() {
        Double AvgISF=0d;

        return AvgISF;
    }

    double getAvgCR() {
        Double AvgISF=0d;

        return AvgISF;
    }

    public Profile getProfile() {
        //todo add code to update profile with basal data, ISF and CR


        return profile;
    }

}

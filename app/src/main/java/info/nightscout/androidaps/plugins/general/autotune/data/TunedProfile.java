package info.nightscout.androidaps.plugins.general.autotune.data;

import info.nightscout.androidaps.data.Profile;

public class TunedProfile extends Profile {
    private Profile profile;
    private ProfileValue pv;
    public double currentBasal;
    public Double AvgISF;

    public TunedProfile (Profile currentProfile) {
        profile=currentProfile;
        getAvgISF();
    }

    public void setCR(int cr) {

    }

    public void setISF(Double isf) {


    }

    private void getAvgISF() {
        Double AvgISF=0d;


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

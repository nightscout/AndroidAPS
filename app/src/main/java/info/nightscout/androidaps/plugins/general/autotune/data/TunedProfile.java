package info.nightscout.androidaps.plugins.general.autotune.data;

import info.nightscout.androidaps.data.Profile;

public class TunedProfile  {
    private Profile profile;
    public String profilename;
    private Profile.ProfileValue pv;
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

    public double getAvgISF() {
        Double AvgISF=0d;

        return AvgISF;
    }

    public double getAvgIC() {
        Double AvgIC=0d;

        return AvgIC;
    }

    public Profile getProfile() {
        //todo add code to update profile with basal data, ISF and CR


        return profile;
    }

    public static double averageProfileValue(Profile.ProfileValue[] pf) {
        double avgValue = 0;
        int secondPerDay=24*60*60;
        if (pf == null)
            return avgValue;
        for(int i = 0; i< pf.length;i++) {
            avgValue+=pf[i].value*((i==pf.length -1 ? secondPerDay : pf[i+1].timeAsSeconds) -pf[i].timeAsSeconds);
        }
        avgValue/=secondPerDay;
        return avgValue;
    }

}

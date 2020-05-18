package info.nightscout.androidaps.plugins.general.autotune;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.StaticInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.plugins.general.autotune.data.BGDatum;
import info.nightscout.androidaps.plugins.general.autotune.data.CRDatum;
import info.nightscout.androidaps.plugins.general.autotune.data.PreppedGlucose;
import info.nightscout.androidaps.plugins.general.autotune.data.TunedProfile;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class AutotuneCore {
    @Inject ActivePluginProvider activePlugin;
    @Inject SP sp;
    @Inject AutotunePlugin autotunePlugin;
    private HasAndroidInjector injector;

    public AutotuneCore (
            HasAndroidInjector injector
    ) {
        this.injector=injector;
        this.injector.androidInjector().inject(this);
    }

    public TunedProfile tuneAllTheThings (PreppedGlucose preppedGlucose, TunedProfile previousAutotune, TunedProfile pumpProfile) {

        //var pumpBasalProfile = pumpProfile.basalprofile;
        Profile.ProfileValue[] pumpBasalProfile = pumpProfile.profile.getBasalValues();
        //console.error(pumpBasalProfile);
        Profile.ProfileValue[] basalProfile = previousAutotune.profile.getBasalValues();
        //console.error(basalProfile);
        Profile.ProfileValue[]  isfProfile = previousAutotune.profile.getIsfsMgdl();
        //console.error(isfProfile);
        Double ISF = isfProfile[0].value;
        //console.error(ISF);
        Profile.ProfileValue[] carbRatioProfile = previousAutotune.profile.getIcs();
        Double carbRatio = carbRatioProfile[0].value;
        //console.error(carbRatio);
        Double CSF = ISF / carbRatio;
        Double DIA = previousAutotune.profile.getDia();
        InsulinInterface insulinInterface = activePlugin.getActiveInsulin();
        int peak=75;
        if (insulinInterface.getId() == InsulinInterface.OREF_ULTRA_RAPID_ACTING)
            peak=55;
        else if (insulinInterface.getId() == InsulinInterface.OREF_FREE_PEAK)
            peak=sp.getInt(R.string.key_insulin_oref_peak,75);

        List<CRDatum> crData = preppedGlucose.crData;
        List<BGDatum> csfGlucoseData = preppedGlucose.csfGlucoseData;
        List<BGDatum> isfGlucoseData = preppedGlucose.isfGlucoseData;
        List<BGDatum> basalGlucoseData = preppedGlucose.basalGlucoseData;



        // to avoid error
        return previousAutotune;
    }


}

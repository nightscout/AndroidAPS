package info.nightscout.androidaps.plugins.pump.omnipod.manager;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodStorageKeys;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class AapsPodStateManager extends PodStateManager {
    private final SP sp;

    @Inject
    public AapsPodStateManager(AAPSLogger aapsLogger, SP sp) {
        super(aapsLogger);
        this.sp = sp;
    }

    @Override
    protected String readPodState() {
        return sp.getString(OmnipodStorageKeys.Preferences.POD_STATE, "");
    }

    @Override
    protected void storePodState(String podState) {
        sp.putString(OmnipodStorageKeys.Preferences.POD_STATE, podState);
    }
}

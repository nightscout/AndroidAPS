package info.nightscout.androidaps.interfaces;

import org.json.JSONObject;

import java.util.Date;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.data.Profile;

/**
 * Created by mike on 04.06.2016.
 */
public interface PumpInterface {

    boolean isInitialized();
    boolean isSuspended();
    boolean isBusy();

    // Upload to pump new basal profile
    int SUCCESS = 0;
    int FAILED = 1;
    int NOT_NEEDED = 2;
    int setNewBasalProfile(Profile profile);
    boolean isThisProfileSet(Profile profile);

    Date lastDataTime();
    void refreshDataFromPump(String reason);

    double getBaseBasalRate(); // base basal rate, not temp basal

    PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo);
    void stopBolusDelivering();
    PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, boolean enforceNew);
    PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes);
    PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes);
    //some pumps might set a very short temp close to 100% as cancelling a temp can be noisy
    //when the cancel request is requested by the user (forced), the pump should always do a real cancel
    PumpEnactResult cancelTempBasal(boolean force);
    PumpEnactResult cancelExtendedBolus();

    // Status to be passed to NS
    JSONObject getJSONStatus();
    String deviceID();

    // Pump capabilities
    PumpDescription getPumpDescription();

    // Short info for SMS, Wear etc
    String shortStatus(boolean veryShort);

    boolean isFakingTempsByExtendedBoluses();
}

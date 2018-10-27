package info.nightscout.androidaps.plugins.PumpMedtronic.data;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryEntry;

/**
 * Created by andy on 10/12/18.
 */

public class MedtronicHistoryData {

    List<PumpHistoryEntry> history = null;
    private boolean suspended = false;
    private boolean relevantConfigurationChanged = false;
    private boolean basalProfileChanged = true;
    private LocalDateTime lastHistoryRecordTime;


    public MedtronicHistoryData() {
        this.history = new ArrayList<>();
    }


    // TODO
    public boolean isSuspended() {
        return suspended;
    }


    // TODO implement logic here fror config changes
    public boolean hasRelevantConfigurationChanged() {
        return relevantConfigurationChanged;
    }


    public void resetRelevantConfigurationChanged() {
        relevantConfigurationChanged = false;
    }


    // TODO implement logic to see if Basalrates changed from last time
    public boolean hasBasalProfileChanged() {
        return basalProfileChanged;
    }


    public void resetBasalProfileChanged() {
        basalProfileChanged = true; // FIXME when this works this should reset to false
    }


    public void setLastHistoryRecordTime(LocalDateTime lastHistoryRecordTime) {

        this.lastHistoryRecordTime = lastHistoryRecordTime;
    }


    public LocalDateTime getLastHistoryRecordTime() {

        return lastHistoryRecordTime;
    }
}

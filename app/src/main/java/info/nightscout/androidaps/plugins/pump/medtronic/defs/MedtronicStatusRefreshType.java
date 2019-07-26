package info.nightscout.androidaps.plugins.pump.medtronic.defs;

import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by andy on 6/28/18.
 */

public enum MedtronicStatusRefreshType {

    PumpHistory(5, null), //
    Configuration(0, null), //
    RemainingInsulin(-1, MedtronicCommandType.GetRemainingInsulin), //
    BatteryStatus(55, MedtronicCommandType.GetBatteryStatus), //
    PumpTime(60, MedtronicCommandType.GetRealTimeClock) //
    ;

    private int refreshTime;
    private MedtronicCommandType commandType;


    MedtronicStatusRefreshType(int refreshTime, MedtronicCommandType commandType) {
        this.refreshTime = refreshTime;
        this.commandType = commandType;
    }


    public int getRefreshTime() {
        return refreshTime;
    }


    public MedtronicCommandType getCommandType() {
        if (this == Configuration) {
            return MedtronicCommandType.getSettings(MedtronicUtil.getMedtronicPumpModel());
        } else
            return commandType;
    }
}

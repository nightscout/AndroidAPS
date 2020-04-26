package info.nightscout.androidaps.plugins.pump.common.defs;

/**
 * Created by andy on 5/12/18.
 */

public enum PumpStatusType {
    Running("normal"), //
    Suspended("suspended") //
    ;

    private String statusString;


    PumpStatusType(String statusString) {
        this.statusString = statusString;
    }


    public String getStatus() {
        return statusString;
    }
}

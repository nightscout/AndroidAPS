package info.nightscout.androidaps.plugins.pump.medtronic.comm.history;

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 * Author: Andy {andy.rozman@gmail.com}
 */

public enum RecordDecodeStatus {
    OK("OK     "), //
    Ignored("IGNORE "), //
    NotSupported("N/A YET"), //
    Error("ERROR  "), //
    WIP("WIP    "), //
    Unknown("UNK    ");

    String description;


    RecordDecodeStatus(String description) {
        this.description = description;

    }


    public String getDescription() {
        return description;
    }
}

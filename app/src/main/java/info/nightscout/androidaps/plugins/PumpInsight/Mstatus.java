package info.nightscout.androidaps.plugins.PumpInsight;

import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightCallback;

/**
 * Created by jamorham on 01/02/2018.
 *
 * Encapsulates results from commands
 */

class Mstatus {

    Cstatus cstatus = Cstatus.UNKNOWN;
    EventInsightCallback event;

    // comment field preparation for results
    String getCommandComment() {
        if (success()) {
            return "OK";
        } else {
            return (event == null) ? "EVENT DATA IS NULL - ERROR OR FIREWALL ENABLED?" : event.message;
        }
    }

    boolean success() {
        return cstatus.success();
    }

    int getResponseID() {
        if (success()) {
            return event.response_id;
        } else {
            return -2; // invalid
        }
    }

    Object getResponseObject() {
        if (success()) {
            return event.response_object;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return cstatus + " " + event;
    }

}

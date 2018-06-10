package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.data;

import org.joda.time.LocalDateTime;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkServiceState;

/**
 * Created by andy on 5/19/18.
 */

public class RLHistoryItem {

    private LocalDateTime dateTime;
    private RileyLinkServiceState serviceState;
    private RileyLinkError errorCode;


    public RLHistoryItem(RileyLinkServiceState serviceState, RileyLinkError errorCode) {
        this.dateTime = new LocalDateTime();
        this.serviceState = serviceState;
        this.errorCode = errorCode;
    }


    public LocalDateTime getDateTime() {
        return dateTime;
    }


    public RileyLinkServiceState getServiceState() {
        return serviceState;
    }


    public RileyLinkError getErrorCode() {
        return errorCode;
    }
}

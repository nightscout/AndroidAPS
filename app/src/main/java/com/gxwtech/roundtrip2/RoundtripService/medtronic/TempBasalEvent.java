package com.gxwtech.roundtrip2.RoundtripService.medtronic;


import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.TempBasalPair;

import org.joda.time.LocalDateTime;

/**
 * Created by geoff on 6/8/15.
 *
 * In the pump's history, temp basals are recorded as 1) a TempBasalRatePumpEvent event,
 * with a timestamp and a rate, and 2) as a separate TempBasalDurationPumpEvent event, with a timestamp
 * and a duration.  This is inconvenient for the rest of the software, so this class puts the two
 * together as a timestamp, duration, and rate in one package.
 *
 */
@Deprecated
public class TempBasalEvent {
    public LocalDateTime mTimestamp;
    public TempBasalPair mBasalPair;
    public TempBasalEvent() {
        mTimestamp = new LocalDateTime();
        mBasalPair = new TempBasalPair();
    }
    public TempBasalEvent(LocalDateTime timestamp, TempBasalPair pair) {
        mTimestamp = timestamp;
        mBasalPair = pair;
    }
}

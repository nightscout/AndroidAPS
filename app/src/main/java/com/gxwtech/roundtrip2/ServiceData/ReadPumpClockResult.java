package com.gxwtech.roundtrip2.ServiceData;

import android.os.Bundle;
import android.util.Log;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Created by geoff on 6/25/16.
 */
public class ReadPumpClockResult extends ServiceResult {
    private static final String TAG="ReadPumpClockResult";
    public ReadPumpClockResult() {}

    @Override
    public void init() {
        map.putString("ServiceMessageType","ReadPumpClockResult");
    }

    public void setTime(LocalDateTime pumpTime) {
        Bundle map = getMap();
        DateTimeFormatter fmt = DateTimeFormat.forStyle("FF");
        map.putString("PumpTime",fmt.print(pumpTime));
        setMap(map);
    }

    public LocalDateTime getTime() {
        LocalDateTime rval = new LocalDateTime(1900,1,1,1,1);
        Bundle map = getMap();
        if (map != null) {
            String timeString = map.getString("PumpTime");
            if (timeString != null) {
                DateTimeFormatter fmt = DateTimeFormat.forStyle("FF");
                try {
                    rval = fmt.parseLocalDateTime(timeString);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG,"getTime: failed to parse time from '"+timeString+"'");
                }
            }
        }
        return rval;
    }

    public String getTimeString() {
        Bundle map = getMap();
        if (map != null) {
            String rval = map.getString("PumpTime");
            if (rval != null) {
                return rval;
            }
        }
        return "";
    }

    // This can be overridden by subclasses -- essentially it allows
    // casting from the base class to the subclass.
    public void initFromServiceResult(ServiceResult serviceResult) {
        setMap(serviceResult.getMap());
    }
}

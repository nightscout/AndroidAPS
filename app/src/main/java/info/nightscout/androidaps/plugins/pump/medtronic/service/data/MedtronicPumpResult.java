package info.nightscout.androidaps.plugins.pump.medtronic.service.data;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceResult;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;

/**
 * Created by geoff on 6/25/16.
 */
public class MedtronicPumpResult extends ServiceResult {

    // private static final String TAG = "ReadPumpClockResult";
    // Map<String,Object> resultMap = new HashMap<>();
    private static final Logger LOG = LoggerFactory.getLogger(MedtronicPumpResult.class);


    public MedtronicPumpResult(MedtronicCommandType commandType) {
        map.putString("ServiceMessageType", commandType.name());
    }


    @Override
    public void init() {
    }


    public void addParameter(String parameter, String value) {
        map.putString(parameter, value);
    }


    public void addParameter(String parameter, Float value) {
        map.putFloat(parameter, value);
    }


    public void setError() {
        map.putBoolean("Error", true);
    }


    // public void addParameter(String parameter, String value)
    // {
    // map.put(parameter, value);
    // }

    // public void setTime(LocalDateTime pumpTime) {
    // Bundle map = getMap();
    // DateTimeFormatter fmt = DateTimeFormat.forStyle("FF");
    // map.putString("PumpTime", fmt.print(pumpTime));
    // setMap(map);
    // }

    public void addParameter(String key, LocalDateTime time) {
        DateTimeFormatter fmt = DateTimeFormat.forStyle("FF");
        map.putString(key, fmt.print(time));
    }


    public LocalDateTime getTimeParameter(String key) {
        LocalDateTime rval = new LocalDateTime(1900, 1, 1, 1, 1);
        Bundle map = getMap();
        if (map != null) {
            String timeString = map.getString(key);
            if (timeString != null) {
                DateTimeFormatter fmt = DateTimeFormat.forStyle("FF");
                try {
                    rval = fmt.parseLocalDateTime(timeString);
                } catch (IllegalArgumentException e) {
                    LOG.error("getTime: failed to parse time from '" + timeString + "'");
                }
            }
        }
        return rval;
    }


    public Float getFloatParameter(String key) {
        return map.getFloat(key, 0.0f);
    }


    // This can be overridden by subclasses -- essentially it allows
    // casting from the base class to the subclass.
    public void initFromServiceResult(ServiceResult serviceResult) {
        setMap(serviceResult.getMap());
    }
}

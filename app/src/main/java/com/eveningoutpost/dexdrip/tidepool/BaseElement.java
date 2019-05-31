package com.eveningoutpost.dexdrip.tidepool;

import com.google.gson.annotations.Expose;

/**
 * jamorham
 * <p>
 * common element base
 */

public abstract class BaseElement {
    @Expose
    public String deviceTime;
    @Expose
    public String time;
    @Expose
    public int timezoneOffset;
    @Expose
    public String type;
    @Expose
    public Origin origin;


    BaseElement populate(final long timestamp, final String uuid) {
        deviceTime = DateUtil.toFormatNoZone(timestamp);
        time = DateUtil.toFormatAsUTC(timestamp);
        timezoneOffset = DateUtil.getTimeZoneOffsetMinutes(timestamp); // TODO
        origin = new Origin(uuid);
        return this;
    }

    public class Origin {
        @Expose
        String id;

        Origin(String id) {
            this.id = id;
        }
    }
}

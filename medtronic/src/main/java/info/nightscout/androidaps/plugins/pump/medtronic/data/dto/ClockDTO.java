package info.nightscout.androidaps.plugins.pump.medtronic.data.dto;

import org.joda.time.LocalDateTime;

/**
 * Created by andy on 2/27/19.
 */

public class ClockDTO {

    public LocalDateTime localDeviceTime;

    public LocalDateTime pumpTime;

    public int timeDifference; // s (pump -> local)
}

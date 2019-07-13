package info.nightscout.androidaps.interaction.utils;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Created by andy on 3/5/19.
 */

public class WearUtil {


    public static String dateTimeText(long timeInMs) {
        Date d = new Date(timeInMs);
        return "" + d.getDay() + "." + d.getMonth() + "." + d.getYear() + " " + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds();
    }


}

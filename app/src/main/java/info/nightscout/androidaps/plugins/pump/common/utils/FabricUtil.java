package info.nightscout.androidaps.plugins.pump.common.utils;

import java.util.Map;

import com.crashlytics.android.answers.AnswersEvent;
import com.crashlytics.android.answers.CustomEvent;
import com.google.common.base.Splitter;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.utils.FabricPrivacy;

/**
 * Created by andy on 10/26/18.
 */

public class FabricUtil {

    public static void createEvent(String eventName, Map<String, String> map) {

        CustomEvent customEvent = new CustomEvent("MedtronicDecode4b6bError") //
            .putCustomAttribute("buildversion", BuildConfig.BUILDVERSION) //
            .putCustomAttribute("version", BuildConfig.VERSION);

        int attributes = 2;
        boolean interrupted = false;

        if (map != null) {
            for (Map.Entry<String, String> entry : map.entrySet()) {

                int part = 1;

                if (interrupted)
                    break;

                for (final String token : Splitter.fixedLength(AnswersEvent.MAX_STRING_LENGTH).split(entry.getValue())) {

                    if (attributes == AnswersEvent.MAX_NUM_ATTRIBUTES) {
                        interrupted = true;
                        break;
                    }

                    customEvent.putCustomAttribute(entry.getKey() + "_" + part, token);
                    attributes++;
                }
            }
        }

        FabricPrivacy.getInstance().logCustom(customEvent);
    }

}

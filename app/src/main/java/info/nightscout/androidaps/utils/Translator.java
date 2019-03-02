package info.nightscout.androidaps.utils;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 15.07.2016.
 */
public class Translator {

    public static String translate(String text) {
        switch (text) {

            case "BG Check":
                return MainApp.gs(R.string.careportal_bgcheck);
            case "Snack Bolus":
                return MainApp.gs(R.string.careportal_snackbolus);
            case "Meal Bolus":
                return MainApp.gs(R.string.careportal_mealbolus);
            case "Correction Bolus":
                return MainApp.gs(R.string.careportal_correctionbolus);
            case "Carb Correction":
                return MainApp.gs(R.string.careportal_carbscorrection);
            case "Combo Bolus":
                return MainApp.gs(R.string.careportal_combobolus);
            case "Announcement":
                return MainApp.gs(R.string.careportal_announcement);
            case "Note":
                return MainApp.gs(R.string.careportal_note);
            case "Question":
                return MainApp.gs(R.string.careportal_question);
            case "Exercise":
                return MainApp.gs(R.string.careportal_exercise);
            case "Site Change":
                return MainApp.gs(R.string.careportal_pumpsitechange);
            case "Sensor Start":
                return MainApp.gs(R.string.careportal_cgmsensorstart);
            case "Sensor Change":
                return MainApp.gs(R.string.careportal_cgmsensorinsert);
            case "Insulin Change":
                return MainApp.gs(R.string.careportal_insulincartridgechange);
            case "Temp Basal Start":
                return MainApp.gs(R.string.careportal_tempbasalstart);
            case "Temp Basal End":
                return MainApp.gs(R.string.careportal_tempbasalend);
            case "Profile Switch":
                return MainApp.gs(R.string.careportal_profileswitch);
            case "Temporary Target":
                return MainApp.gs(R.string.careportal_temporarytarget);
            case "Temporary Target Cancel":
                return MainApp.gs(R.string.careportal_temporarytargetcancel);
            case "OpenAPS Offline":
                return MainApp.gs(R.string.careportal_openapsoffline);
            case "Finger":
                return MainApp.gs(R.string.glucosetype_finger);
            case "Sensor":
                return MainApp.gs(R.string.glucosetype_sensor);
            case "Manual":
                return MainApp.gs(R.string.manual);
        }
        return text;
    }
}

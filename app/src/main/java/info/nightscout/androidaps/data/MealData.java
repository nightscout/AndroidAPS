package info.nightscout.androidaps.data;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.client.data.NSProfile;

/**
 * Created by mike on 04.01.2017.
 */
public class MealData {
    public double boluses = 0d;
    public double carbs = 0d;
    public double mealCOB = 0.0d; // TODO: add calculation for AMA

    public void addTreatment(Treatment treatment) {
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null) return;

        long now = new Date().getTime();
        long dia_ago = now - (new Double(profile.getDia() * 60 * 60 * 1000l)).longValue();
        long t = treatment.created_at.getTime();
        if (t > dia_ago && t <= now) {
            if (treatment.carbs >= 1) {
                carbs += treatment.carbs;
            }
            if (treatment.insulin > 0 && treatment.mealBolus) {
                boluses += treatment.insulin;
            }
        }
    }
}

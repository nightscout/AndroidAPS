package info.nightscout.utils;

import org.json.JSONObject;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.client.data.NSProfile;

/**
 * Created by mike on 11.10.2016.
 */

public class BolusWizard {
    // Inputs
    JSONObject specificProfile = null;
    public Integer carbs = 0;
    Double bg = 0d;
    Double correction;
    Boolean includeBolusIOB = true;
    Boolean includeBasalIOB = true;

    // Intermediate
    public Double sens = 0d;
    public Double ic = 0d;

    public Double targetBGLow = 0d;
    public Double targetBGHigh = 0d;
    public Double bgDiff = 0d;

    IobTotal bolusIob;
    IobTotal basalIob;

    public Double insulinFromBG = 0d;
    public Double insulinFromCarbs = 0d;
    public Double insulingFromBolusIOB = 0d;
    public Double insulingFromBasalsIOB = 0d;
    public Double insulinFromCorrection = 0d;

    // Result
    public Double calculatedTotalInsulin = 0d;
    public Double carbsEquivalent = 0d;

    public Double doCalc(JSONObject specificProfile, Integer carbs, Double bg, Double correction, Boolean includeBolusIOB, Boolean includeBasalIOB) {
        this.specificProfile = specificProfile;
        this.carbs = carbs;
        this.bg = bg;
        this.correction = correction;

        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();

        // Insulin from BG
        sens = profile.getIsf(specificProfile, NSProfile.secondsFromMidnight());
        targetBGLow = profile.getTargetLow(specificProfile, NSProfile.secondsFromMidnight());
        targetBGHigh = profile.getTargetHigh(specificProfile, NSProfile.secondsFromMidnight());
        if (bg <= targetBGLow) {
            bgDiff = bg - targetBGLow;
        } else {
            bgDiff = bg - targetBGHigh;
        }
        insulinFromBG = bg != 0d ? bgDiff / sens : 0d;

        // Insuling from carbs
        ic = profile.getIc(specificProfile, NSProfile.secondsFromMidnight());
        insulinFromCarbs = carbs / ic;

        // Insulin from IOB
        TreatmentsInterface treatments = MainApp.getConfigBuilder().getActiveTreatments();
        TempBasalsInterface tempBasals = MainApp.getConfigBuilder().getActiveTempBasals();
        treatments.updateTotalIOB();
        tempBasals.updateTotalIOB();
        bolusIob = treatments.getLastCalculation();
        basalIob = tempBasals.getLastCalculation();

        insulingFromBolusIOB = includeBolusIOB ? -bolusIob.iob : 0d;
        insulingFromBasalsIOB = includeBasalIOB ? -basalIob.basaliob : 0d;

        // Insulin from correction
        insulinFromCorrection = correction;

        // Total
        calculatedTotalInsulin = insulinFromBG + insulinFromCarbs + insulingFromBolusIOB + insulingFromBasalsIOB + insulinFromCorrection;

        if (calculatedTotalInsulin < 0) {
            carbsEquivalent = -calculatedTotalInsulin * ic;
            calculatedTotalInsulin = 0d;
        }

        calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, 0.05d);

        return calculatedTotalInsulin;
    }
}

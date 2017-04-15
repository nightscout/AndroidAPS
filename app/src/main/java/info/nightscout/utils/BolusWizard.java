package info.nightscout.utils;

import org.json.JSONObject;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;

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
    Boolean superBolus = false;
    Boolean trend = false;

    // Intermediate
    public Double sens = 0d;
    public Double ic = 0d;

    public GlucoseStatus glucoseStatus;

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
    public Double insulinFromSuperBolus = 0d;
    public Double insulinFromCOB = 0d;
    public Double insulinFromTrend = 0d;

    // Result
    public Double calculatedTotalInsulin = 0d;
    public Double carbsEquivalent = 0d;

    public Double doCalc(JSONObject specificProfile, Integer carbs, Double cob, Double bg, Double correction, Boolean includeBolusIOB, Boolean includeBasalIOB, Boolean superBolus, Boolean trend) {
        this.specificProfile = specificProfile;
        this.carbs = carbs;
        this.bg = bg;
        this.correction = correction;
        this.superBolus = superBolus;
        this.trend = trend;

        NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();

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

        // Insulin from 15 min trend
        glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        if (glucoseStatus != null) {
            insulinFromTrend = (NSProfile.fromMgdlToUnits(glucoseStatus.short_avgdelta, profile.getUnits()) * 3) / sens;
        }

        // Insuling from carbs
        ic = profile.getIc(specificProfile, NSProfile.secondsFromMidnight());
        insulinFromCarbs = carbs / ic;
        insulinFromCOB = -cob / ic;

        // Insulin from IOB
        // IOB calculation
        TreatmentsInterface treatments = ConfigBuilderPlugin.getActiveTreatments();
        treatments.updateTotalIOB();
        IobTotal bolusIob = treatments.getLastCalculation();
        TempBasalsInterface tempBasals = ConfigBuilderPlugin.getActiveTempBasals();
        IobTotal basalIob = new IobTotal(new Date().getTime());
        if (tempBasals != null) {
            tempBasals.updateTotalIOB();
            basalIob = tempBasals.getLastCalculation().round();
        }

        insulingFromBolusIOB = includeBolusIOB ? -bolusIob.iob : 0d;
        insulingFromBasalsIOB = includeBasalIOB ? -basalIob.basaliob : 0d;

        // Insulin from correction
        insulinFromCorrection = correction;

        // Insulin from superbolus for 2h. Get basal rate now and after 1h
        if (superBolus) {
            insulinFromSuperBolus = profile.getBasal(NSProfile.secondsFromMidnight());
            long timeAfter1h = new Date().getTime();
            timeAfter1h += 60L * 60 * 1000;
            insulinFromSuperBolus += profile.getBasal(NSProfile.secondsFromMidnight(new Date(timeAfter1h)));
        }

        // Total
        calculatedTotalInsulin = insulinFromBG + insulinFromTrend + insulinFromCarbs + insulingFromBolusIOB + insulingFromBasalsIOB + insulinFromCorrection + insulinFromSuperBolus + insulinFromCOB;

        if (calculatedTotalInsulin < 0) {
            carbsEquivalent = -calculatedTotalInsulin * ic;
            calculatedTotalInsulin = 0d;
        }

        calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, 0.05d);

        return calculatedTotalInsulin;
    }
}

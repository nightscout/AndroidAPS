package info.nightscout.utils;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;

/**
 * Created by mike on 11.10.2016.
 */

public class BolusWizard {
    // Inputs
    Profile specificProfile = null;
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
    public Double totalBeforePercentageAdjustment = 0d;
    public Double carbsEquivalent = 0d;

    public Double doCalc(Profile specificProfile, Integer carbs, Double cob, Double bg, Double correction, Boolean includeBolusIOB, Boolean includeBasalIOB, Boolean superBolus, Boolean trend) {
        return doCalc(specificProfile, carbs, cob, bg, correction, 100d, includeBolusIOB, includeBasalIOB, superBolus, trend);
    }

        public Double doCalc(Profile specificProfile, Integer carbs, Double cob, Double bg, Double correction, double percentageCorrection, Boolean includeBolusIOB, Boolean includeBasalIOB, Boolean superBolus, Boolean trend) {
        this.specificProfile = specificProfile;
        this.carbs = carbs;
        this.bg = bg;
        this.correction = correction;
        this.superBolus = superBolus;
        this.trend = trend;


        // Insulin from BG
        sens = specificProfile.getIsf();
        targetBGLow = specificProfile.getTargetLow();
        targetBGHigh = specificProfile.getTargetHigh();
        if (bg <= targetBGLow) {
            bgDiff = bg - targetBGLow;
        } else {
            bgDiff = bg - targetBGHigh;
        }
        insulinFromBG = bg != 0d ? bgDiff / sens : 0d;

        // Insulin from 15 min trend
        glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        if (glucoseStatus != null && trend) {
            insulinFromTrend = (Profile.fromMgdlToUnits(glucoseStatus.short_avgdelta, specificProfile.getUnits()) * 3) / sens;
        }

        // Insuling from carbs
        ic = specificProfile.getIc();
        insulinFromCarbs = carbs / ic;
        insulinFromCOB = cob / ic;

        // Insulin from IOB
        // IOB calculation
        TreatmentsInterface treatments = MainApp.getConfigBuilder();
        treatments.updateTotalIOBTreatments();
        IobTotal bolusIob = treatments.getLastCalculationTreatments().round();
        treatments.updateTotalIOBTempBasals();
        IobTotal basalIob = treatments.getLastCalculationTempBasals().round();

        insulingFromBolusIOB = includeBolusIOB ? -bolusIob.iob : 0d;
        insulingFromBasalsIOB = includeBasalIOB ? -basalIob.basaliob : 0d;

        // Insulin from correction
        insulinFromCorrection = correction;

        // Insulin from superbolus for 2h. Get basal rate now and after 1h
        if (superBolus) {
            insulinFromSuperBolus = specificProfile.getBasal();
            long timeAfter1h = System.currentTimeMillis();
            timeAfter1h += 60L * 60 * 1000;
            insulinFromSuperBolus += specificProfile.getBasal(timeAfter1h);
        }

        // Total
            calculatedTotalInsulin = totalBeforePercentageAdjustment = insulinFromBG + insulinFromTrend + insulinFromCarbs + insulingFromBolusIOB + insulingFromBasalsIOB + insulinFromCorrection + insulinFromSuperBolus + insulinFromCOB;

            //percentage
            if(totalBeforePercentageAdjustment > 0){
                calculatedTotalInsulin = totalBeforePercentageAdjustment*percentageCorrection/100d;
            }


        if (calculatedTotalInsulin < 0) {
            carbsEquivalent = -calculatedTotalInsulin * ic;
            calculatedTotalInsulin = 0d;
        }

        double bolusStep = MainApp.getConfigBuilder().getPumpDescription().bolusStep;
        calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, bolusStep);

        return calculatedTotalInsulin;
    }
}

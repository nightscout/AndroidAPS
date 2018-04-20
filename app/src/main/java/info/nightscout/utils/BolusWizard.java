package info.nightscout.utils;

import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;

/**
 * Created by mike on 11.10.2016.
 */

public class BolusWizard {
    // Inputs
    private Profile specificProfile = null;
    private TempTarget tempTarget;
    public Integer carbs = 0;
    private Double bg = 0d;
    private Double correction;
    private Boolean includeBolusIOB = true;
    private Boolean includeBasalIOB = true;
    public Boolean superBolus = false;
    private Boolean trend = false;

    // Intermediate
    public Double sens = 0d;
    public Double ic = 0d;

    public GlucoseStatus glucoseStatus;

    public Double targetBGLow = 0d;
    public Double targetBGHigh = 0d;
    public Double bgDiff = 0d;

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

    public Double doCalc(Profile specificProfile, TempTarget tempTarget, Integer carbs, Double cob, Double bg, Double correction, Boolean includeBolusIOB, Boolean includeBasalIOB, Boolean superBolus, Boolean trend) {
        return doCalc(specificProfile, tempTarget, carbs, cob, bg, correction, 100d, includeBolusIOB, includeBasalIOB, superBolus, trend);
    }

    public Double doCalc(Profile specificProfile, TempTarget tempTarget, Integer carbs, Double cob, Double bg, Double correction, double percentageCorrection, Boolean includeBolusIOB, Boolean includeBasalIOB, Boolean superBolus, Boolean trend) {
        this.specificProfile = specificProfile;
        this.tempTarget = tempTarget;
        this.carbs = carbs;
        this.bg = bg;
        this.correction = correction;
        this.includeBolusIOB = includeBolusIOB;
        this.includeBasalIOB = includeBasalIOB;
        this.superBolus = superBolus;
        this.trend = trend;

        // Insulin from BG
        sens = specificProfile.getIsf();
        targetBGLow = specificProfile.getTargetLow();
        targetBGHigh = specificProfile.getTargetHigh();
        if (tempTarget != null) {
            targetBGLow = Profile.fromMgdlToUnits(tempTarget.low, specificProfile.getUnits());
            targetBGHigh = Profile.fromMgdlToUnits(tempTarget.high, specificProfile.getUnits());
        }
        if (bg >= targetBGLow && bg <= targetBGHigh) {
            bgDiff = 0d;
        } else if (bg <= targetBGLow) {
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
        TreatmentsInterface treatments = TreatmentsPlugin.getPlugin();
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
        calculatedTotalInsulin = insulinFromBG + insulinFromTrend + insulinFromCarbs + insulingFromBolusIOB + insulingFromBasalsIOB + insulinFromCorrection + insulinFromSuperBolus + insulinFromCOB;

        // Percentage adjustment
        totalBeforePercentageAdjustment = calculatedTotalInsulin;
        if (calculatedTotalInsulin > 0) {
            calculatedTotalInsulin = calculatedTotalInsulin * percentageCorrection / 100d;
        }

        if (calculatedTotalInsulin < 0) {
            carbsEquivalent = -calculatedTotalInsulin * ic;
            calculatedTotalInsulin = 0d;
        }

        double bolusStep = ConfigBuilderPlugin.getActivePump().getPumpDescription().bolusStep;
        calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, bolusStep);

        return calculatedTotalInsulin;
    }
}

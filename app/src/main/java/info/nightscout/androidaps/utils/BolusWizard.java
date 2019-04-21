package info.nightscout.androidaps.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;

/**
 * Created by mike on 11.10.2016.
 */

public class BolusWizard {
    private Logger log = LoggerFactory.getLogger(L.CORE);
    // Inputs
    private Profile specificProfile = null;
    private TempTarget tempTarget;
    public Integer carbs = 0;
    private Double bg = 0d;
    private Double cob = 0d;
    private Double correction;
    private Double percentageCorrection;
    private Boolean includeBolusIOB = true;
    private Boolean includeBasalIOB = true;
    public Boolean superBolus = false;
    private Boolean trend = false;

    // Intermediate
    public double sens = 0d;
    public double ic = 0d;

    public GlucoseStatus glucoseStatus;

    public double targetBGLow = 0d;
    public double targetBGHigh = 0d;
    public double bgDiff = 0d;

    public double insulinFromBG = 0d;
    public double insulinFromCarbs = 0d;
    public double insulingFromBolusIOB = 0d;
    public double insulingFromBasalsIOB = 0d;
    public double insulinFromCorrection = 0d;
    public double insulinFromSuperBolus = 0d;
    public double insulinFromCOB = 0d;
    public double insulinFromTrend = 0d;

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
        this.cob = cob;
        this.correction = correction;
        this.percentageCorrection = percentageCorrection;
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
            timeAfter1h += T.hours(1).msecs();
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

        double bolusStep = ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().bolusStep;
        calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, bolusStep);

        log.debug(log());

        return calculatedTotalInsulin;
    }

    public String log() {
        StringBuilder sb = new StringBuilder();

        sb.append("TempTarget=").append(tempTarget != null ? tempTarget.toString() : "null").append("; ");
        sb.append("Carbs=").append(carbs != null ? carbs : null).append("; ");
        sb.append("Bg=").append(bg).append("; ");
        sb.append("Cob=").append(cob).append("; ");
        sb.append("Correction=").append(correction).append("; ");
        sb.append("PercentageCorrection=").append(percentageCorrection).append("; ");
        sb.append("IncludeBolusIOB=").append(includeBolusIOB).append("; ");
        sb.append("IncludeBasalIOB=").append(includeBasalIOB).append("; ");
        sb.append("Superbolus=").append(superBolus).append("; ");
        sb.append("Trend=").append(trend).append("; ");
        sb.append("Profile=").append(specificProfile != null && specificProfile.getData() != null ? specificProfile.getData().toString() : "null").append("; ");
        sb.append("\n");

        sb.append("targetBGLow=").append(targetBGLow).append("; ");
        sb.append("targetBGHigh=").append(targetBGHigh).append("; ");
        sb.append("bgDiff=").append(bgDiff).append("; ");
        sb.append("insulinFromBG=").append(insulinFromBG).append("; ");
        sb.append("insulinFromCarbs=").append(insulinFromCarbs).append("; ");
        sb.append("insulingFromBolusIOB=").append(insulingFromBolusIOB).append("; ");
        sb.append("insulingFromBasalsIOB=").append(insulingFromBasalsIOB).append("; ");
        sb.append("insulinFromCorrection=").append(insulinFromCorrection).append("; ");
        sb.append("insulinFromSuperBolus=").append(insulinFromSuperBolus).append("; ");
        sb.append("insulinFromCOB=").append(insulinFromCOB).append("; ");
        sb.append("insulinFromTrend=").append(insulinFromTrend).append("; ");
        sb.append("\n");


        sb.append("calculatedTotalInsulin=").append(calculatedTotalInsulin).append("; ");
        sb.append("totalBeforePercentageAdjustment=").append(totalBeforePercentageAdjustment).append("; ");
        sb.append("carbsEquivalent=").append(carbsEquivalent).append("; ");

        return sb.toString();
    }
}

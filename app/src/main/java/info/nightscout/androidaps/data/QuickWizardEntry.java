package info.nightscout.androidaps.data;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.BolusWizard;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 25.12.2017.
 */

public class QuickWizardEntry {
    private static Logger log = LoggerFactory.getLogger(QuickWizardEntry.class);

    public JSONObject storage;
    public int position;

    public static final int YES = 0;
    public static final int NO = 1;
    public static final int POSITIVE_ONLY = 2;
    public static final int NEGATIVE_ONLY = 3;

    /*
        {
            buttonText: "Meal",
            carbs: 36,
            validFrom: 8 * 60 * 60, // seconds from midnight
            validTo: 9 * 60 * 60,   // seconds from midnight
            useBG: 0,
            useCOB: 0,
            useBolusIOB: 0,
            useBasalIOB: 0,
            useTrend: 0,
            useSuperBolus: 0,
            useTemptarget: 0
        }
     */
    QuickWizardEntry() {
        String emptyData = "{\"buttonText\":\"\",\"carbs\":0,\"validFrom\":0,\"validTo\":86340}";
        try {
            storage = new JSONObject(emptyData);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        position = -1;
    }

    QuickWizardEntry(JSONObject entry, int position) {
        storage = entry;
        this.position = position;
    }

    Boolean isActive() {
        return Profile.secondsFromMidnight() >= validFrom() && Profile.secondsFromMidnight() <= validTo();
    }

    public BolusWizard doCalc(Profile profile, String profileName, BgReading lastBG, boolean _synchronized) {
        final TempTarget tempTarget = TreatmentsPlugin.getPlugin().getTempTargetFromHistory();
        //BG
        double bg = 0;
        if (lastBG != null && useBG() == YES) {
            bg = lastBG.valueToUnits(profile.getUnits());
        }

        // COB
        double cob = 0d;
        if (useCOB() == YES) {
            CobInfo cobInfo = IobCobCalculatorPlugin.getPlugin().getCobInfo(_synchronized, "QuickWizard COB");
            if (cobInfo.displayCob != null)
                cob = cobInfo.displayCob;
        }

        // Bolus IOB
        boolean bolusIOB = false;
        if (useBolusIOB() == YES) {
            bolusIOB = true;
        }

        // Basal IOB
        TreatmentsInterface treatments = TreatmentsPlugin.getPlugin();
        treatments.updateTotalIOBTempBasals();
        IobTotal basalIob = treatments.getLastCalculationTempBasals().round();
        boolean basalIOB = false;
        if (useBasalIOB() == YES) {
            basalIOB = true;
        } else if (useBasalIOB() == POSITIVE_ONLY && basalIob.iob > 0) {
            basalIOB = true;
        } else if (useBasalIOB() == NEGATIVE_ONLY && basalIob.iob < 0) {
            basalIOB = true;
        }

        // SuperBolus
        boolean superBolus = false;
        if (useSuperBolus() == YES && SP.getBoolean(R.string.key_usesuperbolus, false)) {
            superBolus = true;
        }
        final LoopPlugin loopPlugin = LoopPlugin.getPlugin();
        if (loopPlugin.isEnabled(loopPlugin.getType()) && loopPlugin.isSuperBolus())
            superBolus = false;

        // Trend
        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        boolean trend = false;
        if (useTrend() == YES) {
            trend = true;
        } else if (useTrend() == POSITIVE_ONLY && glucoseStatus != null && glucoseStatus.short_avgdelta > 0) {
            trend = true;
        } else if (useTrend() == NEGATIVE_ONLY && glucoseStatus != null && glucoseStatus.short_avgdelta < 0) {
            trend = true;
        }

        return new BolusWizard(profile, profileName, tempTarget, carbs(), cob, bg, 0d, 100, true, useCOB() == YES, bolusIOB, basalIOB, superBolus, useTempTarget() == YES, trend, "QuickWizard");
    }

    public String buttonText() {
        try {
            return storage.getString("buttonText");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return "";
    }

    public Integer carbs() {
        try {
            return storage.getInt("carbs");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return 0;
    }

    public Date validFromDate() {
        return DateUtil.toDate(validFrom());
    }

    public Date validToDate() {
        return DateUtil.toDate(validTo());
    }

    public Integer validFrom() {
        try {
            return storage.getInt("validFrom");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return 0;
    }

    public Integer validTo() {
        try {
            return storage.getInt("validTo");
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return 0;
    }

    public int useBG() {
        try {
            return storage.getInt("useBG");
        } catch (JSONException e) {
            //log.error("Unhandled exception", e);
        }
        return YES;
    }

    public int useCOB() {
        try {
            return storage.getInt("useCOB");
        } catch (JSONException e) {
            //log.error("Unhandled exception", e);
        }
        return NO;
    }

    public int useBolusIOB() {
        try {
            return storage.getInt("useBolusIOB");
        } catch (JSONException e) {
            //log.error("Unhandled exception", e);
        }
        return YES;
    }

    public int useBasalIOB() {
        try {
            return storage.getInt("useBasalIOB");
        } catch (JSONException e) {
            //log.error("Unhandled exception", e);
        }
        return YES;
    }

    public int useTrend() {
        try {
            return storage.getInt("useTrend");
        } catch (JSONException e) {
            //log.error("Unhandled exception", e);
        }
        return NO;
    }

    public int useSuperBolus() {
        try {
            return storage.getInt("useSuperBolus");
        } catch (JSONException e) {
            //log.error("Unhandled exception", e);
        }
        return NO;
    }

    public int useTempTarget() {
        try {
            return storage.getInt("useTempTarget");
        } catch (JSONException e) {
            //log.error("Unhandled exception", e);
        }
        return NO;
    }
}

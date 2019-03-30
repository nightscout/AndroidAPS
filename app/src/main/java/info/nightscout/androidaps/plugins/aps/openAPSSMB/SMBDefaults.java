package info.nightscout.androidaps.plugins.aps.openAPSSMB;

/**
 * Created by mike on 10.12.2017.
 */

public class SMBDefaults {
    // CALCULATED OR FROM PREFS

    // max_iob: 0 // if max_iob is not provided, will default to zero
    // max_daily_safety_multiplier:3
    // current_basal_safety_multiplier:4
    // autosens_max:1.2
    // autosens_min:0.7

    // USED IN AUTOSENS
    public final static boolean rewind_resets_autosens = true; // reset autosensitivity to neutral for awhile after each pump rewind

    // USED IN TARGETS
    // by default the higher end of the target range is used only for avoiding bolus wizard overcorrections
    // use wide_bg_target_range: true to force neutral temps over a wider range of eventualBGs
    public final static boolean wide_bg_target_range = false; // by default use only the low end of the pump's BG target range as OpenAPS target

    // USED IN AUTOTUNE
    public final static double autotune_isf_adjustmentFraction = 1.0; // keep autotune ISF closer to pump ISF via a weighted average of fullNewISF and pumpISF.  1.0 allows full adjustment, 0 is no adjustment from pump ISF.
    public final static double remainingCarbsFraction = 1.0; // fraction of carbs we'll assume will absorb over 4h if we don't yet see carb absorption

    // USED IN DETERMINE_BASAL
    public final static boolean low_temptarget_lowers_sensitivity = false; // lower sensitivity for temptargets <= 99.
    public final static boolean high_temptarget_raises_sensitivity = false; // raise sensitivity for temptargets >= 111.  synonym for exercise_mode
    public final static boolean sensitivity_raises_target = true; // raise BG target when autosens detects sensitivity
    public final static boolean resistance_lowers_target = false; // lower BG target when autosens detects resistance
    public final static boolean adv_target_adjustments = false; // lower target automatically when BG and eventualBG are high
    public final static boolean exercise_mode = false; // when true, > 105 mg/dL high temp target adjusts sensitivityRatio for exercise_mode. This majorly changes the behavior of high temp targets from before. synonmym for high_temptarget_raises_sensitivity
    public final static int half_basal_exercise_target = 160; // when temptarget is 160 mg/dL *and* exercise_mode=true, run 50% basal at this level (120 = 75%; 140 = 60%)
    // create maxCOB and default it to 120 because that's the most a typical body can absorb over 4 hours.
    // (If someone enters more carbs or stacks more; OpenAPS will just truncate dosing based on 120.
    // Essentially, this just limits AMA/SMB as a safety cap against excessive COB entry)
    public final static int maxCOB = 120;
    public final static boolean skip_neutral_temps = true; // ***** default false in oref1 ***** if true, don't set neutral temps
    // unsuspend_if_no_temp:false // if true, pump will un-suspend after a zero temp finishes
    // bolussnooze_dia_divisor:2 // bolus snooze decays after 1/2 of DIA
    public final static double min_5m_carbimpact = 8d; // mg/dL per 5m (8 mg/dL/5m corresponds to 24g/hr at a CSF of 4 mg/dL/g (x/5*60/4))
    public final static int remainingCarbsCap = 90; // max carbs we'll assume will absorb over 4h if we don't yet see carb absorption
    // WARNING: use SMB with caution: it can and will automatically bolus up to max_iob worth of extra insulin
    // enableUAM:true // enable detection of unannounced meal carb absorption
    public final static boolean A52_risk_enable = false;
    //public final static boolean enableSMB_with_COB = true; // ***** default false in oref1 ***** enable supermicrobolus while COB is positive
    //public final static boolean enableSMB_with_temptarget = true; // ***** default false in oref1 ***** enable supermicrobolus for eating soon temp targets
    // *** WARNING *** DO NOT USE enableSMB_always or enableSMB_after_carbs with xDrip+, Libre, or similar
    // xDrip+, LimiTTer, etc. do not properly filter out high-noise SGVs
    // Using SMB overnight with such data sources risks causing a dangerous overdose of insulin
    // if the CGM sensor reads falsely high and doesn't come down as actual BG does
    // public final static boolean enableSMB_always = false; // always enable supermicrobolus (unless disabled by high temptarget)
    // *** WARNING *** DO NOT USE enableSMB_always or enableSMB_after_carbs with xDrip+, Libre, or similar
    //public final static boolean enableSMB_after_carbs = false; // enable supermicrobolus for 6h after carbs, even with 0 COB
    //public final static boolean allowSMB_with_high_temptarget = false; // allow supermicrobolus (if otherwise enabled) even with high temp targets
    public final static int maxSMBBasalMinutes = 30; // maximum minutes of basal that can be delivered as a single SMB with uncovered COB
    // curve:"rapid-acting" // Supported curves: "bilinear", "rapid-acting" (Novolog, Novorapid, Humalog, Apidra) and "ultra-rapid" (Fiasp)
    // useCustomPeakTime:false // allows changing insulinPeakTime
    // insulinPeakTime:75 // number of minutes after a bolus activity peaks.  defaults to 55m for Fiasp if useCustomPeakTime: false
    public final static int carbsReqThreshold = 1; // grams of carbsReq to trigger a pushover
    // offline_hotspot:false // enabled an offline-only local wifi hotspot if no Internet available
}

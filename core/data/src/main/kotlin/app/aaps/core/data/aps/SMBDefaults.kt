package app.aaps.core.data.aps

@Suppress("SpellCheckingInspection")
object SMBDefaults {

    // CALCULATED OR FROM PREFS
    // max_iob: 0 // if max_iob is not provided, will default to zero
    // max_daily_safety_multiplier:3
    // current_basal_safety_multiplier:4
    // autosens_max:1.2
    // autosens_min:0.7
    // USED IN AUTOSENS
    // const val rewind_resets_autosens = true // reset autosensitivity to neutral for awhile after each pump rewind

    // USED IN TARGETS
    // by default the higher end of the target range is used only for avoiding bolus wizard overcorrections
    // use wide_bg_target_range: true to force neutral temps over a wider range of eventualBGs
    // const val wide_bg_target_range = false // by default use only the low end of the pump's BG target range as OpenAPS target

    // USED IN AUTOTUNE
    // const val autotune_isf_adjustmentFraction = 1.0 // keep autotune ISF closer to pump ISF via a weighted average of fullNewISF and pumpISF.  1.0 allows full adjustment, 0 is no adjustment from pump ISF.
    // const val remainingCarbsFraction = 1.0 // fraction of carbs we'll assume will absorb over 4h if we don't yet see carb absorption

    // USED IN DETERMINE_BASAL
    const val low_temptarget_lowers_sensitivity = false // lower sensitivity for temptargets <= 99.
    const val high_temptarget_raises_sensitivity = false // raise sensitivity for temptargets >= 111.  synonym for exercise_mode

    const val sensitivity_raises_target = true // raise BG target when autosens detects sensitivity
    const val resistance_lowers_target = false // lower BG target when autosens detects resistance
    const val adv_target_adjustments = false // lower target automatically when BG and eventualBG are high
    const val exercise_mode =
        false // when true, > 105 mg/dL high temp target adjusts sensitivityRatio for exercise_mode. This majorly changes the behavior of high temp targets from before. synonym for high_temptarget_raises_sensitivity
    const val half_basal_exercise_target = 160 // when temptarget is 160 mg/dL *and* exercise_mode=true, run 50% basal at this level (120 = 75%; 140 = 60%)

    // create maxCOB and default it to 120 because that's the most a typical body can absorb over 4 hours.
    // (If someone enters more carbs or stacks more; OpenAPS will just truncate dosing based on 120.
    // Essentially, this just limits AMA/SMB as a safety cap against excessive COB entry)
    const val maxCOB = 120

    //public final static boolean skip_neutral_temps = true; // ***** default false in oref1 ***** if true, don't set neutral temps
    // un-suspend_if_no_temp:false // if true, pump will un-suspend after a zero temp finishes
    // bolussnooze_dia_divisor:2 // bolus snooze decays after 1/2 of DIA
    const val min_5m_carbimpact = 8.0 // mg/dL per 5m (8 mg/dL/5m corresponds to 24g/hr at a CSF of 4 mg/dL/g (x/5*60/4))
    const val remainingCarbsCap = 90 // max carbs we'll assume will absorb over 4h if we don't yet see carb absorption

    // WARNING: use SMB with caution: it can and will automatically bolus up to max_iob worth of extra insulin
    // enableUAM:true // enable detection of unannounced meal carb absorption
    const val A52_risk_enable = false

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
    const val SMBInterval = 3 // minimum interval between SMBs, in minutes. (limited between 1 and 10 min)
    const val maxSMBBasalMinutes = 30 // maximum minutes of basal that can be delivered as a single SMB with uncovered COB
    const val maxUAMSMBBasalMinutes = 30 // maximum minutes of basal that can be delivered as a single SMB when IOB exceeds COB

    // curve:"rapid-acting" // Supported curves: "bilinear", "rapid-acting" (Novolog, Novorapid, Humalog, Apidra) and "ultra-rapid" (Fiasp)
    // useCustomPeakTime:false // allows changing insulinPeakTime
    // insulinPeakTime:75 // number of minutes after a bolus activity peaks.  defaults to 55m for Fiasp if useCustomPeakTime: false
    const val carbsReqThreshold = 1 // grams of carbsReq to trigger a pushover

    // offline_hotspot:false // enabled an offline-only local wifi hotspot if no Internet available
    // const val bolus_increment = 0.1 // minimum bolus that can be delivered as an SMB
}
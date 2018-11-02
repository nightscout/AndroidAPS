/*
  Determine Basal

  Released under MIT license. See the accompanying LICENSE.txt file for
  full terms and conditions

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
  THE SOFTWARE.
*/


var round_basal = require('../round-basal')

// Rounds value to 'digits' decimal places
function round(value, digits)
{
    if (! digits) { digits = 0; }
    var scale = Math.pow(10, digits);
    return Math.round(value * scale) / scale;
}

// we expect BG to rise or fall at the rate of BGI,
// adjusted by the rate at which BG would need to rise /
// fall to get eventualBG to target over 2 hours
function calculate_expected_delta(target_bg, eventual_bg, bgi) {
    // (hours * mins_per_hour) / 5 = how many 5 minute periods in 2h = 24
    var five_min_blocks = (2 * 60) / 5;
    var target_delta = target_bg - eventual_bg;
    var expectedDelta = round(bgi + (target_delta / five_min_blocks), 1);
    return expectedDelta;
}


function convert_bg(value, profile)
{
    if (profile.out_units == "mmol/L")
    {
        return round(value / 18, 1).toFixed(1);
    }
    else
    {
        return Math.round(value);
    }
}

var determine_basal = function determine_basal(glucose_status, currenttemp, iob_data, profile, autosens_data, meal_data, tempBasalFunctions, microBolusAllowed, reservoir_data) {
    var rT = {}; //short for requestedTemp

    var deliverAt = new Date();

    if (typeof profile === 'undefined' || typeof profile.current_basal === 'undefined') {
        rT.error ='Error: could not get current basal rate';
        return rT;
    }
    var profile_current_basal = round_basal(profile.current_basal, profile);
    var basal = profile_current_basal;

    var systemTime = new Date();
    var bgTime = new Date(glucose_status.date);
    var minAgo = round( (systemTime - bgTime) / 60 / 1000 ,1);

    var bg = glucose_status.glucose;
    if (bg < 39) {  //Dexcom is in ??? mode or calibrating
        rT.reason = "CGM is calibrating or in ??? state";
    }
    if (minAgo > 12 || minAgo < -5) { // Dexcom data is too old, or way in the future
        rT.reason = "If current system time "+systemTime+" is correct, then BG data is too old. The last BG data was read "+minAgo+"m ago at "+bgTime;
    }
    if (bg < 39 || minAgo > 12 || minAgo < -5) {
        if (currenttemp.rate >= basal) { // high temp is running
            rT.reason += ". Canceling high temp basal of "+currenttemp.rate;
            rT.deliverAt = deliverAt;
            rT.temp = 'absolute';
            rT.duration = 0;
            rT.rate = 0;
            return rT;
            //return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        } else if ( currenttemp.rate == 0 && currenttemp.duration > 30 ) { //shorten long zero temps to 30m
            rT.reason += ". Shortening " + currenttemp.duration + "m long zero temp to 30m. ";
            rT.deliverAt = deliverAt;
            rT.temp = 'absolute';
            rT.duration = 30;
            rT.rate = 0;
            return rT;
            //return tempBasalFunctions.setTempBasal(0, 30, profile, rT, currenttemp);
        } else { //do nothing.
            rT.reason += ". Temp " + currenttemp.rate + " <= current basal " + basal + "U/hr; doing nothing. ";
            return rT;
        }
    }

    var max_iob = profile.max_iob; // maximum amount of non-bolus IOB OpenAPS will ever deliver

    // if min and max are set, then set target to their average
    var target_bg;
    var min_bg;
    var max_bg;
    if (typeof profile.min_bg !== 'undefined') {
            min_bg = profile.min_bg;
    }
    if (typeof profile.max_bg !== 'undefined') {
            max_bg = profile.max_bg;
    }
    if (typeof profile.min_bg !== 'undefined' && typeof profile.max_bg !== 'undefined') {
        target_bg = (profile.min_bg + profile.max_bg) / 2;
    } else {
        rT.error ='Error: could not determine target_bg. ';
        return rT;
    }

    var sensitivityRatio;
    var high_temptarget_raises_sensitivity = profile.exercise_mode || profile.high_temptarget_raises_sensitivity;
    var normalTarget = 100; // evaluate high/low temptarget against 100, not scheduled basal (which might change)
    if ( profile.half_basal_exercise_target ) {
        var halfBasalTarget = profile.half_basal_exercise_target;
    } else {
        var halfBasalTarget = 160; // when temptarget is 160 mg/dL, run 50% basal (120 = 75%; 140 = 60%)
        // 80 mg/dL with low_temptarget_lowers_sensitivity would give 1.5x basal, but is limited to autosens_max (1.2x by default)
    }
    if ( high_temptarget_raises_sensitivity && profile.temptargetSet && target_bg > normalTarget + 10
        || profile.low_temptarget_lowers_sensitivity && profile.temptargetSet && target_bg < normalTarget ) {
        // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
        // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
        //sensitivityRatio = 2/(2+(target_bg-normalTarget)/40);
        var c = halfBasalTarget - normalTarget;
        sensitivityRatio = c/(c+target_bg-normalTarget);
        // limit sensitivityRatio to profile.autosens_max (1.2x by default)
        sensitivityRatio = Math.min(sensitivityRatio, profile.autosens_max);
        sensitivityRatio = round(sensitivityRatio,2);
        console.error("Sensitivity ratio set to "+sensitivityRatio+" based on temp target of "+target_bg+"; ");
    } else if (typeof autosens_data !== 'undefined' ) {
        sensitivityRatio = autosens_data.ratio;
        console.error("Autosens ratio: "+sensitivityRatio+"; ");
    }
    if (sensitivityRatio) {
        basal = profile.current_basal * sensitivityRatio;
        basal = round_basal(basal, profile);
        if (basal != profile_current_basal) {
            console.error("Adjusting basal from "+profile_current_basal+" to "+basal+"; ");
        } else {
            console.error("Basal unchanged: "+basal+"; ");
        }
    }

    // adjust min, max, and target BG for sensitivity, such that 50% increase in ISF raises target from 100 to 120
    if (profile.temptargetSet) {
        //console.error("Temp Target set, not adjusting with autosens; ");
    } else if (typeof autosens_data !== 'undefined' ) {
        if ( profile.sensitivity_raises_target && autosens_data.ratio < 1 || profile.resistance_lowers_target && autosens_data.ratio > 1 ) {
            // with a target of 100, default 0.7-1.2 autosens min/max range would allow a 93-117 target range
            min_bg = round((min_bg - 60) / autosens_data.ratio) + 60;
            max_bg = round((max_bg - 60) / autosens_data.ratio) + 60;
            new_target_bg = round((target_bg - 60) / autosens_data.ratio) + 60;
            // don't allow target_bg below 80
            new_target_bg = Math.max(80, new_target_bg);
            if (target_bg == new_target_bg) {
                console.error("target_bg unchanged: "+new_target_bg+"; ");
            } else {
                console.error("target_bg from "+target_bg+" to "+new_target_bg+"; ");
            }
            target_bg = new_target_bg;
        }
    }

    if (typeof iob_data === 'undefined' ) {
        rT.error ='Error: iob_data undefined. ';
        return rT;
    }

    var iobArray = iob_data;
    if (typeof(iob_data.length) && iob_data.length > 1) {
        iob_data = iobArray[0];
        //console.error(JSON.stringify(iob_data[0]));
    }

    if (typeof iob_data.activity === 'undefined' || typeof iob_data.iob === 'undefined' ) {
        rT.error ='Error: iob_data missing some property. ';
        return rT;
    }

    var tick;

    if (glucose_status.delta > -0.5) {
        tick = "+" + round(glucose_status.delta,0);
    } else {
        tick = round(glucose_status.delta,0);
    }
    //var minDelta = Math.min(glucose_status.delta, glucose_status.short_avgdelta, glucose_status.long_avgdelta);
    var minDelta = Math.min(glucose_status.delta, glucose_status.short_avgdelta);
    var minAvgDelta = Math.min(glucose_status.short_avgdelta, glucose_status.long_avgdelta);
    var maxDelta = Math.max(glucose_status.delta, glucose_status.short_avgdelta, glucose_status.long_avgdelta);

    var profile_sens = round(profile.sens,1)
    var sens = profile.sens;
    if (typeof autosens_data !== 'undefined' ) {
        sens = profile.sens / sensitivityRatio;
        sens = round(sens, 1);
        if (sens != profile_sens) {
            console.error("ISF from "+profile_sens+" to "+sens);
        } else {
            console.error("ISF unchanged: "+sens);
        }
        //console.error(" (autosens ratio "+sensitivityRatio+")");
    }
    console.error("; CR:",profile.carb_ratio);

    // compare currenttemp to iob_data.lastTemp and cancel temp if they don't match
    var lastTempAge;
    if (typeof iob_data.lastTemp !== 'undefined' ) {
        lastTempAge = round(( new Date().getTime() - iob_data.lastTemp.date ) / 60000); // in minutes
    // }  ---- added to not produce errors
    } else {
        lastTempAge = 0;
    }
    //console.error("currenttemp:",currenttemp,"lastTemp:",JSON.stringify(iob_data.lastTemp),"lastTempAge:",lastTempAge,"m");
    tempModulus = (lastTempAge + currenttemp.duration) % 30;
    console.error("currenttemp:",currenttemp,"lastTempAge:",lastTempAge,"m","tempModulus:",tempModulus,"m");
    rT.temp = 'absolute';
    rT.deliverAt = deliverAt;
    if ( microBolusAllowed && currenttemp && iob_data.lastTemp && currenttemp.rate != iob_data.lastTemp.rate ) {
        rT.reason = "Warning: currenttemp rate "+currenttemp.rate+" != lastTemp rate "+iob_data.lastTemp.rate+" from pumphistory; setting neutral temp of "+basal+".";
        return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
    }
    if ( currenttemp && iob_data.lastTemp && currenttemp.duration > 0 ) {
        // TODO: fix this (lastTemp.duration is how long it has run; currenttemp.duration is time left
        //if ( currenttemp.duration < iob_data.lastTemp.duration - 2) {
            //rT.reason = "Warning: currenttemp duration "+currenttemp.duration+" << lastTemp duration "+round(iob_data.lastTemp.duration,1)+" from pumphistory; setting neutral temp of "+basal+".";
            //return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        //}
        //console.error(lastTempAge, round(iob_data.lastTemp.duration,1), round(lastTempAge - iob_data.lastTemp.duration,1));
        var lastTempEnded = lastTempAge - iob_data.lastTemp.duration
        if ( lastTempEnded > 5 ) {
            rT.reason = "Warning: currenttemp running but lastTemp from pumphistory ended "+lastTempEnded+"m ago; setting neutral temp of "+basal+".";
            //console.error(currenttemp, round(iob_data.lastTemp,1), round(lastTempAge,1));
            return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        }
        // TODO: figure out a way to do this check that doesn't fail across basal schedule boundaries
        //if ( tempModulus < 25 && tempModulus > 5 ) {
            //rT.reason = "Warning: currenttemp duration "+currenttemp.duration+" + lastTempAge "+lastTempAge+" isn't a multiple of 30m; setting neutral temp of "+basal+".";
            //console.error(rT.reason);
            //return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        //}
    }

    //calculate BG impact: the amount BG "should" be rising or falling based on insulin activity alone
    var bgi = round(( -iob_data.activity * sens * 5 ), 2);
    // project deviations for 30 minutes
    var deviation = round( 30 / 5 * ( minDelta - bgi ) );
    // don't overreact to a big negative delta: use minAvgDelta if deviation is negative
    if (deviation < 0) {
        deviation = round( (30 / 5) * ( minAvgDelta - bgi ) );
        // and if deviation is still negative, use long_avgdelta
        if (deviation < 0) {
            deviation = round( (30 / 5) * ( glucose_status.long_avgdelta - bgi ) );
        }
    }

    // calculate the naive (bolus calculator math) eventual BG based on net IOB and sensitivity
    if (iob_data.iob > 0) {
        var naive_eventualBG = round( bg - (iob_data.iob * sens) );
    } else { // if IOB is negative, be more conservative and use the lower of sens, profile.sens
        var naive_eventualBG = round( bg - (iob_data.iob * Math.min(sens, profile.sens) ) );
    }
    // and adjust it for the deviation above
    var eventualBG = naive_eventualBG + deviation;
    // calculate what portion of that is due to bolussnooze
    //var bolusContrib = iob_data.bolussnooze * sens;
    // and add it back in to get snoozeBG, plus another 50% to avoid low-temping at mealtime
    //var naive_snoozeBG = round( naive_eventualBG + 1.5 * bolusContrib );
    // adjust that for deviation like we did eventualBG
    //var snoozeBG = naive_snoozeBG + deviation;

    // adjust target BG range if needed to safely bring down high BG faster without causing lows
    if ( bg > max_bg && profile.adv_target_adjustments && ! profile.temptargetSet ) {
        // with target=100, as BG rises from 100 to 160, adjustedTarget drops from 100 to 80
        var adjustedMinBG = round(Math.max(80, min_bg - (bg - min_bg)/3 ),0);
        var adjustedTargetBG =round( Math.max(80, target_bg - (bg - target_bg)/3 ),0);
        var adjustedMaxBG = round(Math.max(80, max_bg - (bg - max_bg)/3 ),0);
        // if eventualBG, naive_eventualBG, and target_bg aren't all above adjustedMinBG, don’t use it
        //console.error("naive_eventualBG:",naive_eventualBG+", eventualBG:",eventualBG);
        if (eventualBG > adjustedMinBG && naive_eventualBG > adjustedMinBG && min_bg > adjustedMinBG) {
            console.error("Adjusting targets for high BG: min_bg from "+min_bg+" to "+adjustedMinBG+"; ");
            min_bg = adjustedMinBG;
        } else {
            console.error("min_bg unchanged: "+min_bg+"; ");
        }
        // if eventualBG, naive_eventualBG, and target_bg aren't all above adjustedTargetBG, don’t use it
        if (eventualBG > adjustedTargetBG && naive_eventualBG > adjustedTargetBG && target_bg > adjustedTargetBG) {
            console.error("target_bg from "+target_bg+" to "+adjustedTargetBG+"; ");
            target_bg = adjustedTargetBG;
        } else {
            console.error("target_bg unchanged: "+target_bg+"; ");
        }
        // if eventualBG, naive_eventualBG, and max_bg aren't all above adjustedMaxBG, don’t use it
        if (eventualBG > adjustedMaxBG && naive_eventualBG > adjustedMaxBG && max_bg > adjustedMaxBG) {
            console.error("max_bg from "+max_bg+" to "+adjustedMaxBG);
            max_bg = adjustedMaxBG;
        } else {
            console.error("max_bg unchanged: "+max_bg);
        }
    }

    var expectedDelta = calculate_expected_delta(target_bg, eventualBG, bgi);
    if (typeof eventualBG === 'undefined' || isNaN(eventualBG)) {
        rT.error ='Error: could not calculate eventualBG. ';
        return rT;
    }

    // min_bg of 90 -> threshold of 65, 100 -> 70 110 -> 75, and 130 -> 85
    var threshold = min_bg - 0.5*(min_bg-40);

    //console.error(reservoir_data);

    rT = {
        'temp': 'absolute'
        , 'bg': bg
        , 'tick': tick
        , 'eventualBG': eventualBG
        //, 'snoozeBG': snoozeBG
        , 'insulinReq': 0
        , 'reservoir' : reservoir_data // The expected reservoir volume at which to deliver the microbolus (the reservoir volume from right before the last pumphistory run)
        , 'deliverAt' : deliverAt // The time at which the microbolus should be delivered
        , 'sensitivityRatio' : sensitivityRatio // autosens ratio (fraction of normal basal)
    };

    // generate predicted future BGs based on IOB, COB, and current absorption rate

    var COBpredBGs = [];
    var aCOBpredBGs = [];
    var IOBpredBGs = [];
    var UAMpredBGs = [];
    var ZTpredBGs = [];
    COBpredBGs.push(bg);
    aCOBpredBGs.push(bg);
    IOBpredBGs.push(bg);
    ZTpredBGs.push(bg);
    UAMpredBGs.push(bg);

    // enable SMB whenever we have COB or UAM is enabled
    // SMB is disabled by default, unless explicitly enabled in preferences.json
    var enableSMB=false;
    // disable SMB when a high temptarget is set
    if (! microBolusAllowed) {
        console.error("SMB disabled (!microBolusAllowed)")
    } else if (! profile.allowSMB_with_high_temptarget && profile.temptargetSet && target_bg > 100) {
        console.error("SMB disabled due to high temptarget of",target_bg);
        enableSMB=false;
    // enable SMB/UAM (if enabled in preferences) while we have COB
    } else if (profile.enableSMB_with_COB === true && meal_data.mealCOB) {
        if (meal_data.bwCarbs) {
            if (profile.A52_risk_enable) {
                console.error("Warning: SMB enabled with Bolus Wizard carbs: be sure to easy bolus 30s before using Bolus Wizard")
                enableSMB=true;
            } else {
                console.error("SMB not enabled for Bolus Wizard COB");
            }
        } else {
            console.error("SMB enabled for COB of",meal_data.mealCOB);
            enableSMB=true;
        }
    // enable SMB/UAM (if enabled in preferences) for a full 6 hours after any carb entry
    // (6 hours is defined in carbWindow in lib/meal/total.js)
    } else if (profile.enableSMB_after_carbs === true && meal_data.carbs ) {
        if (meal_data.bwCarbs) {
            if (profile.A52_risk_enable) {
            console.error("Warning: SMB enabled with Bolus Wizard carbs: be sure to easy bolus 30s before using Bolus Wizard")
            enableSMB=true;
            } else {
                console.error("SMB not enabled for Bolus Wizard carbs");
            }
        } else {
            console.error("SMB enabled for 6h after carb entry");
            enableSMB=true;
        }
    // enable SMB/UAM (if enabled in preferences) if a low temptarget is set
    } else if (profile.enableSMB_with_temptarget === true && (profile.temptargetSet && target_bg < 100)) {
        if (meal_data.bwFound) {
            if (profile.A52_risk_enable) {
                console.error("Warning: SMB enabled within 6h of using Bolus Wizard: be sure to easy bolus 30s before using Bolus Wizard")
                enableSMB=true;
            } else {
                console.error("enableSMB_with_temptarget not supported within 6h of using Bolus Wizard");
            }
        } else {
            console.error("SMB enabled for temptarget of",convert_bg(target_bg, profile));
            enableSMB=true;
        }
    // enable SMB/UAM if always-on (unless previously disabled for high temptarget)
    } else if (profile.enableSMB_always === true) {
        if (meal_data.bwFound) {
            if (profile.A52_risk_enable === true) {
                console.error("Warning: SMB enabled within 6h of using Bolus Wizard: be sure to easy bolus 30s before using Bolus Wizard")
                enableSMB=true;
            } else {
                console.error("enableSMB_always not supported within 6h of using Bolus Wizard");
            }
        } else {
            console.error("SMB enabled due to enableSMB_always");
            enableSMB=true;
        }
    } else {
        console.error("SMB disabled (no enableSMB preferences active)");
    }
    // enable UAM (if enabled in preferences)
    var enableUAM=(profile.enableUAM);


    //console.error(meal_data);
    // carb impact and duration are 0 unless changed below
    var ci = 0;
    var cid = 0;
    // calculate current carb absorption rate, and how long to absorb all carbs
    // CI = current carb impact on BG in mg/dL/5m
    ci = round((minDelta - bgi),1);
    uci = round((minDelta - bgi),1);
    // ISF (mg/dL/U) / CR (g/U) = CSF (mg/dL/g)
    if (profile.temptargetSet) {
        // if temptargetSet, use unadjusted profile.sens to allow activity mode sensitivityRatio to adjust CR
        var csf = profile.sens / profile.carb_ratio;
    } else {
        // otherwise, use autosens-adjusted sens to counteract autosens meal insulin dosing adjustments
        // so that autotuned CR is still in effect even when basals and ISF are being adjusted by autosens
        var csf = sens / profile.carb_ratio;
    }
    var maxCarbAbsorptionRate = 30; // g/h; maximum rate to assume carbs will absorb if no CI observed
    // limit Carb Impact to maxCarbAbsorptionRate * csf in mg/dL per 5m
    maxCI = round(maxCarbAbsorptionRate*csf*5/60,1)
    if (ci > maxCI) {
        console.error("Limiting carb impact from",ci,"to",maxCI,"mg/dL/5m (",maxCarbAbsorptionRate,"g/h )");
        ci = maxCI;
    }
    // set meal_carbimpact high enough to absorb all meal carbs over 6 hours
    // total_impact (mg/dL) = CSF (mg/dL/g) * carbs (g)
    //console.error(csf * meal_data.carbs);
    // meal_carbimpact (mg/dL/5m) = CSF (mg/dL/g) * carbs (g) / 6 (h) * (1h/60m) * 5 (m/5m) * 2 (for linear decay)
    //var meal_carbimpact = round((csf * meal_data.carbs / 6 / 60 * 5 * 2),1)
    var remainingCATimeMin = 3; // h; before carb absorption starts
    // adjust remainingCATime (instead of CR) for autosens
    remainingCATimeMin = remainingCATimeMin / sensitivityRatio;
    // 20 g/h means that anything <= 60g will get a remainingCATimeMin, 80g will get 4h, and 120g 6h
    // when actual absorption ramps up it will take over from remainingCATime
    var assumedCarbAbsorptionRate = 20; // g/h; maximum rate to assume carbs will absorb if no CI observed
    var remainingCATime = remainingCATimeMin; // added by mike https://github.com/openaps/oref0/issues/884
    if (meal_data.carbs) {
        // if carbs * assumedCarbAbsorptionRate > remainingCATimeMin, raise it
        // so <= 90g is assumed to take 3h, and 120g=4h
        remainingCATimeMin = Math.max(remainingCATimeMin, meal_data.mealCOB/assumedCarbAbsorptionRate);
        var lastCarbAge = round(( new Date().getTime() - meal_data.lastCarbTime ) / 60000);
        //console.error(meal_data.lastCarbTime, lastCarbAge);

        fractionCOBAbsorbed = ( meal_data.carbs - meal_data.mealCOB ) / meal_data.carbs;
        remainingCATime = remainingCATimeMin + 1.5 * lastCarbAge/60;
        remainingCATime = round(remainingCATime,1);
        //console.error(fractionCOBAbsorbed, remainingCATimeAdjustment, remainingCATime)
        console.error("Last carbs",lastCarbAge,"minutes ago; remainingCATime:",remainingCATime,"hours;",round(fractionCOBAbsorbed*100)+"% carbs absorbed");
    }

    // calculate the number of carbs absorbed over remainingCATime hours at current CI
    // CI (mg/dL/5m) * (5m)/5 (m) * 60 (min/hr) * 4 (h) / 2 (linear decay factor) = total carb impact (mg/dL)
    var totalCI = Math.max(0, ci / 5 * 60 * remainingCATime / 2);
    // totalCI (mg/dL) / CSF (mg/dL/g) = total carbs absorbed (g)
    var totalCA = totalCI / csf;
    var remainingCarbsCap = 90; // default to 90
    var remainingCarbsFraction = 1;
    if (profile.remainingCarbsCap) { remainingCarbsCap = Math.min(90,profile.remainingCarbsCap); }
    if (profile.remainingCarbsFraction) { remainingCarbsFraction = Math.min(1,profile.remainingCarbsFraction); }
    var remainingCarbsIgnore = 1 - remainingCarbsFraction;
    var remainingCarbs = Math.max(0, meal_data.mealCOB - totalCA - meal_data.carbs*remainingCarbsIgnore);
    remainingCarbs = Math.min(remainingCarbsCap,remainingCarbs);
    // assume remainingCarbs will absorb in a /\ shaped bilinear curve
    // peaking at remainingCATime / 2 and ending at remainingCATime hours
    // area of the /\ triangle is the same as a remainingCIpeak-height rectangle out to remainingCATime/2
    // remainingCIpeak (mg/dL/5m) = remainingCarbs (g) * CSF (mg/dL/g) * 5 (m/5m) * 1h/60m / (remainingCATime/2) (h)
    var remainingCIpeak = remainingCarbs * csf * 5 / 60 / (remainingCATime/2);
    //console.error(profile.min_5m_carbimpact,ci,totalCI,totalCA,remainingCarbs,remainingCI,remainingCATime);
    //if (meal_data.mealCOB * 3 > meal_data.carbs) { }

    // calculate peak deviation in last hour, and slope from that to current deviation
    var slopeFromMaxDeviation = round(meal_data.slopeFromMaxDeviation,2);
    // calculate lowest deviation in last hour, and slope from that to current deviation
    var slopeFromMinDeviation = round(meal_data.slopeFromMinDeviation,2);
    // assume deviations will drop back down at least at 1/3 the rate they ramped up
    var slopeFromDeviations = Math.min(slopeFromMaxDeviation,-slopeFromMinDeviation/3);
    //console.error(slopeFromMaxDeviation);

    aci = 10;
    //5m data points = g * (1U/10g) * (40mg/dL/1U) / (mg/dL/5m)
    // duration (in 5m data points) = COB (g) * CSF (mg/dL/g) / ci (mg/dL/5m)
    // limit cid to remainingCATime hours: the reset goes to remainingCI
    if (ci == 0) {
        // avoid divide by zero
        cid = 0;
    } else {
        cid = Math.min(remainingCATime*60/5/2,Math.max(0, meal_data.mealCOB * csf / ci ));
    }
    acid = Math.max(0, meal_data.mealCOB * csf / aci );
    // duration (hours) = duration (5m) * 5 / 60 * 2 (to account for linear decay)
    console.error("Carb Impact:",ci,"mg/dL per 5m; CI Duration:",round(cid*5/60*2,1),"hours; remaining CI (~2h peak):",round(remainingCIpeak,1),"mg/dL per 5m");
    //console.error("Accel. Carb Impact:",aci,"mg/dL per 5m; ACI Duration:",round(acid*5/60*2,1),"hours");
    var minIOBPredBG = 999;
    var minCOBPredBG = 999;
    var minUAMPredBG = 999;
    var minGuardBG = bg;
    var minCOBGuardBG = 999;
    var minUAMGuardBG = 999;
    var minIOBGuardBG = 999;
    var minZTGuardBG = 999;
    var minPredBG;
    var avgPredBG;
    var IOBpredBG = eventualBG;
    var maxIOBPredBG = bg;
    var maxCOBPredBG = bg;
    var maxUAMPredBG = bg;
    //var maxPredBG = bg;
    var eventualPredBG = bg;
    var lastIOBpredBG;
    var lastCOBpredBG;
    var lastUAMpredBG;
    var lastZTpredBG;
    var UAMduration = 0;
    var remainingCItotal = 0;
    var remainingCIs = [];
    var predCIs = [];
    try {
        iobArray.forEach(function(iobTick) {
            //console.error(iobTick);
            predBGI = round(( -iobTick.activity * sens * 5 ), 2);
            predZTBGI = round(( -iobTick.iobWithZeroTemp.activity * sens * 5 ), 2);
            // for IOBpredBGs, predicted deviation impact drops linearly from current deviation down to zero
            // over 60 minutes (data points every 5m)
            predDev = ci * ( 1 - Math.min(1,IOBpredBGs.length/(60/5)) );
            IOBpredBG = IOBpredBGs[IOBpredBGs.length-1] + predBGI + predDev;
            // calculate predBGs with long zero temp without deviations
            ZTpredBG = ZTpredBGs[ZTpredBGs.length-1] + predZTBGI;
            // for COBpredBGs, predicted carb impact drops linearly from current carb impact down to zero
            // eventually accounting for all carbs (if they can be absorbed over DIA)
            predCI = Math.max(0, Math.max(0,ci) * ( 1 - COBpredBGs.length/Math.max(cid*2,1) ) );
            predACI = Math.max(0, Math.max(0,aci) * ( 1 - COBpredBGs.length/Math.max(acid*2,1) ) );
            // if any carbs aren't absorbed after remainingCATime hours, assume they'll absorb in a /\ shaped
            // bilinear curve peaking at remainingCIpeak at remainingCATime/2 hours (remainingCATime/2*12 * 5m)
            // and ending at remainingCATime h (remainingCATime*12 * 5m intervals)
            var intervals = Math.min( COBpredBGs.length, (remainingCATime*12)-COBpredBGs.length );
            var remainingCI = Math.max(0, intervals / (remainingCATime/2*12) * remainingCIpeak );
            remainingCItotal += predCI+remainingCI;
            remainingCIs.push(round(remainingCI,0));
            predCIs.push(round(predCI,0));
            //console.error(round(predCI,1)+"+"+round(remainingCI,1)+" ");
            COBpredBG = COBpredBGs[COBpredBGs.length-1] + predBGI + Math.min(0,predDev) + predCI + remainingCI;
            aCOBpredBG = aCOBpredBGs[aCOBpredBGs.length-1] + predBGI + Math.min(0,predDev) + predACI;
            // for UAMpredBGs, predicted carb impact drops at slopeFromDeviations
            // calculate predicted CI from UAM based on slopeFromDeviations
            predUCIslope = Math.max(0, uci + ( UAMpredBGs.length*slopeFromDeviations ) );
            // if slopeFromDeviations is too flat, predicted deviation impact drops linearly from
            // current deviation down to zero over 3h (data points every 5m)
            predUCImax = Math.max(0, uci * ( 1 - UAMpredBGs.length/Math.max(3*60/5,1) ) );
            //console.error(predUCIslope, predUCImax);
            // predicted CI from UAM is the lesser of CI based on deviationSlope or DIA
            predUCI = Math.min(predUCIslope, predUCImax);
            if(predUCI>0) {
                //console.error(UAMpredBGs.length,slopeFromDeviations, predUCI);
                UAMduration=round((UAMpredBGs.length+1)*5/60,1);
            }
            UAMpredBG = UAMpredBGs[UAMpredBGs.length-1] + predBGI + Math.min(0, predDev) + predUCI;
            //console.error(predBGI, predCI, predUCI);
            // truncate all BG predictions at 4 hours
            if ( IOBpredBGs.length < 48) { IOBpredBGs.push(IOBpredBG); }
            if ( COBpredBGs.length < 48) { COBpredBGs.push(COBpredBG); }
            if ( aCOBpredBGs.length < 48) { aCOBpredBGs.push(aCOBpredBG); }
            if ( UAMpredBGs.length < 48) { UAMpredBGs.push(UAMpredBG); }
            if ( ZTpredBGs.length < 48) { ZTpredBGs.push(ZTpredBG); }
            // calculate minGuardBGs without a wait from COB, UAM, IOB predBGs
            if ( COBpredBG < minCOBGuardBG ) { minCOBGuardBG = round(COBpredBG); }
            if ( UAMpredBG < minUAMGuardBG ) { minUAMGuardBG = round(UAMpredBG); }
            if ( IOBpredBG < minIOBGuardBG ) { minIOBGuardBG = round(IOBpredBG); }
            if ( ZTpredBG < minZTGuardBG ) { minZTGuardBG = round(ZTpredBG); }

            // set minPredBGs starting when currently-dosed insulin activity will peak
            // look ahead 60m (regardless of insulin type) so as to be less aggressive on slower insulins
            var insulinPeakTime = 60;
            // add 30m to allow for insluin delivery (SMBs or temps)
            insulinPeakTime = 90;
            var insulinPeak5m = (insulinPeakTime/60)*12;
            //console.error(insulinPeakTime, insulinPeak5m, profile.insulinPeakTime, profile.curve);

            // wait 90m before setting minIOBPredBG
            if ( IOBpredBGs.length > insulinPeak5m && (IOBpredBG < minIOBPredBG) ) { minIOBPredBG = round(IOBpredBG); }
            if ( IOBpredBG > maxIOBPredBG ) { maxIOBPredBG = IOBpredBG; }
            // wait 85-105m before setting COB and 60m for UAM minPredBGs
            if ( (cid || remainingCIpeak > 0) && COBpredBGs.length > insulinPeak5m && (COBpredBG < minCOBPredBG) ) { minCOBPredBG = round(COBpredBG); }
            if ( (cid || remainingCIpeak > 0) && COBpredBG > maxIOBPredBG ) { maxCOBPredBG = COBpredBG; }
            if ( enableUAM && UAMpredBGs.length > 12 && (UAMpredBG < minUAMPredBG) ) { minUAMPredBG = round(UAMpredBG); }
            if ( enableUAM && UAMpredBG > maxIOBPredBG ) { maxUAMPredBG = UAMpredBG; }
        });
        // set eventualBG to include effect of carbs
        //console.error("PredBGs:",JSON.stringify(predBGs));
    } catch (e) {
        console.error("Problem with iobArray.  Optional feature Advanced Meal Assist disabled:",e);
    }
    if (meal_data.mealCOB) {
        console.error("predCIs (mg/dL/5m):",predCIs.join(" "));
        console.error("remainingCIs:      ",remainingCIs.join(" "));
    }
    //,"totalCA:",round(totalCA,2),"remainingCItotal/csf+totalCA:",round(remainingCItotal/csf+totalCA,2));
    rT.predBGs = {};
    IOBpredBGs.forEach(function(p, i, theArray) {
        theArray[i] = round(Math.min(401,Math.max(39,p)));
    });
    for (var i=IOBpredBGs.length-1; i > 12; i--) {
        if (IOBpredBGs[i-1] != IOBpredBGs[i]) { break; }
        else { IOBpredBGs.pop(); }
    }
    rT.predBGs.IOB = IOBpredBGs;
    lastIOBpredBG=round(IOBpredBGs[IOBpredBGs.length-1]);
    ZTpredBGs.forEach(function(p, i, theArray) {
        theArray[i] = round(Math.min(401,Math.max(39,p)));
    });
    for (var i=ZTpredBGs.length-1; i > 6; i--) {
        //if (ZTpredBGs[i-1] != ZTpredBGs[i]) { break; }
        // stop displaying ZTpredBGs once they're rising and above target
        if (ZTpredBGs[i-1] >= ZTpredBGs[i] || ZTpredBGs[i] < target_bg) { break; }
        else { ZTpredBGs.pop(); }
    }
    rT.predBGs.ZT = ZTpredBGs;
    lastZTpredBG=round(ZTpredBGs[ZTpredBGs.length-1]);
    if (meal_data.mealCOB > 0) {
        aCOBpredBGs.forEach(function(p, i, theArray) {
            theArray[i] = round(Math.min(401,Math.max(39,p)));
        });
        for (var i=aCOBpredBGs.length-1; i > 12; i--) {
            if (aCOBpredBGs[i-1] != aCOBpredBGs[i]) { break; }
            else { aCOBpredBGs.pop(); }
        }
        // disable for now.  may want to add a preference to re-enable
        //rT.predBGs.aCOB = aCOBpredBGs;
    }
    if (meal_data.mealCOB > 0 && ( ci > 0 || remainingCIpeak > 0 )) {
        COBpredBGs.forEach(function(p, i, theArray) {
            theArray[i] = round(Math.min(401,Math.max(39,p)));
        });
        for (var i=COBpredBGs.length-1; i > 12; i--) {
            if (COBpredBGs[i-1] != COBpredBGs[i]) { break; }
            else { COBpredBGs.pop(); }
        }
        rT.predBGs.COB = COBpredBGs;
        lastCOBpredBG=round(COBpredBGs[COBpredBGs.length-1]);
        eventualBG = Math.max(eventualBG, round(COBpredBGs[COBpredBGs.length-1]) );
    }
    if (ci > 0 || remainingCIpeak > 0) {
        if (enableUAM) {
            UAMpredBGs.forEach(function(p, i, theArray) {
                theArray[i] = round(Math.min(401,Math.max(39,p)));
            });
            for (var i=UAMpredBGs.length-1; i > 12; i--) {
                if (UAMpredBGs[i-1] != UAMpredBGs[i]) { break; }
                else { UAMpredBGs.pop(); }
            }
            rT.predBGs.UAM = UAMpredBGs;
            lastUAMpredBG=round(UAMpredBGs[UAMpredBGs.length-1]);
            if (UAMpredBGs[UAMpredBGs.length-1]) {
                eventualBG = Math.max(eventualBG, round(UAMpredBGs[UAMpredBGs.length-1]) );
            }
        }

        // set eventualBG and snoozeBG based on COB or UAM predBGs
        rT.eventualBG = eventualBG;
    }

    console.error("UAM Impact:",uci,"mg/dL per 5m; UAM Duration:",UAMduration,"hours");


    minIOBPredBG = Math.max(39,minIOBPredBG);
    minCOBPredBG = Math.max(39,minCOBPredBG);
    minUAMPredBG = Math.max(39,minUAMPredBG);
    minPredBG = round(minIOBPredBG);

    var fractionCarbsLeft = meal_data.mealCOB/meal_data.carbs;
    // if we have COB and UAM is enabled, average both
    if ( minUAMPredBG < 999 && minCOBPredBG < 999 ) {
        // weight COBpredBG vs. UAMpredBG based on how many carbs remain as COB
        avgPredBG = round( (1-fractionCarbsLeft)*UAMpredBG + fractionCarbsLeft*COBpredBG );
    // if UAM is disabled, average IOB and COB
    } else if ( minCOBPredBG < 999 ) {
        avgPredBG = round( (IOBpredBG + COBpredBG)/2 );
    // if we have UAM but no COB, average IOB and UAM
    } else if ( minUAMPredBG < 999 ) {
        avgPredBG = round( (IOBpredBG + UAMpredBG)/2 );
    } else {
        avgPredBG = round( IOBpredBG );
    }
    // if avgPredBG is below minZTGuardBG, bring it up to that level
    if ( minZTGuardBG > avgPredBG ) {
        avgPredBG = minZTGuardBG;
    }

    // if we have both minCOBGuardBG and minUAMGuardBG, blend according to fractionCarbsLeft
    if ( (cid || remainingCIpeak > 0) ) {
        if ( enableUAM ) {
            minGuardBG = fractionCarbsLeft*minCOBGuardBG + (1-fractionCarbsLeft)*minUAMGuardBG;
        } else {
            minGuardBG = minCOBGuardBG;
        }
    } else if ( enableUAM ) {
        minGuardBG = minUAMGuardBG;
    } else {
        minGuardBG = minIOBGuardBG;
    }
    minGuardBG = round(minGuardBG);
    //console.error(minCOBGuardBG, minUAMGuardBG, minIOBGuardBG, minGuardBG);

    var minZTUAMPredBG = minUAMPredBG;
    // if minZTGuardBG is below threshold, bring down any super-high minUAMPredBG by averaging
    // this helps prevent UAM from giving too much insulin in case absorption falls off suddenly
    if ( minZTGuardBG < threshold ) {
        minZTUAMPredBG = (minUAMPredBG + minZTGuardBG) / 2;
    // if minZTGuardBG is between threshold and target, blend in the averaging
    } else if ( minZTGuardBG < target_bg ) {
        // target 100, threshold 70, minZTGuardBG 85 gives 50%: (85-70) / (100-70)
        var blendPct = (minZTGuardBG-threshold) / (target_bg-threshold);
        var blendedMinZTGuardBG = minUAMPredBG*blendPct + minZTGuardBG*(1-blendPct);
        minZTUAMPredBG = (minUAMPredBG + blendedMinZTGuardBG) / 2;
        //minZTUAMPredBG = minUAMPredBG - target_bg + minZTGuardBG;
    // if minUAMPredBG is below minZTGuardBG, bring minUAMPredBG up by averaging
    // this allows more insulin if lastUAMPredBG is below target, but minZTGuardBG is still high
    } else if ( minZTGuardBG > minUAMPredBG ) {
        minZTUAMPredBG = (minUAMPredBG + minZTGuardBG) / 2;
    }
    minZTUAMPredBG = round(minZTUAMPredBG);
    //console.error("minUAMPredBG:",minUAMPredBG,"minZTGuardBG:",minZTGuardBG,"minZTUAMPredBG:",minZTUAMPredBG);
    // if any carbs have been entered recently
    if (meal_data.carbs) {
        // average the minIOBPredBG and minUAMPredBG if available
        /*
        if ( minUAMPredBG < 999 ) {
            avgMinPredBG = round( (minIOBPredBG+minUAMPredBG)/2 );
        } else {
            avgMinPredBG = minIOBPredBG;
        }
        */

        // if UAM is disabled, use max of minIOBPredBG, minCOBPredBG
        if ( ! enableUAM && minCOBPredBG < 999 ) {
            minPredBG = round(Math.max(minIOBPredBG, minCOBPredBG));
        // if we have COB, use minCOBPredBG, or blendedMinPredBG if it's higher
        } else if ( minCOBPredBG < 999 ) {
            // calculate blendedMinPredBG based on how many carbs remain as COB
            //blendedMinPredBG = fractionCarbsLeft*minCOBPredBG + (1-fractionCarbsLeft)*minUAMPredBG;
            blendedMinPredBG = fractionCarbsLeft*minCOBPredBG + (1-fractionCarbsLeft)*minZTUAMPredBG;
            // if blendedMinPredBG > minCOBPredBG, use that instead
            minPredBG = round(Math.max(minIOBPredBG, minCOBPredBG, blendedMinPredBG));
        // if carbs have been entered, but have expired, use minUAMPredBG
        } else {
            //minPredBG = minUAMPredBG;
            minPredBG = minZTUAMPredBG;
        }
    // in pure UAM mode, use the higher of minIOBPredBG,minUAMPredBG
    } else if ( enableUAM ) {
        //minPredBG = round(Math.max(minIOBPredBG,minUAMPredBG));
        minPredBG = round(Math.max(minIOBPredBG,minZTUAMPredBG));
    }

    // make sure minPredBG isn't higher than avgPredBG
    minPredBG = Math.min( minPredBG, avgPredBG );

    console.error("minPredBG: "+minPredBG+" minIOBPredBG: "+minIOBPredBG+" minZTGuardBG: "+minZTGuardBG);
    if (minCOBPredBG < 999) {
        console.error(" minCOBPredBG: "+minCOBPredBG);
    }
    if (minUAMPredBG < 999) {
        console.error(" minUAMPredBG: "+minUAMPredBG);
    }
    console.error(" avgPredBG:",avgPredBG,"COB:",meal_data.mealCOB,"/",meal_data.carbs);
    // But if the COB line falls off a cliff, don't trust UAM too much:
    // use maxCOBPredBG if it's been set and lower than minPredBG
    if ( maxCOBPredBG > bg ) {
        minPredBG = Math.min(minPredBG, maxCOBPredBG);
    }

    rT.COB=meal_data.mealCOB;
    rT.IOB=iob_data.iob;
    rT.reason="COB: " + meal_data.mealCOB + ", Dev: " + convert_bg(deviation, profile) + ", BGI: " + convert_bg(bgi, profile) + ", ISF: " + convert_bg(sens, profile) + ", CR: " + round(profile.carb_ratio, 2) + ", Target: " + convert_bg(target_bg, profile) + ", minPredBG " + convert_bg(minPredBG, profile) + ", minGuardBG " + convert_bg(minGuardBG, profile) + ", IOBpredBG " + convert_bg(lastIOBpredBG, profile);
    if (lastCOBpredBG > 0) {
        rT.reason += ", COBpredBG " + convert_bg(lastCOBpredBG, profile);
    }
    if (lastUAMpredBG > 0) {
        rT.reason += ", UAMpredBG " + convert_bg(lastUAMpredBG, profile)
    }
    rT.reason += "; ";
    //var bgUndershoot = threshold - Math.min(minGuardBG, Math.max( naive_eventualBG, eventualBG ));
    // use naive_eventualBG if above 40, but switch to minGuardBG if both eventualBGs hit floor of 39
    //var carbsReqBG = Math.max( naive_eventualBG, eventualBG );
    var carbsReqBG = naive_eventualBG;
    if ( carbsReqBG < 40 ) {
        carbsReqBG = Math.min( minGuardBG, carbsReqBG );
    }
    var bgUndershoot = threshold - carbsReqBG;
    // calculate how long until COB (or IOB) predBGs drop below min_bg
    var minutesAboveMinBG = 240;
    var minutesAboveThreshold = 240;
    if (meal_data.mealCOB > 0 && ( ci > 0 || remainingCIpeak > 0 )) {
        for (var i=0; i<COBpredBGs.length; i++) {
            //console.error(COBpredBGs[i], min_bg);
            if ( COBpredBGs[i] < min_bg ) {
                minutesAboveMinBG = 5*i;
                break;
            }
        }
        for (var i=0; i<COBpredBGs.length; i++) {
            //console.error(COBpredBGs[i], threshold);
            if ( COBpredBGs[i] < threshold ) {
                minutesAboveThreshold = 5*i;
                break;
            }
        }
    } else {
        for (var i=0; i<IOBpredBGs.length; i++) {
            //console.error(IOBpredBGs[i], min_bg);
            if ( IOBpredBGs[i] < min_bg ) {
                minutesAboveMinBG = 5*i;
                break;
            }
        }
        for (var i=0; i<IOBpredBGs.length; i++) {
            //console.error(IOBpredBGs[i], threshold);
            if ( IOBpredBGs[i] < threshold ) {
                minutesAboveThreshold = 5*i;
                break;
            }
        }
    }

    if (enableSMB && minGuardBG < threshold) {
        console.error("minGuardBG",convert_bg(minGuardBG, profile),"projected below", convert_bg(threshold, profile) ,"- disabling SMB");
        //rT.reason += "minGuardBG "+minGuardBG+"<"+threshold+": SMB disabled; ";
        enableSMB = false;
    }
    if ( maxDelta > 0.20 * bg ) {
        console.error("maxDelta",convert_bg(maxDelta, profile),"> 20% of BG",convert_bg(bg, profile),"- disabling SMB");
        rT.reason += "maxDelta "+convert_bg(maxDelta, profile)+" > 20% of BG "+convert_bg(bg, profile)+": SMB disabled; ";
        enableSMB = false;
    }

    console.error("BG projected to remain above",convert_bg(min_bg, profile),"for",minutesAboveMinBG,"minutes");
    if ( minutesAboveThreshold < 240 || minutesAboveMinBG < 60 ) {
        console.error("BG projected to remain above",convert_bg(threshold,profile),"for",minutesAboveThreshold,"minutes");
    }
    // include at least minutesAboveMinBG worth of zero temps in calculating carbsReq
    // always include at least 30m worth of zero temp (carbs to 80, low temp up to target)
    //var zeroTempDuration = Math.max(30,minutesAboveMinBG);
    var zeroTempDuration = minutesAboveThreshold;
    // BG undershoot, minus effect of zero temps until hitting min_bg, converted to grams, minus COB
    var zeroTempEffect = profile.current_basal*sens*zeroTempDuration/60;
    // don't count the last 25% of COB against carbsReq
    var COBforCarbsReq = Math.max(0, meal_data.mealCOB - 0.25*meal_data.carbs);
    var carbsReq = (bgUndershoot - zeroTempEffect) / csf - COBforCarbsReq;
    zeroTempEffect = round(zeroTempEffect);
    carbsReq = round(carbsReq);
    console.error("naive_eventualBG:",naive_eventualBG,"bgUndershoot:",bgUndershoot,"zeroTempDuration:",zeroTempDuration,"zeroTempEffect:",zeroTempEffect,"carbsReq:",carbsReq);
    if ( carbsReq >= profile.carbsReqThreshold && minutesAboveThreshold <= 45 ) {
        rT.carbsReq = carbsReq;
        rT.reason += carbsReq + " add'l carbs req w/in " + minutesAboveThreshold + "m; ";
    }
    // don't low glucose suspend if IOB is already super negative and BG is rising faster than predicted
    if (bg < threshold && iob_data.iob < -profile.current_basal*20/60 && minDelta > 0 && minDelta > expectedDelta) {
        rT.reason += "IOB "+iob_data.iob+" < " + round(-profile.current_basal*20/60,2);
        rT.reason += " and minDelta " + convert_bg(minDelta, profile) + " > " + "expectedDelta " + convert_bg(expectedDelta, profile) + "; ";
    // predictive low glucose suspend mode: BG is / is projected to be < threshold
    } else if ( bg < threshold || minGuardBG < threshold ) {
        rT.reason += "minGuardBG " + convert_bg(minGuardBG, profile) + "<" + convert_bg(threshold, profile);
        var bgUndershoot = target_bg - minGuardBG;
        var worstCaseInsulinReq = bgUndershoot / sens;
        var durationReq = round(60*worstCaseInsulinReq / profile.current_basal);
        durationReq = round(durationReq/30)*30;
        // always set a 30-120m zero temp (oref0-pump-loop will let any longer SMB zero temp run)
        durationReq = Math.min(120,Math.max(30,durationReq));
        return tempBasalFunctions.setTempBasal(0, durationReq, profile, rT, currenttemp);
    }

    if (eventualBG < min_bg) { // if eventual BG is below target:
        rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " < " + convert_bg(min_bg, profile);
        // if 5m or 30m avg BG is rising faster than expected delta
        if ( minDelta > expectedDelta && minDelta > 0 && !carbsReq ) {
            // if naive_eventualBG < 40, set a 30m zero temp (oref0-pump-loop will let any longer SMB zero temp run)
            if (naive_eventualBG < 40) {
                rT.reason += ", naive_eventualBG < 40. ";
                return tempBasalFunctions.setTempBasal(0, 30, profile, rT, currenttemp);
            }
            if (glucose_status.delta > minDelta) {
                rT.reason += ", but Delta " + convert_bg(tick, profile) + " > expectedDelta " + convert_bg(expectedDelta, profile);
            } else {
                rT.reason += ", but Min. Delta " + minDelta.toFixed(2) + " > Exp. Delta " + convert_bg(expectedDelta, profile);
            }
            if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
                rT.reason += ", temp " + currenttemp.rate + " ~ req " + basal + "U/hr. ";
                return rT;
            } else {
                rT.reason += "; setting current basal of " + basal + " as temp. ";
                return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
            }
        }

        // calculate 30m low-temp required to get projected BG up to target
        // use snoozeBG to more gradually ramp in any counteraction of the user's boluses
        // multiply by 2 to low-temp faster for increased hypo safety
        //var insulinReq = 2 * Math.min(0, (snoozeBG - target_bg) / sens);
        var insulinReq = 2 * Math.min(0, (eventualBG - target_bg) / sens);
        insulinReq = round( insulinReq , 2);
        // calculate naiveInsulinReq based on naive_eventualBG
        var naiveInsulinReq = Math.min(0, (naive_eventualBG - target_bg) / sens);
        naiveInsulinReq = round( naiveInsulinReq , 2);
        if (minDelta < 0 && minDelta > expectedDelta) {
            // if we're barely falling, newinsulinReq should be barely negative
            //rT.reason += ", Snooze BG " + convert_bg(snoozeBG, profile);
            var newinsulinReq = round(( insulinReq * (minDelta / expectedDelta) ), 2);
            //console.error("Increasing insulinReq from " + insulinReq + " to " + newinsulinReq);
            insulinReq = newinsulinReq;
        }
        // rate required to deliver insulinReq less insulin over 30m:
        var rate = basal + (2 * insulinReq);
        rate = round_basal(rate, profile);
        // if required temp < existing temp basal
        var insulinScheduled = currenttemp.duration * (currenttemp.rate - basal) / 60;
        // if current temp would deliver a lot (30% of basal) less than the required insulin,
        // by both normal and naive calculations, then raise the rate
        var minInsulinReq = Math.min(insulinReq,naiveInsulinReq);
        if (insulinScheduled < minInsulinReq - basal*0.3) {
            rT.reason += ", "+currenttemp.duration + "m@" + (currenttemp.rate).toFixed(2) + " is a lot less than needed. ";
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }
        if (typeof currenttemp.rate !== 'undefined' && (currenttemp.duration > 5 && rate >= currenttemp.rate * 0.8)) {
            rT.reason += ", temp " + currenttemp.rate + " ~< req " + rate + "U/hr. ";
            return rT;
        } else {
            // calculate a long enough zero temp to eventually correct back up to target
            if ( rate <=0 ) {
                var bgUndershoot = target_bg - naive_eventualBG;
                var worstCaseInsulinReq = bgUndershoot / sens;
                var durationReq = round(60*worstCaseInsulinReq / profile.current_basal);
                if (durationReq < 0) {
                    durationReq = 0;
                // don't set an SMB zero temp longer than 60 minutess
                } else {
                    durationReq = round(durationReq/30)*30;
                    durationReq = Math.min(60,Math.max(0,durationReq));
                }
                //console.error(durationReq);
                //rT.reason += "insulinReq " + insulinReq + "; "
                if (durationReq > 0) {
                    rT.reason += ", setting " + durationReq + "m zero temp. ";
                    return tempBasalFunctions.setTempBasal(rate, durationReq, profile, rT, currenttemp);
                }
            } else {
                rT.reason += ", setting " + rate + "U/hr. ";
            }
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }
    }

    // if eventual BG is above min but BG is falling faster than expected Delta
    if (minDelta < expectedDelta) {
        // if in SMB mode, don't cancel SMB zero temp
        if (! (microBolusAllowed && enableSMB)) {
            if (glucose_status.delta < minDelta) {
                rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " > " + convert_bg(min_bg, profile) + " but Delta " + convert_bg(tick, profile) + " < Exp. Delta " + convert_bg(expectedDelta, profile);
            } else {
                rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " > " + convert_bg(min_bg, profile) + " but Min. Delta " + minDelta.toFixed(2) + " < Exp. Delta " + convert_bg(expectedDelta, profile);
            }
            if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
                rT.reason += ", temp " + currenttemp.rate + " ~ req " + basal + "U/hr. ";
                return rT;
            } else {
                rT.reason += "; setting current basal of " + basal + " as temp. ";
                return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
            }
        }
    }
    // eventualBG or minPredBG is below max_bg
    if (Math.min(eventualBG,minPredBG) < max_bg) {
        // if in SMB mode, don't cancel SMB zero temp
        if (! (microBolusAllowed && enableSMB )) {
            rT.reason += convert_bg(eventualBG, profile)+"-"+convert_bg(minPredBG, profile)+" in range: no temp required";
            if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
                rT.reason += ", temp " + currenttemp.rate + " ~ req " + basal + "U/hr. ";
                return rT;
            } else {
                rT.reason += "; setting current basal of " + basal + " as temp. ";
                return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
            }
        }
    }

    // eventual BG is at/above target
    // if iob is over max, just cancel any temps
    // if we're not here because of SMB, eventual BG is at/above target
    if (! (microBolusAllowed && rT.COB)) {
        rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " >= " +  convert_bg(max_bg, profile) + ", ";
    }
    if (iob_data.iob > max_iob) {
        rT.reason += "IOB " + round(iob_data.iob,2) + " > max_iob " + max_iob;
        if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
            rT.reason += ", temp " + currenttemp.rate + " ~ req " + basal + "U/hr. ";
            return rT;
        } else {
            rT.reason += "; setting current basal of " + basal + " as temp. ";
            return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        }
    } else { // otherwise, calculate 30m high-temp required to get projected BG down to target

        // insulinReq is the additional insulin required to get minPredBG down to target_bg
        //console.error(minPredBG,eventualBG);
        //var insulinReq = round( (Math.min(minPredBG,eventualBG) - target_bg) / sens, 2);
        var insulinReq = round( (Math.min(minPredBG,eventualBG) - target_bg) / sens, 2);
        // when dropping, but not as fast as expected, reduce insulinReq proportionally
        // to the what fraction of expectedDelta we're dropping at
        //if (minDelta < 0 && minDelta > expectedDelta) {
            //var newinsulinReq = round(( insulinReq * (1 - (minDelta / expectedDelta)) ), 2);
            //console.error("Reducing insulinReq from " + insulinReq + " to " + newinsulinReq + " for minDelta " + minDelta + " vs. expectedDelta " + expectedDelta);
            //insulinReq = newinsulinReq;
        //}
        // if that would put us over max_iob, then reduce accordingly
        if (insulinReq > max_iob-iob_data.iob) {
            rT.reason += "max_iob " + max_iob + ", ";
            insulinReq = max_iob-iob_data.iob;
        }

        // rate required to deliver insulinReq more insulin over 30m:
        var rate = basal + (2 * insulinReq);
        rate = round_basal(rate, profile);
        insulinReq = round(insulinReq,3);
        rT.insulinReq = insulinReq;
        //console.error(iob_data.lastBolusTime);
        // minutes since last bolus
        var lastBolusAge = round(( new Date().getTime() - iob_data.lastBolusTime ) / 60000,1);
        //console.error(lastBolusAge);
        //console.error(profile.temptargetSet, target_bg, rT.COB);
        // only allow microboluses with COB or low temp targets, or within DIA hours of a bolus
        if (microBolusAllowed && enableSMB && bg > threshold) {
            // never bolus more than maxSMBBasalMinutes worth of basal
            mealInsulinReq = round( meal_data.mealCOB / profile.carb_ratio ,3);
            if (typeof profile.maxSMBBasalMinutes == 'undefined' ) {
                maxBolus = round( profile.current_basal * 30 / 60 ,1);
                console.error("profile.maxSMBBasalMinutes undefined: defaulting to 30m");
            // if IOB covers more than COB, limit maxBolus to 30m of basal
            } else if ( iob_data.iob > mealInsulinReq && iob_data.iob > 0 ) {
                console.error("IOB",iob_data.iob,"> COB",meal_data.mealCOB+"; mealInsulinReq =",mealInsulinReq);
                maxBolus = round( profile.current_basal * 30 / 60 ,1);
            } else {
                console.error("profile.maxSMBBasalMinutes:",profile.maxSMBBasalMinutes,"profile.current_basal:",profile.current_basal);
                maxBolus = round( profile.current_basal * profile.maxSMBBasalMinutes / 60 ,1);
            }
            // bolus 1/2 the insulinReq, up to maxBolus, rounding down to nearest 0.1U
            microBolus = Math.floor(Math.min(insulinReq/2,maxBolus)*10)/10;
            // calculate a long enough zero temp to eventually correct back up to target
            var smbTarget = target_bg;
            var worstCaseInsulinReq = (smbTarget - (naive_eventualBG + minIOBPredBG)/2 ) / sens;
            var durationReq = round(60*worstCaseInsulinReq / profile.current_basal);

            // if insulinReq > 0 but not enough for a microBolus, don't set an SMB zero temp
            if (insulinReq > 0 && microBolus < 0.1) {
                durationReq = 0;
            }

            var smbLowTempReq = 0;
            if (durationReq <= 0) {
                durationReq = 0;
            // don't set a temp longer than 120 minutes
            } else if (durationReq >= 30) {
                durationReq = round(durationReq/30)*30;
                durationReq = Math.min(120,Math.max(0,durationReq));
            } else {
                // if SMB durationReq is less than 30m, set a nonzero low temp
                smbLowTempReq = round( basal * durationReq/30 ,2);
                durationReq = 30;
            }
            rT.reason += " insulinReq " + insulinReq;
            if (microBolus >= maxBolus) {
                rT.reason +=  "; maxBolus " + maxBolus;
            }
            if (durationReq > 0) {
                rT.reason += "; setting " + durationReq + "m low temp of " + smbLowTempReq + "U/h";
            }
            rT.reason += ". ";

            //allow SMBs every 3 minutes
            var nextBolusMins = round(3-lastBolusAge,1);
            //console.error(naive_eventualBG, insulinReq, worstCaseInsulinReq, durationReq);
            console.error("naive_eventualBG",naive_eventualBG+",",durationReq+"m "+smbLowTempReq+"U/h temp needed; last bolus",lastBolusAge+"m ago; maxBolus: "+maxBolus);
            if (lastBolusAge > 3) {
                if (microBolus > 0) {
                    rT.units = microBolus;
                    rT.reason += "Microbolusing " + microBolus + "U. ";
                }
            } else {
                rT.reason += "Waiting " + nextBolusMins + "m to microbolus again. ";
            }
            //rT.reason += ". ";

            // if no zero temp is required, don't return yet; allow later code to set a high temp
            if (durationReq > 0) {
                rT.rate = smbLowTempReq;
                rT.duration = durationReq;
                return rT;
            }

            // if insulinReq is negative, snoozeBG > target_bg, and lastCOBpredBG > target_bg, set a neutral temp
            //if (insulinReq < 0 && snoozeBG > target_bg && lastCOBpredBG > target_bg) {
                //rT.reason += "; SMB bolus snooze: setting current basal of " + basal + " as temp. ";
                //return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
            //}
        }

        var maxSafeBasal = tempBasalFunctions.getMaxSafeBasal(profile);

        if (rate > maxSafeBasal) {
            rT.reason += "adj. req. rate: "+rate+" to maxSafeBasal: "+maxSafeBasal+", ";
            rate = round_basal(maxSafeBasal, profile);
        }

        var insulinScheduled = currenttemp.duration * (currenttemp.rate - basal) / 60;
        if (insulinScheduled >= insulinReq * 2) { // if current temp would deliver >2x more than the required insulin, lower the rate
            rT.reason += currenttemp.duration + "m@" + (currenttemp.rate).toFixed(2) + " > 2 * insulinReq. Setting temp basal of " + rate + "U/hr. ";
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }

        if (typeof currenttemp.duration == 'undefined' || currenttemp.duration == 0) { // no temp is set
            rT.reason += "no temp, setting " + rate + "U/hr. ";
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }

        if (currenttemp.duration > 5 && (round_basal(rate, profile) <= round_basal(currenttemp.rate, profile))) { // if required temp <~ existing temp basal
            rT.reason += "temp " + currenttemp.rate + " >~ req " + rate + "U/hr. ";
            return rT;
        }

        // required temp > existing temp basal
        rT.reason += "temp " + currenttemp.rate + "<" + rate + "U/hr. ";
        return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
    }

};

module.exports = determine_basal;

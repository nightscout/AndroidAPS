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
    var scale = Math.pow(10, digits);
    return Math.round(value * scale) / scale;
}

// we expect BG to rise or fall at the rate of BGI,
// adjusted by the rate at which BG would need to rise /
// fall to get eventualBG to target over DIA/2 hours
function calculate_expected_delta(dia, target_bg, eventual_bg, bgi) {
    // (hours * mins_per_hour) / 5 = how many 5 minute periods in dia/2
    var dia_in_5min_blocks = (dia/2 * 60) / 5;
    var target_delta = target_bg - eventual_bg;
    var expectedDelta = round(bgi + (target_delta / dia_in_5min_blocks), 1);
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
        return value.toFixed(0);
    }
}

var determine_basal = function determine_basal(glucose_status, currenttemp, iob_data, profile, autosens_data, meal_data, tempBasalFunctions) {
    var rT = { //short for requestedTemp
    };

    if (typeof profile === 'undefined' || typeof profile.current_basal === 'undefined') {
        rT.error ='Error: could not get current basal rate';
        return rT;
    }
    var basal = profile.current_basal;
    if (typeof autosens_data !== 'undefined' ) {
        basal = profile.current_basal * autosens_data.ratio;
        basal = round_basal(basal, profile);
        if (basal != profile.current_basal) {
            console.error("Adjusting basal from "+profile.current_basal+" to "+basal);
        }
    }

    var bg = glucose_status.glucose;
    // TODO: figure out how to use raw isig data to estimate BG
    if (bg < 39) {  //Dexcom is in ??? mode or calibrating
        rT.reason = "CGM is calibrating or in ??? state";
        if (basal <= currenttemp.rate * 1.2) { // high temp is running
            rT.reason += "; setting current basal of " + round(basal, 2) + " as temp";
            return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        } else { //do nothing.
            rT.reason += ", temp " + currenttemp.rate + " <~ current basal " + round(basal, 2) + "U/hr";
            return rT;
        }
    }

    var max_iob = profile.max_iob; // maximum amount of non-bolus IOB OpenAPS will ever deliver

    // if target_bg is set, great. otherwise, if min and max are set, then set target to their average
    var target_bg;
    var min_bg;
    var max_bg;
    if (typeof profile.min_bg !== 'undefined') {
            min_bg = profile.min_bg;
    }
    if (typeof profile.max_bg !== 'undefined') {
            max_bg = profile.max_bg;
    }
    if (typeof profile.target_bg !== 'undefined') {
        target_bg = profile.target_bg;
    } else {
        if (typeof profile.min_bg !== 'undefined' && typeof profile.max_bg !== 'undefined') {
            target_bg = (profile.min_bg + profile.max_bg) / 2;
        } else {
            rT.error ='Error: could not determine target_bg';
            return rT;
        }
    }

    // adjust min, max, and target BG for sensitivity, such that 50% increase in ISF raises target from 100 to 120
    if (typeof autosens_data !== 'undefined' && profile.autosens_adjust_targets) {
      if (profile.temptargetSet) {
        console.error("Temp Target set, not adjusting with autosens");
      } else {
        min_bg = Math.round((min_bg - 60) / autosens_data.ratio) + 60;
        max_bg = Math.round((max_bg - 60) / autosens_data.ratio) + 60;
        new_target_bg = Math.round((target_bg - 60) / autosens_data.ratio) + 60;
        if (target_bg == new_target_bg) {
            console.error("target_bg unchanged:", new_target_bg);
        } else {
            console.error("Adjusting target_bg from", target_bg, "to", new_target_bg);
        }
        target_bg = new_target_bg;
      }
    }

    if (typeof iob_data === 'undefined' ) {
        rT.error ='Error: iob_data undefined';
        return rT;
    }

    var iobArray = iob_data;
    if (typeof(iob_data.length) && iob_data.length > 1) {
        iob_data = iobArray[0];
        //console.error(JSON.stringify(iob_data[0]));
    }

    if (typeof iob_data.activity === 'undefined' || typeof iob_data.iob === 'undefined' ) {
        rT.error ='Error: iob_data missing some property';
        return rT;
    }

    var tick;

    if (glucose_status.delta > -0.5) {
        tick = "+" + round(glucose_status.delta,0);
    } else {
        tick = round(glucose_status.delta,0);
    }
    var minDelta = Math.min(glucose_status.delta, glucose_status.short_avgdelta, glucose_status.long_avgdelta);
    var minAvgDelta = Math.min(glucose_status.short_avgdelta, glucose_status.long_avgdelta);

    var sens = profile.sens;
    if (typeof autosens_data !== 'undefined' ) {
        sens = profile.sens / autosens_data.ratio;
        sens = round(sens, 1);
        if (sens != profile.sens) {
            console.error("Adjusting sens from "+profile.sens+" to "+sens);
        }
    }

    //calculate BG impact: the amount BG "should" be rising or falling based on insulin activity alone
    var bgi = round(( -iob_data.activity * sens * 5 ), 2);
    // project deviations for 30 minutes
    var deviation = Math.round( 30 / 5 * ( minDelta - bgi ) );
    // don't overreact to a big negative delta: use minAvgDelta if deviation is negative
    if (deviation < 0) {
        deviation = Math.round( (30 / 5) * ( minAvgDelta - bgi ) );
    }

    // calculate the naive (bolus calculator math) eventual BG based on net IOB and sensitivity
    if (iob_data.iob > 0) {
        var naive_eventualBG = Math.round( bg - (iob_data.iob * sens) );
    } else { // if IOB is negative, be more conservative and use the lower of sens, profile.sens
        var naive_eventualBG = Math.round( bg - (iob_data.iob * Math.min(sens, profile.sens) ) );
    }
    // and adjust it for the deviation above
    var eventualBG = naive_eventualBG + deviation;
    // calculate what portion of that is due to bolussnooze
    var bolusContrib = iob_data.bolussnooze * sens;
    // and add it back in to get snoozeBG, plus another 50% to avoid low-temping at mealtime
    var naive_snoozeBG = Math.round( naive_eventualBG + 1.5 * bolusContrib );
    // adjust that for deviation like we did eventualBG
    var snoozeBG = naive_snoozeBG + deviation;

    var expectedDelta = calculate_expected_delta(profile.dia, target_bg, eventualBG, bgi);
    if (typeof eventualBG === 'undefined' || isNaN(eventualBG)) {
        rT.error ='Error: could not calculate eventualBG';
        return rT;
    }

    // min_bg of 90 -> threshold of 70, 110 -> 80, and 130 -> 90
    var threshold = min_bg - 0.5*(min_bg-50);

    rT = {
        'temp': 'absolute'
        , 'bg': bg
        , 'tick': tick
        , 'eventualBG': eventualBG
        , 'snoozeBG': snoozeBG
    };

    var basaliob;
    if (iob_data.basaliob) { basaliob = iob_data.basaliob; }
    else { basaliob = iob_data.iob - iob_data.bolussnooze; }

    // generate predicted future BGs based on IOB, COB, and current absorption rate

    var COBpredBGs = [];
    var aCOBpredBGs = [];
    var IOBpredBGs = [];
    COBpredBGs.push(bg);
    aCOBpredBGs.push(bg);
    IOBpredBGs.push(bg);
    //console.error(meal_data);
    // carb impact and duration are 0 unless changed below
    var ci = 0;
    var cid = 0;
    // calculate current carb absorption rate, and how long to absorb all carbs
    // CI = current carb impact on BG in mg/dL/5m
    ci = Math.round((minDelta - bgi)*10)/10;
    if (meal_data.mealCOB * 2 > meal_data.carbs) {
        // set ci to a minimum of 3mg/dL/5m (default) if less than half of carbs have absorbed
        ci = Math.max(profile.min_5m_carbimpact, ci);
    }
    aci = 10;
    //5m data points = g * (1U/10g) * (40mg/dL/1U) / (mg/dL/5m)
    cid = meal_data.mealCOB * ( sens / profile.carb_ratio ) / ci;
    acid = meal_data.mealCOB * ( sens / profile.carb_ratio ) / aci;
    console.error("Carb Impact:",ci,"mg/dL per 5m; CI Duration:",Math.round(10*cid/6)/10,"hours");
    console.error("Accel. Carb Impact:",aci,"mg/dL per 5m; ACI Duration:",Math.round(10*acid/6)/10,"hours");
    var minPredBG = 999;
    var maxPredBG = bg;
    var eventualPredBG = bg;
    try {
        iobArray.forEach(function(iobTick) {
            //console.error(iobTick);
            predBGI = round(( -iobTick.activity * sens * 5 ), 2);
            // predicted deviation impact drops linearly from current deviation down to zero
            // over 60 minutes (data points every 5m)
            predDev = ci * ( 1 - Math.min(1,IOBpredBGs.length/(60/5)) );
            IOBpredBG = IOBpredBGs[IOBpredBGs.length-1] + predBGI + predDev;
            //IOBpredBG = IOBpredBGs[IOBpredBGs.length-1] + predBGI;
            // predicted carb impact drops linearly from current carb impact down to zero
            // eventually accounting for all carbs (if they can be absorbed over DIA)
            predCI = Math.max(0, ci * ( 1 - COBpredBGs.length/Math.max(cid*2,1) ) );
            predACI = Math.max(0, aci * ( 1 - COBpredBGs.length/Math.max(acid*2,1) ) );
            COBpredBG = COBpredBGs[COBpredBGs.length-1] + predBGI + Math.min(0,predDev) + predCI;
            aCOBpredBG = aCOBpredBGs[aCOBpredBGs.length-1] + predBGI + Math.min(0,predDev) + predACI;
            //console.error(predBGI, predCI, predBG);
            IOBpredBGs.push(IOBpredBG);
            COBpredBGs.push(COBpredBG);
            aCOBpredBGs.push(aCOBpredBG);
            // wait 45m before setting minPredBG
            if ( COBpredBGs.length > 9 && (COBpredBG < minPredBG) ) { minPredBG = COBpredBG; }
            if ( COBpredBG > maxPredBG ) { maxPredBG = COBpredBG; }
        });
        // set eventualBG to include effect of carbs
        //console.error("PredBGs:",JSON.stringify(predBGs));
    } catch (e) {
        console.error("Problem with iobArray.  Optional feature Advanced Meal Assist disabled.");
    }
    rT.predBGs = {};
    IOBpredBGs.forEach(function(p, i, theArray) {
        theArray[i] = Math.round(Math.min(401,Math.max(39,p)));
    });
    for (var i=IOBpredBGs.length-1; i > 12; i--) {
        if (IOBpredBGs[i-1] != IOBpredBGs[i]) { break; }
        else { IOBpredBGs.pop(); }
    }
    rT.predBGs.IOB = IOBpredBGs;
    if (meal_data.mealCOB > 0) {
        aCOBpredBGs.forEach(function(p, i, theArray) {
            theArray[i] = Math.round(Math.min(401,Math.max(39,p)));
        });
        for (var i=aCOBpredBGs.length-1; i > 12; i--) {
            if (aCOBpredBGs[i-1] != aCOBpredBGs[i]) { break; }
            else { aCOBpredBGs.pop(); }
        }
        rT.predBGs.aCOB = aCOBpredBGs;
    }
    if (meal_data.mealCOB > 0 && ci > 0 ) {
        COBpredBGs.forEach(function(p, i, theArray) {
            theArray[i] = Math.round(Math.min(401,Math.max(39,p)));
        });
        for (var i=COBpredBGs.length-1; i > 12; i--) {
            if (COBpredBGs[i-1] != COBpredBGs[i]) { break; }
            else { COBpredBGs.pop(); }
        }
        rT.predBGs.COB = COBpredBGs;
        eventualBG = Math.max(eventualBG, Math.round(COBpredBGs[COBpredBGs.length-1]) );
        rT.eventualBG = eventualBG;
        minPredBG = Math.min(minPredBG, eventualBG);
        // set snoozeBG to minPredBG
        snoozeBG = Math.round(Math.max(snoozeBG,minPredBG));
        rT.snoozeBG = snoozeBG;
    }

    rT.COB=meal_data.mealCOB;
    rT.IOB=iob_data.iob;
    rT.reason="COB: " + round(meal_data.mealCOB, 1) + ", Dev: " + deviation + ", BGI: " + bgi + ", ISF: " + convert_bg(sens, profile) + ", Target: " + convert_bg(target_bg, profile) + "; ";
    if (typeof autosens_data !== 'undefined' && profile.autosens_adjust_targets && autosens_data.ratio != 1)
        rT.reason += "Autosens: " + autosens_data.ratio + "; ";
    if (bg < threshold) { // low glucose suspend mode: BG is < ~80
        rT.reason += "BG " + convert_bg(bg, profile) + "<" + convert_bg(threshold, profile);
        if ((glucose_status.delta <= 0 && minDelta <= 0) || (glucose_status.delta < expectedDelta && minDelta < expectedDelta) || bg < 60 ) {
            // BG is still falling / rising slower than predicted
            return tempBasalFunctions.setTempBasal(0, 30, profile, rT, currenttemp);
        }
        if (glucose_status.delta > minDelta) {
            rT.reason += ", delta " + glucose_status.delta + ">0";
        } else {
            rT.reason += ", min delta " + minDelta.toFixed(2) + ">0";
        }
        if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
            rT.reason += ", temp " + currenttemp.rate + " ~ req " + round(basal, 2) + "U/hr";
            return rT;
        } else {
            rT.reason += "; setting current basal of " + round(basal, 2) + " as temp";
            return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        }
    }

    if (eventualBG < min_bg) { // if eventual BG is below target:
        rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " < " + convert_bg(min_bg, profile);
        // if 5m or 30m avg BG is rising faster than expected delta
        if (minDelta > expectedDelta && minDelta > 0) {
            if (glucose_status.delta > minDelta) {
                rT.reason += ", but Delta " + tick + " > Exp. Delta " + expectedDelta;
            } else {
                rT.reason += ", but Min. Delta " + minDelta.toFixed(2) + " > Exp. Delta " + expectedDelta;
            }
            if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
                rT.reason += ", temp " + currenttemp.rate + " ~ req " + round(basal, 2) + "U/hr";
                return rT;
            } else {
                rT.reason += "; setting current basal of " + round(basal, 2) + " as temp";
                return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
            }
        }

        if (eventualBG < min_bg) {
            // if we've bolused recently, we can snooze until the bolus IOB decays (at double speed)
            if (snoozeBG > min_bg) { // if adding back in the bolus contribution BG would be above min
                rT.reason += ", bolus snooze: eventual BG range " + convert_bg(eventualBG, profile) + "-" + convert_bg(snoozeBG, profile);
                //console.error(currenttemp, basal );
                if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
                    rT.reason += ", temp " + currenttemp.rate + " ~ req " + round(basal, 2) + "U/hr";
                    return rT;
                } else {
                    rT.reason += "; setting current basal of " + round(basal, 2) + " as temp";
                    return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
                }
            } else {
                // calculate 30m low-temp required to get projected BG up to target
                // use snoozeBG to more gradually ramp in any counteraction of the user's boluses
                // multiply by 2 to low-temp faster for increased hypo safety
                var insulinReq = 2 * Math.min(0, (snoozeBG - target_bg) / sens);
                insulinReq = round( insulinReq , 2);
                if (minDelta < 0 && minDelta > expectedDelta) {
                    // if we're barely falling, newinsulinReq should be barely negative
                    rT.reason += ", Snooze BG " + convert_bg(snoozeBG, profile);
                    var newinsulinReq = round(( insulinReq * (minDelta / expectedDelta) ), 2);
                    //console.error("Increasing insulinReq from " + insulinReq + " to " + newinsulinReq);
                    insulinReq = newinsulinReq;
                }
                // rate required to deliver insulinReq less insulin over 30m:
                var rate = basal + (2 * insulinReq);
                rate = round_basal(rate, profile);
                // if required temp < existing temp basal
                var insulinScheduled = currenttemp.duration * (currenttemp.rate - basal) / 60;
                if (insulinScheduled < insulinReq - basal*0.3) { // if current temp would deliver a lot (30% of basal) less than the required insulin, raise the rate
                    rT.reason += ", "+currenttemp.duration + "m@" + (currenttemp.rate - basal).toFixed(3) + " = " + insulinScheduled.toFixed(3) + " < req " + insulinReq + "-" + (basal*0.3).toFixed(2);
                    return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
                }
                if (typeof currenttemp.rate !== 'undefined' && (currenttemp.duration > 5 && rate >= currenttemp.rate * 0.8)) {
                    rT.reason += ", temp " + (currenttemp.rate).toFixed(3) + " ~< req " + round(rate, 2) + "U/hr";
                    return rT;
                } else {
                    rT.reason += ", setting " + round(rate, 2) + "U/hr";
                    return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
                }
            }
        }
    }

    var minutes_running;
    if (typeof currenttemp.duration == 'undefined' || currenttemp.duration == 0) {
        minutes_running = 30;
    } else if (typeof currenttemp.minutesrunning !== 'undefined'){
        // If the time the current temp is running is not defined, use default request duration of 30 minutes.
        minutes_running = currenttemp.minutesrunning;
    } else {
        minutes_running = 30 - currenttemp.duration;
    }

    // if there is a low-temp running, and eventualBG would be below min_bg without it, let it run
    if (round_basal(currenttemp.rate, profile) < round_basal(basal, profile) ) {
        var lowtempimpact = (currenttemp.rate - basal) * ((30-minutes_running)/60) * sens;
        var adjEventualBG = eventualBG + lowtempimpact;
        if ( adjEventualBG < min_bg ) {
            rT.reason += "letting low temp of " + currenttemp.rate + " run.";
            return rT;
        }
    }

    // if eventual BG is above min but BG is falling faster than expected Delta
    if (minDelta < expectedDelta) {
        if (glucose_status.delta < minDelta) {
            rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " > " + convert_bg(min_bg, profile) + " but Delta " + tick + " < Exp. Delta " + expectedDelta;
        } else {
            rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " > " + convert_bg(min_bg, profile) + " but Min. Delta " + minDelta.toFixed(2) + " < Exp. Delta " + expectedDelta;
        }
        if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
            rT.reason += ", temp " + currenttemp.rate + " ~ req " + round(basal, 2) + "U/hr";
            return rT;
        } else {
            rT.reason += "; setting current basal of " + round(basal, 2) + " as temp";
            return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        }
    }
    // eventualBG or snoozeBG (from minPredBG) is below max_bg
    if (eventualBG < max_bg || snoozeBG < max_bg) {
        // if there is a high-temp running and eventualBG > max_bg, let it run
        if (eventualBG > max_bg && round_basal(currenttemp.rate, profile) > round_basal(basal, profile) ) {
            rT.reason += ", " + eventualBG + " > " + max_bg + ": no action required (letting high temp of " + currenttemp.rate + " run)."
            return rT;
        }

        rT.reason += convert_bg(eventualBG, profile)+"-"+convert_bg(snoozeBG, profile)+" in range: no temp required";
        if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
            rT.reason += ", temp " + currenttemp.rate + " ~ req " + round(basal, 2) + "U/hr";
            return rT;
        } else {
            rT.reason += "; setting current basal of " + round(basal, 2) + " as temp";
            return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        }
    }

    // eventual BG is at/above target:
    // if iob is over max, just cancel any temps
    var basaliob;
    if (iob_data.basaliob) { basaliob = iob_data.basaliob; }
    else { basaliob = iob_data.iob - iob_data.bolussnooze; }
    rT.reason += "Eventual BG " + convert_bg(eventualBG, profile) + " >= " +  convert_bg(max_bg, profile) + ", ";
    if (basaliob > max_iob) {
        rT.reason += "basaliob " + round(basaliob,2) + " > max_iob " + max_iob;
        if (currenttemp.duration > 15 && (round_basal(basal, profile) === round_basal(currenttemp.rate, profile))) {
            rT.reason += ", temp " + currenttemp.rate + " ~ req " + round(basal, 2) + "U/hr";
            return rT;
        } else {
            rT.reason += "; setting current basal of " + round(basal, 2) + " as temp";
            return tempBasalFunctions.setTempBasal(basal, 30, profile, rT, currenttemp);
        }
    } else { // otherwise, calculate 30m high-temp required to get projected BG down to target

        // insulinReq is the additional insulin required to get down to max bg:
        // if in meal assist mode, check if snoozeBG is lower, as eventualBG is not dependent on IOB
        var insulinReq = round( (Math.min(snoozeBG,eventualBG) - target_bg) / sens, 2);
        if (minDelta < 0 && minDelta > expectedDelta) {
            var newinsulinReq = round(( insulinReq * (1 - (minDelta / expectedDelta)) ), 2);
            //console.error("Reducing insulinReq from " + insulinReq + " to " + newinsulinReq);
            insulinReq = newinsulinReq;
        }
        // if that would put us over max_iob, then reduce accordingly
        if (insulinReq > max_iob-basaliob) {
            rT.reason += "max_iob " + max_iob + ", ";
            insulinReq = max_iob-basaliob;
        }

        // rate required to deliver insulinReq more insulin over 30m:
        var rate = basal + (2 * insulinReq);
        rate = round_basal(rate, profile);

//        var maxSafeBasal = Math.min(profile.max_basal, 3 * profile.max_daily_basal, 4 * basal);

        var maxSafeBasal = tempBasalFunctions.getMaxSafeBasal(profile);

        if (rate > maxSafeBasal) {
            rT.reason += "adj. req. rate: "+round(rate, 2)+" to maxSafeBasal: "+maxSafeBasal+", ";
            rate = round_basal(maxSafeBasal, profile);
        }

        var insulinScheduled = currenttemp.duration * (currenttemp.rate - basal) / 60;
        if (insulinScheduled >= insulinReq * 2) { // if current temp would deliver >2x more than the required insulin, lower the rate
            rT.reason += currenttemp.duration + "m@" + (currenttemp.rate - basal).toFixed(3) + " = " + insulinScheduled.toFixed(3) + " > 2 * req " + insulinReq + ". Setting temp basal of " + round(rate, 2) + "U/hr";
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }

        if (typeof currenttemp.duration == 'undefined' || currenttemp.duration == 0) { // no temp is set
            rT.reason += "no temp, setting " + round(rate, 2) + "U/hr";
            return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
        }

        if (currenttemp.duration > 5 && (round_basal(rate, profile) <= round_basal(currenttemp.rate, profile))) { // if required temp <~ existing temp basal
            rT.reason += "temp " + (currenttemp.rate).toFixed(3) + " >~ req " + round(rate, 2) + "U/hr";
            return rT;
        }

        // required temp > existing temp basal
        rT.reason += "temp " + (currenttemp.rate).toFixed(3) + " < " + round(rate, 2) + "U/hr";
        return tempBasalFunctions.setTempBasal(rate, 30, profile, rT, currenttemp);
    }

};

module.exports = determine_basal;

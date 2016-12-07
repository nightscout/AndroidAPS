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
var determine_basal = function determine_basal(glucose_status, currenttemp, iob_data, profile, offline, meal_data, setTempBasal) {
    var rT = { //short for requestedTemp
    };

    if (typeof profile === 'undefined' || typeof profile.current_basal === 'undefined') {
        rT.error ='Error: could not get current basal rate';
        return rT;
    }

    var bg = glucose_status.glucose;
    if (bg < 38) {  //Dexcom is in ??? mode or calibrating, do nothing. Asked @benwest for raw data in iter_glucose
        rT.error = "CGM is calibrating or in ??? state";
        return rT;
    }

    var max_iob = profile.max_iob; // maximum amount of non-bolus IOB OpenAPS will ever deliver

    // if target_bg is set, great. otherwise, if min and max are set, then set target to their average
    var target_bg;
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


    if (typeof iob_data === 'undefined' ) {
        rT.error ='Error: iob_data undefined';
        return rT;
    }

    if (typeof iob_data.activity === 'undefined' || typeof iob_data.iob === 'undefined' || typeof iob_data.activity === 'undefined') {
        rT.error ='Error: iob_data missing some property';
        return rT;
    }

    var tick;

    if (glucose_status.delta >= 0) {
        tick = "+" + glucose_status.delta;
    } else {
        tick = glucose_status.delta;
    }
    var minDelta = Math.min(glucose_status.delta, glucose_status.avgdelta);
    //var maxDelta = Math.max(glucose_status.delta, glucose_status.avgdelta);


    //calculate BG impact: the amount BG "should" be rising or falling based on insulin activity alone
    var bgi = Math.round(( -iob_data.activity * profile.sens * 5 )*100)/100;
    // project positive deviations for 15 minutes
    var deviation = Math.round( 15 / 5 * ( glucose_status.avgdelta - bgi ) );
    // project negative deviations for 30 minutes
    if (deviation < 0) {
        deviation = Math.round( 30 / 5 * ( glucose_status.avgdelta - bgi ) );
    }
    //console.log("Avg.Delta: " + glucose_status.avgdelta.toFixed(1) + ", BGI: " + bgi.toFixed(1) + " 15m activity projection: " + deviation.toFixed(0));

    // calculate the naive (bolus calculator math) eventual BG based on net IOB and sensitivity
    var naive_eventualBG = Math.round( bg - (iob_data.iob * profile.sens) );
    // and adjust it for the deviation above
    var eventualBG = naive_eventualBG + deviation;
    // calculate what portion of that is due to bolussnooze
    var bolusContrib = iob_data.bolussnooze * profile.sens;
    // and add it back in to get snoozeBG, plus another 50% to avoid low-temping at mealtime
    var naive_snoozeBG = Math.round( naive_eventualBG + 1.5 * bolusContrib );
    // adjust that for deviation like we did eventualBG
    var snoozeBG = naive_snoozeBG + deviation;

    //console.log("BG: " + bg +"(" + tick + ","+glucose_status.avgdelta.toFixed(1)+")"+ " -> " + eventualBG + "-" + snoozeBG + " (Unadjusted: " + naive_eventualBG + "-" + naive_snoozeBG + "), BGI: " + bgi);

    var expectedDelta = Math.round(( bgi + ( target_bg - eventualBG ) / ( profile.dia * 60 / 5 ) )*10)/10;
    //console.log("expectedDelta: " + expectedDelta);

    if (typeof eventualBG === 'undefined' || isNaN(eventualBG)) {
        rT.error ='Error: could not calculate eventualBG';
        return rT;
    }

    // min_bg of 90 -> threshold of 70, 110 -> 80, and 130 -> 90
    var threshold = profile.min_bg - 0.5*(profile.min_bg-50);

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
    // allow meal assist to run when carbs are just barely covered
    if (minDelta > Math.max(3, bgi) && ( (meal_data.carbs > 0 && (1.1 * meal_data.carbs/profile.carb_ratio > meal_data.boluses + basaliob)) || ( deviation > 25 && minDelta > 7 ) ) ) {
        // ignore all covered IOB, and just set eventualBG to the current bg
        eventualBG = Math.max(bg,eventualBG) + deviation;
        rT.eventualBG = eventualBG;
        profile.min_bg = 80;
        target_bg = (profile.min_bg + profile.max_bg) / 2;
        expectedDelta = Math.round(( bgi + ( target_bg - eventualBG ) / ( profile.dia * 60 / 5 ) )*10)/10;
        rT.mealAssist = "On: Carbs: " + meal_data.carbs + " Boluses: " + meal_data.boluses + " Target: " + target_bg + " Deviation: " + deviation + " BGI: " + bgi;
    } else {
        rT.mealAssist = "Off: Carbs: " + meal_data.carbs + " Boluses: " + meal_data.boluses + " Target: " + target_bg + " Deviation: " + deviation + " BGI: " + bgi;
    }
    if (bg < threshold) { // low glucose suspend mode: BG is < ~80
        rT.reason = "BG " + bg + "<" + threshold;
        if ((glucose_status.delta <= 0 && glucose_status.avgdelta <= 0) || (glucose_status.delta < expectedDelta && glucose_status.avgdelta < expectedDelta)) {
            // BG is still falling / rising slower than predicted
            return setTempBasal(0, 30, profile, rT, offline);
        }
        if (glucose_status.delta > glucose_status.avgdelta) {
            rT.reason += ", delta " + glucose_status.delta + ">0";
        } else {
            rT.reason += ", avg delta " + glucose_status.avgdelta.toFixed(2) + ">0";
        }
        if (currenttemp.rate > profile.current_basal) { // if a high-temp is running
            rT.reason += ", cancel high temp";
            return setTempBasal(0, 0, profile, rT, offline); // cancel high temp
        } else if (currenttemp.duration && eventualBG > profile.max_bg) { // if low-temped and predicted to go high from negative IOB
            rT.reason += ", cancel low temp";
            return setTempBasal(0, 0, profile, rT, offline); // cancel low temp
        }
        rT.reason += "; no high-temp to cancel";
        return rT;
    }
    if (eventualBG < profile.min_bg) { // if eventual BG is below target:
        if (rT.mealAssist.indexOf("On") == 0) {
            rT.reason = "Meal assist: " + meal_data.carbs + "g, " + meal_data.boluses + "U";
        } else {
            rT.reason = "Eventual BG " + eventualBG + "<" + profile.min_bg;
            // if 5m or 15m avg BG is rising faster than expected delta
            if (minDelta > expectedDelta && minDelta > 0) {
                if (glucose_status.delta > glucose_status.avgdelta) {
                    rT.reason += ", but Delta " + tick + " > Exp. Delta " + expectedDelta;
                } else {
                    rT.reason += ", but Avg. Delta " + glucose_status.avgdelta.toFixed(2) + " > Exp. Delta " + expectedDelta;
                }
                if (currenttemp.duration > 0) { // if there is currently any temp basal running
                    rT.reason = rT.reason += "; cancel";
                    return setTempBasal(0, 0, profile, rT, offline); // cancel temp
                } else {
                rT.reason = rT.reason += "; no temp to cancel";
                return rT;
                }
            }
        }

        if (eventualBG < profile.min_bg) {
            // if this is just due to boluses, we can snooze until the bolus IOB decays (at double speed)
            if (snoozeBG > profile.min_bg) { // if adding back in the bolus contribution BG would be above min
                // if BG is falling and high-temped, or rising and low-temped, cancel
                // compare against zero here, not BGI, because BGI will be highly negative from boluses and no carbs
                if (glucose_status.delta < 0 && currenttemp.duration > 0 && currenttemp.rate > profile.current_basal) {
                    rT.reason += tick + ", and temp " + currenttemp.rate + " > basal " + profile.current_basal;
                    return setTempBasal(0, 0, profile, rT, offline); // cancel temp
                } else if (glucose_status.delta > 0 && currenttemp.duration > 0 && currenttemp.rate < profile.current_basal) {
                    rT.reason += tick + ", and temp " + currenttemp.rate + " < basal " + profile.current_basal;
                    return setTempBasal(0, 0, profile, rT, offline); // cancel temp
                }

                rT.reason += ", bolus snooze: eventual BG range " + eventualBG + "-" + snoozeBG;
                return rT;
            } else {
                // calculate 30m low-temp required to get projected BG up to target
                // use snoozeBG to more gradually ramp in any counteraction of the user's boluses
                // multiply by 2 to low-temp faster for increased hypo safety
                var insulinReq = 2 * Math.min(0, (snoozeBG - target_bg) / profile.sens);
                if (minDelta < 0 && minDelta > expectedDelta) {
                    // if we're barely falling, newinsulinReq should be barely negative
                    rT.reason += ", Snooze BG " + snoozeBG;
                    var newinsulinReq = Math.round(( insulinReq * (minDelta / expectedDelta) ) * 100)/100;
                    //console.log("Increasing insulinReq from " + insulinReq + " to " + newinsulinReq);
                    insulinReq = newinsulinReq;
                }
                // rate required to deliver insulinReq less insulin over 30m:
                var rate = profile.current_basal + (2 * insulinReq);
                rate = Math.round( rate * 1000 ) / 1000;
                // if required temp < existing temp basal
                var insulinScheduled = currenttemp.duration * (currenttemp.rate - profile.current_basal) / 60;
                if (insulinScheduled < insulinReq - 0.2) { // if current temp would deliver >0.2U less than the required insulin, raise the rate
                    rT.reason = currenttemp.duration + "m@" + (currenttemp.rate - profile.current_basal).toFixed(3) + " = " + insulinScheduled.toFixed(3) + " < req " + insulinReq + "-0.2U";
                    return setTempBasal(rate, 30, profile, rT, offline);
                }
                if (typeof currenttemp.rate !== 'undefined' && (currenttemp.duration > 5 && rate > currenttemp.rate - 0.1)) {
                    rT.reason += ", temp " + currenttemp.rate + " ~< req " + rate + "U/hr";
                    return rT;
                } else {
                    rT.reason += ", setting " + rate + "U/hr";
                    return setTempBasal(rate, 30, profile, rT, offline);
                }
            }
        }
    }

    // if eventual BG is above min but BG is falling faster than expected Delta
    if (minDelta < expectedDelta) {
        if (glucose_status.delta < glucose_status.avgdelta) {
            rT.reason = "Eventual BG " + eventualBG + ">" + profile.min_bg + " but Delta " + tick + " < Exp. Delta " + expectedDelta;
        } else {
            rT.reason = "Eventual BG " + eventualBG + ">" + profile.min_bg + " but Avg. Delta " + glucose_status.avgdelta.toFixed(2) + " < Exp. Delta " + expectedDelta;
        }
        if (currenttemp.duration > 0) { // if there is currently any temp basal running
            rT.reason = rT.reason += "; cancel";
            return setTempBasal(0, 0, profile, rT, offline); // cancel temp
        } else {
            rT.reason = rT.reason += "; no temp to cancel";
            return rT;
        }
    }

    if (eventualBG < profile.max_bg) {
        rT.reason = eventualBG + " is in range. No temp required";
        if (currenttemp.duration > 0) { // if there is currently any temp basal running
            rT.reason = rT.reason += "; cancel";
            return setTempBasal(0, 0, profile, rT, offline); // cancel temp
        }
        if (offline == 'Offline') {
            // if no temp is running or required, set the current basal as a temp, so you can see on the pump that the loop is working
            if ((!currenttemp.duration || (currenttemp.rate == profile.current_basal)) && !rT.duration) {
                rT.reason = rT.reason + "; setting current basal of " + profile.current_basal + " as temp";
                return setTempBasal(profile.current_basal, 30, profile, rT, offline);
            }
        }
        return rT;
    }

    if (snoozeBG < profile.max_bg) {
        rT.reason = snoozeBG + " < " + profile.max_bg;
        if (currenttemp.duration > 0) { // if there is currently any temp basal running
            rT.reason = rT.reason += "; cancel";
            return setTempBasal(0, 0, profile, rT, offline); // cancel temp
        } else {
            rT.reason = rT.reason += "; no temp to cancel";
            return rT;
        }
    }


    // eventual BG is at/above target:
    // if iob is over max, just cancel any temps
    var basaliob;
    if (iob_data.basaliob) { basaliob = iob_data.basaliob; }
    else { basaliob = iob_data.iob - iob_data.bolussnooze; }
    rT.reason = "Eventual BG " + eventualBG + ">=" + profile.max_bg + ", ";
    if (basaliob > max_iob) {
        rT.reason = "basaliob " + basaliob + " > max_iob " + max_iob;
        return setTempBasal(0, 0, profile, rT, offline);
    } else { // otherwise, calculate 30m high-temp required to get projected BG down to target

        // insulinReq is the additional insulin required to get down to max bg:
        // if in meal assist mode, check if snoozeBG is lower, as eventualBG is not dependent on IOB
        var insulinReq = (Math.min(snoozeBG,eventualBG) - target_bg) / profile.sens;
        if (minDelta < 0 && minDelta > expectedDelta) {
            var newinsulinReq = Math.round(( insulinReq * (1 - (minDelta / expectedDelta)) ) * 100)/100;
            //console.log("Reducing insulinReq from " + insulinReq + " to " + newinsulinReq);
            insulinReq = newinsulinReq;
        }
        // if that would put us over max_iob, then reduce accordingly
        if (insulinReq > max_iob-basaliob) {
            rT.reason = "max_iob " + max_iob + ", ";
            insulinReq = max_iob-basaliob;
        }

        // rate required to deliver insulinReq more insulin over 30m:
        var rate = profile.current_basal + (2 * insulinReq);
        rate = Math.round( rate * 1000 ) / 1000;

            var maxSafeBasal = Math.min(profile.max_basal, 3 * profile.max_daily_basal, 4 * profile.current_basal);
        if (rate > maxSafeBasal) {
            rT.reason += "adj. req. rate:"+rate.toFixed(1) +" to maxSafeBasal:"+maxSafeBasal.toFixed(1)+", ";
            rate = maxSafeBasal;
        }

        var insulinScheduled = currenttemp.duration * (currenttemp.rate - profile.current_basal) / 60;
        if (insulinScheduled > insulinReq + 0.2) { // if current temp would deliver >0.2U more than the required insulin, lower the rate
            rT.reason = currenttemp.duration + "m@" + (currenttemp.rate - profile.current_basal).toFixed(3) + " = " + insulinScheduled.toFixed(3) + " > req " + insulinReq + "+0.2U";
            return setTempBasal(rate, 30, profile, rT, offline);
        }

        if (typeof currenttemp.duration == 'undefined' || currenttemp.duration == 0) { // no temp is set
            rT.reason += "no temp, setting " + rate + "U/hr";
            return setTempBasal(rate, 30, profile, rT, offline);
        }

        if (currenttemp.duration > 5 && rate < currenttemp.rate + 0.1) { // if required temp <~ existing temp basal
            rT.reason += "temp " + currenttemp.rate + " >~ req " + rate + "U/hr";
            return rT;
        }

        // required temp > existing temp basal
        rT.reason += "temp " + currenttemp.rate + "<" + rate + "U/hr";
        return setTempBasal(rate, 30, profile, rT, offline);
    }

};

module.exports = determine_basal;
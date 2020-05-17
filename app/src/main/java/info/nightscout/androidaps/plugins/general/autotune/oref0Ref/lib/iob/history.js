
var tz = require('moment-timezone');
var basalprofile = require('../profile/basal.js');
var _ = require('lodash');
var moment = require('moment');

function splitTimespanWithOneSplitter(event,splitter) {

    var resultArray = [event];

    if (splitter.type === 'recurring') {

        var startMinutes = event.started_at.getHours() * 60 + event.started_at.getMinutes();
        var endMinutes = startMinutes + event.duration;

        // 1440 = one day; no clean way to check if the event overlaps midnight
        // so checking if end of event in minutes is past midnight

        if (event.duration > 30 || (startMinutes < splitter.minutes && endMinutes > splitter.minutes) || (endMinutes > 1440 && splitter.minutes < (endMinutes - 1440))) {

            var event1 = _.cloneDeep(event);
            var event2 = _.cloneDeep(event);

            var event1Duration = 0;

            if (event.duration > 30) {
                event1Duration = 30;
            } else {
                var splitPoint = splitter.minutes;
                if (endMinutes > 1440) { splitPoint = 1440; }
                event1Duration = splitPoint - startMinutes;
            }

            var event1EndDate = moment(event.started_at).add(event1Duration,'minutes');

            event1.duration = event1Duration;

            event2.duration  = event.duration - event1Duration;
            event2.timestamp = event1EndDate.format();
            event2.started_at = new Date(event2.timestamp);
            event2.date = event2.started_at.getTime();

            resultArray = [event1,event2];
        }
    }

    return resultArray;
}

function splitTimespan(event, splitterMoments) {

    var results = [event];

    var splitFound = true;

    while(splitFound) {

        var resultArray = [];
        splitFound = false;

        _.forEach(results,function split(o) {
            _.forEach(splitterMoments,function split(p) {
                var splitResult = splitTimespanWithOneSplitter(o,p);
                if (splitResult.length > 1) {
                    resultArray = resultArray.concat(splitResult);
                    splitFound = true;
                    return false;
                }
            });

            if (!splitFound) resultArray = resultArray.concat([o]);

        });

        results = resultArray;
    }

    return results;
}

// Split currentEvent around any conflicting suspends
// by removing the time period from the event that
// overlaps with any suspend.
function splitAroundSuspends (currentEvent, pumpSuspends, firstResumeTime, suspendedPrior, lastSuspendTime, currentlySuspended) {
    var events = [];

    var firstResumeStarted = new Date(firstResumeTime);
    var firstResumeDate = firstResumeStarted.getTime()

    var lastSuspendStarted = new Date(lastSuspendTime);
    var lastSuspendDate = lastSuspendStarted.getTime();

    if (suspendedPrior && (currentEvent.date < firstResumeDate)) {
        if ((currentEvent.date+currentEvent.duration*60*1000) < firstResumeDate) {
            currentEvent.duration = 0;
        } else {
            currentEvent.duration = ((currentEvent.date+currentEvent.duration*60*1000)-firstResumeDate)/60/1000;

            currentEvent.started_at = new Date(tz(firstResumeTime));
            currentEvent.date = firstResumeDate
        }
    }

    if (currentlySuspended && ((currentEvent.date+currentEvent.duration*60*1000) > lastSuspendTime)) {
        if (currentEvent.date > lastSuspendTime) {
            currentEvent.duration = 0;
        } else {
            currentEvent.duration = (firstResumeDate - currentEvent.date)/60/1000;
        }
    }

    events.push(currentEvent);

    if (currentEvent.duration === 0) {
        // bail out rather than wasting time going through the rest of the suspend events
        return events;
    }

    for (var i=0; i < pumpSuspends.length; i++) {
        var suspend = pumpSuspends[i];

        for (var j=0; j < events.length; j++) {

            if ((events[j].date <= suspend.date) && (events[j].date+events[j].duration*60*1000) > suspend.date) {
                // event started before the suspend, but finished after the suspend started

                if ((events[j].date+events[j].duration*60*1000) > (suspend.date+suspend.duration*60*1000)) {
                    var event2 = _.cloneDeep(events[j]);

                    var event2StartDate = moment(suspend.started_at).add(suspend.duration,'minutes');

                    event2.timestamp = event2StartDate.format();
                    event2.started_at = new Date(tz(event2.timestamp));
                    event2.date = suspend.date+suspend.duration*60*1000;

                    event2.duration = ((events[j].date+events[j].duration*60*1000) - (suspend.date+suspend.duration*60*1000))/60/1000;

                    events.push(event2);
                }

                events[j].duration = (suspend.date-events[j].date)/60/1000;

            } else if ((suspend.date <= events[j].date) && (suspend.date+suspend.duration*60*1000 > events[j].date)) {
                // suspend started before the event, but finished after the event started
            
                events[j].duration = ((events[j].date+events[j].duration*60*1000) - (suspend.date+suspend.duration*60*1000))/60/1000;

                var eventStartDate = moment(suspend.started_at).add(suspend.duration,'minutes');

                events[j].timestamp = eventStartDate.format();
                events[j].started_at = new Date(tz(events[j].timestamp));
                events[j].date = suspend.date + suspend.duration*60*1000;
            }
        }
    }

    return events;
}

function calcTempTreatments (inputs, zeroTempDuration) {
    var pumpHistory = inputs.history;
    var pumpHistory24 = inputs.history24;
    var profile_data = inputs.profile;
    var autosens_data = inputs.autosens;
    var tempHistory = [];
    var tempBoluses = [];
    var pumpSuspends = [];
    var pumpResumes = [];
    var suspendedPrior = false;
    var firstResumeTime, lastSuspendTime;
    var currentlySuspended = false;
    var suspendError = false;

    var now = new Date(tz(inputs.clock));

    if(inputs.history24) {
        var pumpHistory =  [ ].concat(inputs.history).concat(inputs.history24);
    }

    var lastRecordTime = now;

    // Gather the times the pump was suspended and resumed
    for (var i=0; i < pumpHistory.length; i++) {
        var temp = {};

        var current = pumpHistory[i];

        if (current._type === "PumpSuspend") {
            temp.timestamp = current.timestamp;
            temp.started_at = new Date(tz(current.timestamp));
            temp.date = temp.started_at.getTime();
            pumpSuspends.push(temp);
        } else if (current._type === "PumpResume") {
            temp.timestamp = current.timestamp;
            temp.started_at = new Date(tz(current.timestamp));
            temp.date = temp.started_at.getTime();
            pumpResumes.push(temp);
        }
    }

    pumpSuspends = _.sortBy(pumpSuspends, 'date');

    pumpResumes = _.sortBy(pumpResumes, 'date');

    if (pumpResumes.length > 0) {
        firstResumeTime = pumpResumes[0].timestamp;

        // Check to see if our first resume was prior to our first suspend
        // indicating suspend was prior to our first event.
        if (pumpSuspends.length === 0 || (pumpResumes[0].date < pumpSuspends[0].date)) {
            suspendedPrior = true;
        }

    }

    var j=0;  // matching pumpResumes entry;

    // Match the resumes with the suspends to get durations
    for (i=0; i < pumpSuspends.length; i++) {
        for (; j < pumpResumes.length; j++) {
            if (pumpResumes[j].date > pumpSuspends[i].date) {
                break;
            }
        }

        if ((j >= pumpResumes.length) && !currentlySuspended) {
            // even though it isn't the last suspend, we have reached
            // the final suspend. Set resume last so the
            // algorithm knows to suspend all the way
            // through the last record beginning at the last suspend
            // since we don't have a matching resume.
            currentlySuspended = 1;
            lastSuspendTime = pumpSuspends[i].timestamp;

            break;
        }

        pumpSuspends[i].duration = (pumpResumes[j].date - pumpSuspends[i].date)/60/1000;

    }

    // These checks indicate something isn't quite aligned.
    // Perhaps more resumes that suspends or vice versa...
    if (!suspendedPrior && !currentlySuspended && (pumpResumes.length !== pumpSuspends.length)) {
        console.error("Mismatched number of resumes("+pumpResumes.length+") and suspends("+pumpSuspends.length+")!");
    } else if (suspendedPrior && !currentlySuspended && ((pumpResumes.length-1) !== pumpSuspends.length)) {
        console.error("Mismatched number of resumes("+pumpResumes.length+") and suspends("+pumpSuspends.length+") assuming suspended prior to history block!");
    } else if (!suspendedPrior && currentlySuspended && (pumpResumes.length !== (pumpSuspends.length-1))) {
        console.error("Mismatched number of resumes("+pumpResumes.length+") and suspends("+pumpSuspends.length+") assuming suspended past end of history block!");
    } else if (suspendedPrior && currentlySuspended && (pumpResumes.length !== pumpSuspends.length)) {
        console.error("Mismatched number of resumes("+pumpResumes.length+") and suspends("+pumpSuspends.length+") assuming suspended prior to and past end of history block!");
    }

    if (i < (pumpSuspends.length-1)) {
        // truncate any extra suspends. if we had any extras
        // the error checks above would have issued a error log message
        pumpSuspends.splice(i+1, pumpSuspends.length-i-1);
    }

    // Pick relevant events for processing and clean the data

    for (i=0; i < pumpHistory.length; i++) {
        var current = pumpHistory[i];
        if (current.bolus && current.bolus._type === "Bolus") {
            var temp = current;
            current = temp.bolus;
        }
        if (current.created_at) {
            current.timestamp = current.created_at;
        }
        var currentRecordTime = new Date(tz(current.timestamp));
        //console.error(current);
        //console.error(currentRecordTime,lastRecordTime);
        // ignore duplicate or out-of-order records (due to 1h and 24h overlap, or timezone changes)
        if (currentRecordTime > lastRecordTime) {
            //console.error("",currentRecordTime," > ",lastRecordTime);
            //process.stderr.write(".");
            continue;
        } else {
            lastRecordTime = currentRecordTime;
        }
        if (current._type === "Bolus") {
            var temp = {};
            temp.timestamp = current.timestamp;
            temp.started_at = new Date(tz(current.timestamp));
            if (temp.started_at > now) {
                //console.error("Warning: ignoring",current.amount,"U bolus in the future at",temp.started_at);
                process.stderr.write(" "+current.amount+"U @ "+temp.started_at);
            } else {
                temp.date = temp.started_at.getTime();
                temp.insulin = current.amount;
                tempBoluses.push(temp);
            }
        } else if (current.eventType === "Meal Bolus" || current.eventType === "Correction Bolus" || current.eventType === "Snack Bolus" || current.eventType === "Bolus Wizard") {
            //imports treatments entered through Nightscout Care Portal
            //"Bolus Wizard" refers to the Nightscout Bolus Wizard, not the Medtronic Bolus Wizard
            var temp = {};
            temp.timestamp = current.created_at;
            temp.started_at = new Date(tz(temp.timestamp));
            temp.date = temp.started_at.getTime();
            temp.insulin = current.insulin;
            tempBoluses.push(temp);
        } else if (current.enteredBy === "xdrip") {
            var temp = {};
            temp.timestamp = current.timestamp;
            temp.started_at = new Date(tz(temp.timestamp));
            temp.date = temp.started_at.getTime();
            temp.insulin = current.insulin;
            tempBoluses.push(temp);
        } else if (current.enteredBy ==="HAPP_App" && current.insulin) {
            var temp = {};
            temp.timestamp = current.created_at;
            temp.started_at = new Date(tz(temp.timestamp));
            temp.date = temp.started_at.getTime();
            temp.insulin = current.insulin;
            tempBoluses.push(temp);
        } else if (current.eventType === "Temp Basal" && (current.enteredBy === "HAPP_App" || current.enteredBy === "openaps://AndroidAPS")) {
            var temp = {};
            temp.rate = current.absolute;
            temp.duration = current.duration;
            temp.timestamp = current.created_at;
            temp.started_at = new Date(tz(temp.timestamp));
            temp.date = temp.started_at.getTime();
            tempHistory.push(temp);
        } else if (current.eventType === "Temp Basal") {
            var temp = {};
            temp.rate = current.rate;
            temp.duration = current.duration;
            temp.timestamp = current.timestamp;
            temp.started_at = new Date(tz(temp.timestamp));
            temp.date = temp.started_at.getTime();
            tempHistory.push(temp);
        } else if (current._type === "TempBasal") {
            if (current.temp === 'percent') {
                continue;
            }
            var rate = current.rate;
            var timestamp = current.timestamp;
            var duration;
            if (i>0 && pumpHistory[i-1].timestamp === timestamp && pumpHistory[i-1]._type === "TempBasalDuration") {
                duration = pumpHistory[i-1]['duration (min)'];
            } else {
                for (var iter=0; iter < pumpHistory.length; iter++) {
                    if (pumpHistory[iter].timestamp === timestamp && pumpHistory[iter]._type === "TempBasalDuration") {
                            duration = pumpHistory[iter]['duration (min)'];
                            break;
                    }
                }

                if (duration === undefined) {
                    console.error("No duration found for "+rate+" U/hr basal "+timestamp, pumpHistory[i - 1], current, pumpHistory[i + 1]);
                }
            }
            var temp = {};
            temp.rate = rate;
            temp.timestamp = current.timestamp;
            temp.started_at = new Date(tz(temp.timestamp));
            temp.date = temp.started_at.getTime();
            temp.duration = duration;
            tempHistory.push(temp);
        }
        // Add a temp basal cancel event to ignore future temps and reduce predBG oscillation
        var temp = {};
        temp.rate = 0;
        // start the zero temp 1m in the future to avoid clock skew
        temp.started_at = new Date(now.getTime() + (1 * 60 * 1000));
        temp.date = temp.started_at.getTime();
        if (zeroTempDuration) {
            temp.duration = zeroTempDuration;
        } else {
            temp.duration = 0;
        }
        tempHistory.push(temp);
    }

    // Check for overlapping events and adjust event lengths in case of overlap

    tempHistory = _.sortBy(tempHistory, 'date');

    for (i=0; i+1 < tempHistory.length; i++) {
        if (tempHistory[i].date + tempHistory[i].duration*60*1000 > tempHistory[i+1].date) {
            tempHistory[i].duration = (tempHistory[i+1].date - tempHistory[i].date)/60/1000;
            // Delete AndroidAPS "Cancel TBR records" in which duration is not populated
            if (tempHistory[i+1].duration === null) {
                tempHistory.splice(i+1, 1);
            }
        }
    }

    // Create an array of moments to slit the temps by
    // currently supports basal changes

    var splitterEvents = [];

    _.forEach(profile_data.basalprofile,function addSplitter(o) {
        var splitterEvent = {};
        splitterEvent.type = 'recurring';
        splitterEvent.minutes = o.minutes;
        splitterEvents.push(splitterEvent);
    });

    // iterate through the events and split at basal break points if needed

    var splitHistoryByBasal = [];

    _.forEach(tempHistory, function splitEvent(o) {
        splitHistoryByBasal = splitHistoryByBasal.concat(splitTimespan(o,splitterEvents));
    });

    tempHistory = _.sortBy(tempHistory, function(o) { return o.date; });

    var suspend_zeros_iob = false;

    if (typeof profile_data.suspend_zeros_iob !== 'undefined') {
        suspend_zeros_iob = profile_data.suspend_zeros_iob;
    }

    if (suspend_zeros_iob) {
        // iterate through the events and adjust their 
        // times as required to account for pump suspends
        var splitHistory = [];

        _.forEach(splitHistoryByBasal, function splitSuspendEvent(o) {
            var splitEvents = splitAroundSuspends(o, pumpSuspends, firstResumeTime, suspendedPrior, lastSuspendTime, currentlySuspended);
            splitHistory = splitHistory.concat(splitEvents);
        });

        var zTempSuspendBasals = [];

        // Any existing temp basals during times the pump was suspended are now deleted
        // Add 0 temp basals to negate the profile basal rates during times pump is suspended
        _.forEach(pumpSuspends, function createTempBasal(o) {
            var zTempBasal = [{
                _type: 'SuspendBasal',
                rate: 0,
                duration: o.duration,
                date: o.date,
                started_at: o.started_at
            }];
            zTempSuspendBasals = zTempSuspendBasals.concat(zTempBasal);
        });

        // Add temp suspend basal for maximum DIA (8) up to the resume time
        // if there is no matching suspend in the history before the first
        // resume
        var max_dia_ago = now.getTime() - 8*60*60*1000;
        var firstResumeStarted = new Date(firstResumeTime);
        var firstResumeDate = firstResumeStarted.getTime()

        // impact on IOB only matters if the resume occurred
        // after DIA hours before now.
        // otherwise, first resume date can be ignored. Whatever
        // insulin is present prior to resume will be aged
        // out due to DIA.
        if (suspendedPrior && (max_dia_ago < firstResumeDate)) {

            var suspendStart = new Date(max_dia_ago);
            var suspendStartDate = suspendStart.getTime()
            var started_at = new Date(tz(suspendStart.toISOString()));

            var zTempBasal = [{
               // add _type to aid debugging. It isn't used
               // anywhere.
                _type: 'SuspendBasal',
                rate: 0,
                duration: (firstResumeDate - max_dia_ago)/60/1000,
                date: suspendStartDate,
                started_at: started_at
            }];
            zTempSuspendBasals = zTempSuspendBasals.concat(zTempBasal);
        }

        if (currentlySuspended) {
            var suspendStart = new Date(lastSuspendTime);
            var suspendStartDate = suspendStart.getTime()
            var started_at = new Date(tz(suspendStart.toISOString()));

            var zTempBasal = [{
                _type: 'SuspendBasal',
                rate: 0,
                duration: (now - suspendStartDate)/60/1000,
                date: suspendStartDate,
                timestamp: lastSuspendTime,
                started_at: started_at
            }];
            zTempSuspendBasals = zTempSuspendBasals.concat(zTempBasal);
        }

        // Add the new 0 temp basals to the splitHistory.
        // We have to split the new zero temp basals by the profile
        // basals just like the other temp basals.
        _.forEach(zTempSuspendBasals, function splitEvent(o) {
            splitHistory = splitHistory.concat(splitTimespan(o,splitterEvents));
        });
    } else {
        splitHistory = splitHistoryByBasal;
    }

    splitHistory = _.sortBy(splitHistory, function(o) { return o.date; });

    // tempHistory = splitHistory;

    // iterate through the temp basals and create bolus events from temps that affect IOB

    var tempBolusSize;

    for (i=0; i < splitHistory.length; i++) {

        var currentItem = splitHistory[i];

        if (currentItem.duration > 0) {

            var currentRate = profile_data.current_basal;
            if (!_.isEmpty(profile_data.basalprofile)) {
                currentRate = basalprofile.basalLookup(profile_data.basalprofile,new Date(currentItem.timestamp));
            }

            if (typeof profile_data.min_bg !== 'undefined' && typeof profile_data.max_bg !== 'undefined') {
                target_bg = (profile_data.min_bg + profile_data.max_bg) / 2;
            }
            //if (profile_data.temptargetSet && target_bg > 110) {
                //sensitivityRatio = 2/(2+(target_bg-100)/40);
                //currentRate = profile_data.current_basal * sensitivityRatio;
            //}
            var sensitivityRatio;
            var profile = profile_data;
            var normalTarget = 100; // evaluate high/low temptarget against 100, not scheduled basal (which might change)
            if ( profile.half_basal_exercise_target ) {
                var halfBasalTarget = profile.half_basal_exercise_target;
            } else {
                var halfBasalTarget = 160; // when temptarget is 160 mg/dL, run 50% basal (120 = 75%; 140 = 60%)
            }
            if ( profile.exercise_mode && profile.temptargetSet && target_bg >= normalTarget + 5 ) {
                // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
                // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
                var c = halfBasalTarget - normalTarget;
                sensitivityRatio = c/(c+target_bg-normalTarget);
            } else if (typeof autosens_data !== 'undefined' ) {
                sensitivityRatio = autosens_data.ratio;
                //process.stderr.write("Autosens ratio: "+sensitivityRatio+"; ");
            }
            if ( sensitivityRatio ) {
                currentRate = currentRate * sensitivityRatio;
            }

            var netBasalRate = currentItem.rate - currentRate;
            if (netBasalRate < 0) { tempBolusSize = -0.05; }
            else { tempBolusSize = 0.05; }
            var netBasalAmount = Math.round(netBasalRate*currentItem.duration*10/6)/100
            var tempBolusCount = Math.round(netBasalAmount / tempBolusSize);
            var tempBolusSpacing = currentItem.duration / tempBolusCount;
            for (j=0; j < tempBolusCount; j++) {
                var tempBolus = {};
                tempBolus.insulin = tempBolusSize;
                tempBolus.date = currentItem.date + j * tempBolusSpacing*60*1000;
                tempBolus.created_at = new Date(tempBolus.date);
                tempBoluses.push(tempBolus);
            }
        }
    }
    var all_data =  [ ].concat(tempBoluses).concat(tempHistory);
    all_data = _.sortBy(all_data, 'date');
    return all_data;
}
exports = module.exports = calcTempTreatments;

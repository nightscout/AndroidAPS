package info.nightscout.androidaps.plugins.general.autotune.AutotunePrep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction;
import info.nightscout.androidaps.plugins.general.autotune.data.IobInputs;
import info.nightscout.androidaps.plugins.general.autotune.data.NsTreatment;
import info.nightscout.androidaps.plugins.general.autotune.data.TunedProfile;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSTreatment;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.Round;

public class Iob {
    @Inject ProfileFunction profileFunction;
    private final HasAndroidInjector injector;

    public Iob(){
        injector = MainApp.instance();
        injector.androidInjector().inject(this);
    }

    public IobTotal getIOB(IobInputs iobInputs) {
        IobTotal iobTotal = new IobTotal(iobInputs.clock);


        return iobTotal;
    }

    public List<NsTreatment> find_insulin (IobInputs inputs) {
        List<NsTreatment> pumpHistory = inputs.history;
        List<CareportalEvent> careportalEvents = inputs.careportalEvents;
        TunedProfile profile_data = inputs.profile;
        List<NsTreatment> tempHistory = new ArrayList<NsTreatment>();
        List<NsTreatment> tempBoluses = new ArrayList<NsTreatment>();
        List<NsTreatment> pumpSuspends = new ArrayList<NsTreatment>();
        List<NsTreatment> pumpResumes = new ArrayList<NsTreatment>();
        boolean suspendedPrior = false;
        long firstResumeTime, lastSuspendTime;
        boolean currentlySuspended = false;
        boolean suspendError = false;

        long now = System.currentTimeMillis();

        long lastRecordTime = now;

        // Gather the times the pump was suspended and resumed
        /* disable, not sure if it's necessary (zero temp if pump disconnect), minor I think for autotune except if pump stopped for a long time
        for (int i=0; i < careportalEvents.size(); i++) {

            //NsTreatment temp = new NsTreatment();

            CareportalEvent current = careportalEvents.get(i);

            if (current.eventType == "PumpSuspend") {
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
        */
            if (pumpResumes.size() > 0) {
                firstResumeTime = pumpResumes.get(0).date;

                // Check to see if our first resume was prior to our first suspend
                // indicating suspend was prior to our first event.
                if (pumpSuspends.size() == 0 || (pumpResumes.get(0).date < pumpSuspends.get(0).date)) {
                    suspendedPrior = true;
                }

            }

            int j=0;  // matching pumpResumes entry;
            // bloc below left event if not necessary
            // Match the resumes with the suspends to get durations
            int i;
            for (i=0; i < pumpSuspends.size(); i++) {
                for (; j < pumpResumes.size(); j++) {
                    if (pumpResumes.get(j).date > pumpSuspends.get(i).date) {
                        break;
                    }
                }

                if ((j >= pumpResumes.size()) && !currentlySuspended) {
                    // even though it isn't the last suspend, we have reached
                    // the final suspend. Set resume last so the
                    // algorithm knows to suspend all the way
                    // through the last record beginning at the last suspend
                    // since we don't have a matching resume.
                    currentlySuspended = true;
                    lastSuspendTime = pumpSuspends.get(i).date;

                    break;
                }

                pumpSuspends.get(i).duration = (int) (pumpResumes.get(j).date - pumpSuspends.get(i).date)/60/1000;

            }

            // These checks indicate something isn't quite aligned.
            // Perhaps more resumes that suspends or vice versa...
            /*
            if (!suspendedPrior && !currentlySuspended && (pumpResumes.size() != pumpSuspends.size())) {
                console.error("Mismatched number of resumes("+pumpResumes.length+") and suspends("+pumpSuspends.length+")!");
            } else if (suspendedPrior && !currentlySuspended && ((pumpResumes.length-1) !== pumpSuspends.length)) {
                console.error("Mismatched number of resumes("+pumpResumes.length+") and suspends("+pumpSuspends.length+") assuming suspended prior to history block!");
            } else if (!suspendedPrior && currentlySuspended && (pumpResumes.length !== (pumpSuspends.length-1))) {
                console.error("Mismatched number of resumes("+pumpResumes.length+") and suspends("+pumpSuspends.length+") assuming suspended past end of history block!");
            } else if (suspendedPrior && currentlySuspended && (pumpResumes.length !== pumpSuspends.length)) {
                console.error("Mismatched number of resumes("+pumpResumes.length+") and suspends("+pumpSuspends.length+") assuming suspended prior to and past end of history block!");
            }


            if (i < (pumpSuspends.size()-1)) {
                // truncate any extra suspends. if we had any extras
                // the error checks above would have issued a error log message
                pumpSuspends.splice(i+1, pumpSuspends.length-i-1);
            }
            */
            // Pick relevant events for processing and clean the data
            //Philoul: Note we already have clean data, just need to remove Carbs data and update duration
            for (i=0; i < pumpHistory.size(); i++) {
                NsTreatment current = pumpHistory.get(i);

                if (current.eventType != CareportalEvent.CARBCORRECTION)
                tempHistory.add(current);
            }

            // Check for overlapping events and adjust event lengths in case of overlap
            Collections.sort(tempHistory, (o1, o2) -> (int) (o1.date  - o2.date) );

            for (i=0; i+1 < tempHistory.size(); i++) {
                if (tempHistory.get(i).date + tempHistory.get(i).duration*60*1000 > tempHistory.get(i+1).date) {
                    tempHistory.get(i).duration = (int) (tempHistory.get(i+1).date - tempHistory.get(i).date)/60/1000;
                    // Delete AndroidAPS "Cancel TBR records" in which duration is not populated
                    if (tempHistory.get(i+1).isEndingEvent) {
                        tempHistory.remove(i+1);
                    }
                }
            }

            // Create an array of moments to slit the temps by
            // currently supports basal changes
            //Philoul I prefer split data each hour (24 hour per day)

            // iterate through the events and split at basal break points if needed
            for (NsTreatment t : tempHistory ) {
                // split each object according to
                if(t.eventType==CareportalEvent.TEMPBASAL && t.duration>0){
                    long minStartEvent = t.date/60/1000; //(in minutes)
                    long minEndEvent = minStartEvent + t.duration;
                    if(minStartEvent/60 != minEndEvent/60) {    //not the same hour so split event
                        NsTreatment temp = t;
                        temp.date = DateUtil.toTimeMinutesFromMidnight(t.date, (int) minEndEvent / 60);
                        temp.duration = t.duration - (int) (temp.date-t.date)/60/1000;
                        t.duration = (int) (temp.date-t.date)/60/1000;
                        tempHistory.add(temp);
                    }
                }
            }
            Collections.sort(tempHistory, (o1, o2) -> (int) (o1.date  - o2.date) );

            //Todo Philoul, see if we can get Pump Suspend event... Bloc disabled
            /*
            boolean suspend_zeros_iob = false;

            if (typeof profile_data.suspend_zeros_iob != 'undefined') {
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
            */
            // tempHistory = splitHistory;

            // iterate through the temp basals and create bolus events from temps that affect IOB

            Double tempBolusSize;

            for (i=0; i < tempHistory.size(); i++) {

                NsTreatment currentItem = tempHistory.get(i);

                if (currentItem.duration > 0) {
                    //todo Check here use of average basal of Categorize
                    //Double currentRate = profile_data.current_basal;

                    Profile currentItemProfile = profileFunction.getProfile(currentItem.date);
                    Double currentRate = currentItemProfile.getBasal(currentItem.date);

                    //Todo get profile_data.getSingleTargetsMgdl() or profile_data.getTargets() to improve calculation
                    //if (profile_data.min_bg != 'undefined' && typeof profile_data.max_bg != 'undefined') {
                    //    target_bg = (profile_data.min_bg + profile_data.max_bg) / 2;
                    //}
                    //if (profile_data.temptargetSet && target_bg > 110) {
                        //sensitivityRatio = 2/(2+(target_bg-100)/40);
                        //currentRate = profile_data.current_basal * sensitivityRatio;
                    //}
                    Double sensitivityRatio = 0d;
                    Profile profile = profile_data;
                    int normalTarget = 100; // evaluate high/low temptarget against 100, not scheduled basal (which might change)
                    //if ( profile.half_basal_exercise_target ) {
                    //    var halfBasalTarget = profile.half_basal_exercise_target;
                    //} else {
                        int halfBasalTarget = 160; // when temptarget is 160 mg/dL, run 50% basal (120 = 75%; 140 = 60%)
                    //}

                    //if ( profile.exercise_mode && profile.temptargetSet && target_bg >= normalTarget + 5 ) {
                        // w/ target 100, temp target 110 = .89, 120 = 0.8, 140 = 0.67, 160 = .57, and 200 = .44
                        // e.g.: Sensitivity ratio set to 0.8 based on temp target of 120; Adjusting basal from 1.65 to 1.35; ISF from 58.9 to 73.6
                    //    int c = halfBasalTarget - normalTarget;
                    //    sensitivityRatio = c/(c+target_bg-normalTarget);
                    //}
                    if ( sensitivityRatio > 0 ) {
                        currentRate = currentRate * sensitivityRatio;
                    }

                    Double netBasalRate = currentItem.absoluteRate - currentRate;
                    if (netBasalRate < 0) { tempBolusSize = -0.05d; }
                    else { tempBolusSize = 0.05d; }
                    Double netBasalAmount = Round.roundTo(netBasalRate*currentItem.duration*60,0.01);
                    int tempBolusCount = (int) Math.round(netBasalAmount/tempBolusSize);
                    int tempBolusSpacing = currentItem.duration / tempBolusCount;
                    for (j=0; j < tempBolusCount; j++) {
                        Treatment tempBolus = new Treatment();
                        tempBolus.insulin = tempBolusSize;
                        tempBolus.date = currentItem.date + j * tempBolusSpacing*60*1000;
                        tempBoluses.add(new NsTreatment(tempBolus));
                    }
                }
            }
            tempHistory.addAll(tempBoluses);

            Collections.sort(tempHistory, (o1, o2) -> (int) (o1.date  - o2.date) );

        return tempHistory;
    }
}

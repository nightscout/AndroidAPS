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
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.general.autotune.data.IobInputs;
import info.nightscout.androidaps.plugins.general.autotune.data.NsTreatment;
import info.nightscout.androidaps.plugins.general.autotune.data.TunedProfile;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSTreatment;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.Round;

public class AutotuneIob {
    @Inject ProfileFunction profileFunction;
    private final HasAndroidInjector injector;

    public AutotuneIob(){
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
        // pump Suspend and resume disabled for AndroidAPS


        int i;
        int j;

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

        for (i=0; i+1 < tempHistory.size(); i++) {
            NsTreatment currentItem = tempHistory.get(i);
            // split each object according to
            if(currentItem.eventType==CareportalEvent.TEMPBASAL && currentItem.duration>0){
                long minStartEvent = currentItem.date/60/1000; //(in minutes)
                long minEndEvent = minStartEvent + currentItem.duration;
                if(minStartEvent/60 != minEndEvent/60) {    //not the same hour so split event
                    NsTreatment temp = currentItem;
                    temp.date = DateUtil.toTimeMinutesFromMidnight(currentItem.date, (int) minEndEvent / 60);
                    temp.duration = currentItem.duration - (int) (temp.date-currentItem.date)/60/1000;
                    currentItem.duration = (int) (temp.date-currentItem.date)/60/1000;
                    tempHistory.add(temp);
                }
            }
        }
        Collections.sort(tempHistory, (o1, o2) -> (int) (o1.date  - o2.date) );

        //Bloc to split event according to Pump Suspend event removed

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
                TunedProfile profile = profile_data;
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

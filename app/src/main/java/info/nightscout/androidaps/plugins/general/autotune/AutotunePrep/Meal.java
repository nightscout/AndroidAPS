package info.nightscout.androidaps.plugins.general.autotune.AutotunePrep;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.general.autotune.data.Opts;

public class Meal {
    public static List<Treatment> generateMeal(Opts opts) {
        // todo check if cleaning of treatment data (double data at same time) is necessary here (treatment if already in Opts object)

        return opts.treatments;
    }

    /*
    function arrayHasElementWithSameTimestampAndProperty(array,t,propname) {
        for (var j=0; j < array.length; j++) {
            var element = array[j];
            if (element.timestamp === t && element[propname] !== undefined) return true;
            if ( element[propname] !== undefined ) {
                var eDate = new Date(element.timestamp);
                var tDate = new Date(t);
                var tMin = new Date(tDate.getTime() - 2000);
                var tMax = new Date(tDate.getTime() + 2000);
                //console.error(tDate, tMin, tMax);
                if (eDate > tMin && eDate < tMax) return true;
            }
        }
        return false;
    }

    // cleaning made with only what is available in AAPS
    function findMealInputs (inputs) {
        var pumpHistory = inputs.history;
        var profile_data = inputs.profile;
        var mealInputs = [];
        var duplicates = 0;

        for (i=0; i < pumpHistory.length; i++) {
            current = pumpHistory[i];
            if (current._type === "Bolus" && current.timestamp) {
                //console.log(pumpHistory[i]);
                temp = {};
                temp.timestamp = current.timestamp;
                temp.bolus = current.amount;

                if (!arrayHasElementWithSameTimestampAndProperty(mealInputs,current.timestamp,"bolus")) {
                    mealInputs.push(temp);
                } else {
                    duplicates += 1;
                }
            } else if ((current._type === "Meal Bolus" || current._type === "Correction Bolus" || current._type === "Bolus Wizard" || current._type === "Carb Correction") && current.created_at) {
                //imports carbs entered through Nightscout Care Portal
                //"Bolus Wizard" refers to the Nightscout Bolus Wizard, not the Medtronic Bolus Wizard
                temp = {};
                temp.timestamp = current.created_at;
                temp.carbs = current.carbs;
                temp.nsCarbs = current.carbs;
                // don't enter the treatment if there's another treatment with the same exact timestamp
                // to prevent duped carb entries from multiple sources
                if (!arrayHasElementWithSameTimestampAndProperty(mealInputs,current.created_at,"carbs")) {
                    mealInputs.push(temp);
                } else {
                    duplicates += 1;
                }
            } else if (current.carbs > 0) {
                temp = {};
                temp.carbs = current.carbs;
                temp.nsCarbs = current.carbs;
                temp.timestamp = current.created_at;
                temp.bolus = current.insulin;
                if (!arrayHasElementWithSameTimestampAndProperty(mealInputs,current.timestamp,"carbs")) {
                    mealInputs.push(temp);
                } else {
                    duplicates += 1;
                }
            }
        }

        //if (duplicates > 0) console.error("Removed duplicate bolus/carb entries:" + duplicates);

        return mealInputs;
    }
    */
}
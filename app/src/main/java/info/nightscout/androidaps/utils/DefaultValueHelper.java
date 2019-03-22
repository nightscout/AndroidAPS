package info.nightscout.androidaps.utils;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.R;

public class DefaultValueHelper {

    /**
     * returns the corresponding EatingSoon TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    public double getDefaultEatingSoonTT(String units) {
        return Constants.MMOL.equals(units) ? Constants.defaultEatingSoonTTmmol
                : Constants.defaultEatingSoonTTmgdl;
    }

    /**
     * returns the corresponding Activity TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    public double getDefaultActivityTT(String units) {
        return Constants.MMOL.equals(units) ? Constants.defaultActivityTTmmol
                : Constants.defaultActivityTTmgdl;
    }

    /**
     * returns the corresponding Hypo TempTarget based on the given units (MMOL / MGDL)
     *
     * @param units
     * @return
     */
    public double getDefaultHypoTT(String units) {
        return Constants.MMOL.equals(units) ? Constants.defaultHypoTTmmol
                : Constants.defaultHypoTTmgdl;
    }

    /**
     * returns the configured EatingSoon TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @param units
     * @return
     */
    public double determineEatingSoonTT(String units) {
        double value = SP.getDouble(R.string.key_eatingsoon_target, this.getDefaultEatingSoonTT(units));
        return value > 0 ? value : this.getDefaultEatingSoonTT(units);
    }

    public int determineEatingSoonTTDuration() {
        int value = SP.getInt(R.string.key_eatingsoon_duration, Constants.defaultEatingSoonTTDuration);
        return value > 0 ? value : Constants.defaultEatingSoonTTDuration;
    }


    /**
     * returns the configured Activity TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @param units
     * @return
     */
    public double determineActivityTT(String units) {
        double value = SP.getDouble(R.string.key_activity_target, this.getDefaultActivityTT(units));
        return value > 0 ? value : this.getDefaultActivityTT(units);
    }

    public int determineActivityTTDuration() {
        int value = SP.getInt(R.string.key_activity_duration, Constants.defaultActivityTTDuration);
        return value > 0 ? value : Constants.defaultActivityTTDuration;
    }

    /**
     * returns the configured Hypo TempTarget, if this is set to 0, the Default-Value is returned.
     *
     * @param units
     * @return
     */
    public double determineHypoTT(String units) {
        double value = SP.getDouble(R.string.key_hypo_target, this.getDefaultHypoTT(units));
        return value > 0 ? value : this.getDefaultHypoTT(units);
    }

    public int determineHypoTTDuration() {
        int value = SP.getInt(R.string.key_hypo_duration, Constants.defaultHypoTTDuration);
        return value > 0 ? value : Constants.defaultHypoTTDuration;
    }

}

package info.nightscout.androidaps.plugins.general.careportal;

/**
 * Created by mike on 05.11.2016.
 */

public class OptionsToShow {
    public int eventType;
    public int eventName;
    public boolean date;
    public boolean bg;
    public boolean insulin;
    public boolean carbs;
    public boolean prebolus;
    public boolean duration;
    public boolean percent;
    public boolean absolute;
    public boolean profile;
    public boolean split;
    public boolean tempTarget;

    public OptionsToShow(int eventType, int eventName) {
        this.eventType = eventType;
        this.eventName = eventName;
    }

    public OptionsToShow date() {
        date = true;
        return this;
    }

    public OptionsToShow bg() {
        bg = true;
        return this;
    }

    public OptionsToShow insulin() {
        insulin = true;
        return this;
    }

    public OptionsToShow carbs() {
        carbs = true;
        return this;
    }

    public OptionsToShow prebolus() {
        prebolus = true;
        return this;
    }

    public OptionsToShow duration() {
        duration = true;
        return this;
    }

    public OptionsToShow percent() {
        percent = true;
        return this;
    }

    public OptionsToShow absolute() {
        absolute = true;
        return this;
    }

    public OptionsToShow profile() {
        profile = true;
        return this;
    }

    public OptionsToShow split() {
        split = true;
        return this;
    }

    public OptionsToShow tempTarget() {
        tempTarget = true;
        return this;
    }
}

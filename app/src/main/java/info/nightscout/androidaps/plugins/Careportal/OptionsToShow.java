package info.nightscout.androidaps.plugins.Careportal;

/**
 * Created by mike on 05.11.2016.
 */

public class OptionsToShow {
    public int eventType;
    public int eventName;
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

    // perform direct actions
    public boolean executeProfileSwitch = false;
    public boolean executeTempTarget = false;

    public OptionsToShow(int eventType,
                         int eventName,
                         boolean bg,
                         boolean insulin,
                         boolean carbs,
                         boolean prebolus,
                         boolean duration,
                         boolean percent,
                         boolean absolute,
                         boolean profile,
                         boolean split,
                         boolean tempTarget) {
        this.eventType = eventType;
        this.eventName = eventName;
        this.bg = bg;
        this.insulin = insulin;
        this.carbs = carbs;
        this.prebolus = prebolus;
        this.duration = duration;
        this.percent = percent;
        this.absolute = absolute;
        this.profile = profile;
        this.split = split;
        this.tempTarget = tempTarget;
    }
}

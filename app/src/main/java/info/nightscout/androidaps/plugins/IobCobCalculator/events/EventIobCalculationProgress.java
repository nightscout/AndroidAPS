package info.nightscout.androidaps.plugins.IobCobCalculator.events;

import info.nightscout.androidaps.events.Event;

public class EventIobCalculationProgress extends Event {
    public String progress;

    public EventIobCalculationProgress(String progress) {
        this.progress = progress;
    }
}

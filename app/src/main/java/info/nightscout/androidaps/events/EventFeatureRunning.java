package info.nightscout.androidaps.events;

/**
 * Created by jamorham on 07/02/2018.
 *
 * Event to indicate that an app feature is being used, for example bolus wizard being opened
 *
 * The purpose this has been created for is to enable opportunistic connection to the pump
 * so that it is already connected before the user wishes to enact a pump function
 *
 */

public class EventFeatureRunning extends Event {

    private Feature feature = Feature.UNKNOWN;

    public EventFeatureRunning() {
    }

    public EventFeatureRunning(Feature feature) {
        this.feature = feature;
    }

    public Feature getFeature() {
        return feature;
    }

    public enum Feature {
        UNKNOWN,
        MAIN,
        WIZARD,

        JUST_ADD_MORE_HERE
    }

}

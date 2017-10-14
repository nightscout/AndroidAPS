package info.nightscout.androidaps.events;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/** Base class for all events posted on the event bus. */
public abstract class Event {
    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}

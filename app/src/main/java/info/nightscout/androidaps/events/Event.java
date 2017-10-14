package info.nightscout.androidaps.events;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/** Base class for all events posted on the event bus. */
public abstract class Event {
    static {
        ReflectionToStringBuilder.setDefaultStyle(ToStringStyle.SHORT_PREFIX_STYLE);
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}

package info.nightscout.androidaps.events

import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

/** Base class for all events posted on the event bus.  */
abstract class Event {

    override fun toString(): String {
        return ReflectionToStringBuilder.toString(this)
    }

    companion object {
        init {
            ReflectionToStringBuilder.setDefaultStyle(ToStringStyle.SHORT_PREFIX_STYLE)
        }
    }
}

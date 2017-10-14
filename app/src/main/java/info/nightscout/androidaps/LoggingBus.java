package info.nightscout.androidaps;

import com.squareup.otto.Bus;
import com.squareup.otto.DeadEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.events.Event;

class LoggingBus extends Bus {
    private static Logger log = LoggerFactory.getLogger(LoggingBus.class);

    private final Bus delegate;

    public LoggingBus(Bus bus) {
        delegate = bus;
    }

    @Override
    public void post(Object event) {
        if (event instanceof DeadEvent) {
            log.debug("Event has no receiver:" + ((DeadEvent) event).event + ", source: " + ((DeadEvent) event).source);
            return;
        }

        if (!(event instanceof Event)) {
            log.error("Posted event not an event class: " + event.getClass());
        }

        log.debug("Event posted: " + event);
        delegate.post(event);
    }
}

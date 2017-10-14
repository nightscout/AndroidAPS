package info.nightscout.androidaps;

import com.squareup.otto.Bus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LoggingBus extends Bus {
    private static Logger log = LoggerFactory.getLogger(LoggingBus.class);

    private final Bus delegate;

    public LoggingBus(Bus bus) {
        delegate = bus;
    }

    @Override
    public void post(Object event) {
        log.debug("Event posted: " + event);
        super.post(event);
    }
}

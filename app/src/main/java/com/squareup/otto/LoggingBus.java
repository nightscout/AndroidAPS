package com.squareup.otto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.logging.L;

/**
 * Logs events has they're being posted to and dispatched from the event bus.
 * <p>
 * A summary of event-receiver calls that occurred so far is logged
 * after 10s (after startup) and then again every 60s.
 */
public class LoggingBus extends Bus {
    private static Logger log = LoggerFactory.getLogger(L.EVENTS);

    private static long everyMinute = System.currentTimeMillis() + 10 * 1000;
    private Map<String, Set<String>> event2Receiver = new HashMap<>();

    public LoggingBus(ThreadEnforcer enforcer) {
        super(enforcer);
    }

    @Override
    public void post(Object event) {
        if (event instanceof DeadEvent) {
            log.debug("Event has no receiver: " + ((DeadEvent) event).event + ", source: " + ((DeadEvent) event).source);
            return;
        }

        if (!(event instanceof Event)) {
            log.error("Posted event not an event class: " + event.getClass());
        }

        log.debug("<<< " + event);
        try {
            StackTraceElement caller = new Throwable().getStackTrace()[1];
            String className = caller.getClassName();
            className = className.substring(className.lastIndexOf(".") + 1);
            log.debug("    source: " + className + "." + caller.getMethodName() + ":" + caller.getLineNumber());
        } catch (RuntimeException e) {
            log.debug("    source: <unknown>");
        }

        try {
            super.post(event);
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    protected void dispatch(Object event, EventHandler wrapper) {
        try {
            log.debug(">>> " + event);
            Field methodField = wrapper.getClass().getDeclaredField("method");
            methodField.setAccessible(true);
            Method targetMethod = (Method) methodField.get(wrapper);
            String className = targetMethod.getDeclaringClass().getSimpleName();
            String methodName = targetMethod.getName();
            String receiverMethod = className + "." + methodName;
            log.debug("    receiver: " + receiverMethod);

            String key = event.getClass().getSimpleName();
            if (!event2Receiver.containsKey(key)) event2Receiver.put(key, new HashSet<String>());
            event2Receiver.get(key).add(receiverMethod);
        } catch (ReflectiveOperationException e) {
            log.debug("    receiver: <unknown>");
        }

        try {
            if (everyMinute < System.currentTimeMillis()) {
                log.debug("***************** Event -> receiver pairings seen so far ****************");
                for (Map.Entry<String, Set<String>> stringSetEntry : event2Receiver.entrySet()) {
                    log.debug("  " + stringSetEntry.getKey());
                    for (String s : stringSetEntry.getValue()) {
                        log.debug("    -> " + s);
                    }
                }
                log.debug("*************************************************************************");
                everyMinute = System.currentTimeMillis() + 60 * 1000;
            }
        } catch (ConcurrentModificationException ignored) {
        }

        super.dispatch(event, wrapper);
    }
}

package com.squareup.otto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import info.nightscout.androidaps.events.Event;

public class LoggingBus extends Bus {
    private static Logger log = LoggerFactory.getLogger(LoggingBus.class);

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

        super.post(event);
    }

    @Override
    protected void dispatch(Object event, EventHandler wrapper) {
        try {
            log.debug(">>> " + event);
            Field methodField = wrapper.getClass().getDeclaredField("method");
            methodField.setAccessible(true);
            Method targcetMethod = (Method) methodField.get(wrapper);
            String className = targcetMethod.getDeclaringClass().getSimpleName();
            String methodName = targcetMethod.getName();
            log.debug("    receiver: " + className + "." + methodName);
        } catch (ReflectiveOperationException e) {
            log.debug("    receiver: <unknown>");
        }
        super.dispatch(event, wrapper);
    }
}

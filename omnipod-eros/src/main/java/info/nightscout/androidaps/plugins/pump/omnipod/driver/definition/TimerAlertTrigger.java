package info.nightscout.androidaps.plugins.pump.omnipod.driver.definition;

import org.joda.time.Duration;

public class TimerAlertTrigger extends AlertTrigger<Duration> {
    public TimerAlertTrigger(Duration value) {
        super(value);
    }
}
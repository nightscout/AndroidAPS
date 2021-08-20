package info.nightscout.androidaps.plugins.pump.omnipod.util;

import org.joda.time.Duration;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodStorageKeys;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class OmnipodAlertUtil {
    private final SP sp;

    @Inject
    public OmnipodAlertUtil(SP sp) {
        this.sp = sp;
    }

    public Duration getExpirationReminderTimeBeforeShutdown() {
        boolean expirationAlertEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.EXPIRATION_REMINDER_ENABLED, true);
        return expirationAlertEnabled ? Duration.standardHours(sp.getInt(OmnipodStorageKeys.Preferences.EXPIRATION_REMINDER_HOURS_BEFORE_SHUTDOWN, 9)) : null;
    }

    public Integer getLowReservoirAlertUnits() {
        boolean lowReservoirAlertEnabled = sp.getBoolean(OmnipodStorageKeys.Preferences.LOW_RESERVOIR_ALERT_ENABLED, true);
        return lowReservoirAlertEnabled ? sp.getInt(OmnipodStorageKeys.Preferences.LOW_RESERVOIR_ALERT_UNITS, OmnipodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD) : null;
    }
}

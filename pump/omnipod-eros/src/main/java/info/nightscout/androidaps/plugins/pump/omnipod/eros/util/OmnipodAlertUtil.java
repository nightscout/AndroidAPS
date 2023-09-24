package info.nightscout.androidaps.plugins.pump.omnipod.eros.util;

import org.joda.time.Duration;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.sharedPreferences.SP;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.definition.OmnipodErosStorageKeys;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants;

@Singleton
public class OmnipodAlertUtil {
    private final SP sp;

    @Inject
    public OmnipodAlertUtil(SP sp) {
        this.sp = sp;
    }

    public Duration getExpirationReminderTimeBeforeShutdown() {
        boolean expirationAlertEnabled = sp.getBoolean(OmnipodErosStorageKeys.Preferences.EXPIRATION_REMINDER_ENABLED, true);
        return expirationAlertEnabled ? Duration.standardHours(sp.getInt(OmnipodErosStorageKeys.Preferences.EXPIRATION_REMINDER_HOURS_BEFORE_SHUTDOWN, 9)) : null;
    }

    public Integer getLowReservoirAlertUnits() {
        boolean lowReservoirAlertEnabled = sp.getBoolean(OmnipodErosStorageKeys.Preferences.LOW_RESERVOIR_ALERT_ENABLED, true);
        return lowReservoirAlertEnabled ? sp.getInt(OmnipodErosStorageKeys.Preferences.LOW_RESERVOIR_ALERT_UNITS, OmnipodConstants.DEFAULT_MAX_RESERVOIR_ALERT_THRESHOLD) : null;
    }
}

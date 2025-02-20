package app.aaps.pump.omnipod.eros.util;

import androidx.annotation.Nullable;

import org.joda.time.Duration;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.keys.interfaces.Preferences;
import app.aaps.pump.omnipod.common.keys.OmnipodBooleanPreferenceKey;
import app.aaps.pump.omnipod.common.keys.OmnipodIntPreferenceKey;

@Singleton
public class OmnipodAlertUtil {
    private final Preferences preferences;

    @Inject
    public OmnipodAlertUtil(Preferences preferences) {
        this.preferences = preferences;
    }

    @Nullable public Duration getExpirationReminderTimeBeforeShutdown() {
        boolean expirationAlertEnabled = preferences.get(OmnipodBooleanPreferenceKey.ExpirationReminder);
        return expirationAlertEnabled ? Duration.standardHours(preferences.get(OmnipodIntPreferenceKey.ExpirationReminderHours)) : null;
    }

    @Nullable public Integer getLowReservoirAlertUnits() {
        boolean lowReservoirAlertEnabled = preferences.get(OmnipodBooleanPreferenceKey.LowReservoirAlert);
        return lowReservoirAlertEnabled ? preferences.get(OmnipodIntPreferenceKey.LowReservoirAlertUnits) : null;
    }
}

package app.aaps.pump.omnipod.eros.driver.communication.action.service;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.aaps.pump.omnipod.eros.driver.definition.AlertConfiguration;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSlot;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.driver.util.AlertConfigurationUtil;

public final class ExpirationReminderBuilder {
    private final Map<AlertSlot, AlertConfiguration> alerts = new HashMap<>();
    private final DateTime endOfServiceTime;

    public ExpirationReminderBuilder(ErosPodStateManager podStateManager) {
        this.endOfServiceTime = podStateManager.getActivatedAt().plus(OmnipodConstants.SERVICE_DURATION);
    }

    public ExpirationReminderBuilder defaults() {
        DateTime shutdownImminentAlarmTime = endOfServiceTime.minus(OmnipodConstants.END_OF_SERVICE_IMMINENT_WINDOW);

        if (DateTime.now().isBefore(shutdownImminentAlarmTime)) {
            Duration timeUntilShutdownImminentAlarm = new Duration(DateTime.now(),
                    shutdownImminentAlarmTime);
            AlertConfiguration shutdownImminentAlertConfiguration = AlertConfigurationUtil.createShutdownImminentAlertConfiguration(
                    timeUntilShutdownImminentAlarm);
            alerts.put(shutdownImminentAlertConfiguration.getAlertSlot(), shutdownImminentAlertConfiguration);
        }

        AlertConfiguration autoOffAlertConfiguration = AlertConfigurationUtil.createAutoOffAlertConfiguration(
                false, Duration.ZERO);
        alerts.put(autoOffAlertConfiguration.getAlertSlot(), autoOffAlertConfiguration);

        return this;
    }

    public ExpirationReminderBuilder expirationAdvisory(boolean active, Duration timeBeforeShutdown) {
        DateTime expirationAdvisoryAlarmTime = endOfServiceTime.minus(timeBeforeShutdown);

        Duration timeUntilExpirationAdvisoryAlarm = DateTime.now().isBefore(expirationAdvisoryAlarmTime) ? new Duration(DateTime.now(),
                expirationAdvisoryAlarmTime) : Duration.ZERO;
        AlertConfiguration expirationAdvisoryAlertConfiguration = AlertConfigurationUtil.createExpirationAdvisoryAlertConfiguration(active,
                timeUntilExpirationAdvisoryAlarm, timeBeforeShutdown);
        alerts.put(expirationAdvisoryAlertConfiguration.getAlertSlot(), expirationAdvisoryAlertConfiguration);
        return this;
    }

    public ExpirationReminderBuilder lowReservoir(boolean active, int units) {
        AlertConfiguration lowReservoirAlertConfiguration = AlertConfigurationUtil.createLowReservoirAlertConfiguration(active, (double) units);
        alerts.put(lowReservoirAlertConfiguration.getAlertSlot(), lowReservoirAlertConfiguration);
        return this;
    }

    public List<AlertConfiguration> build() {
        return new ArrayList<>(alerts.values());
    }
}

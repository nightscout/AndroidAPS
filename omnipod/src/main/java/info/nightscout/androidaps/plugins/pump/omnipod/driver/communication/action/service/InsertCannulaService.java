package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.service;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.BolusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.ConfigureAlertsAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.SetBasalScheduleAction;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.util.AlertConfigurationUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.OmnipodRileyLinkCommunicationManager;

public class InsertCannulaService {
    public StatusResponse programInitialBasalSchedule(OmnipodRileyLinkCommunicationManager communicationService,
                                                      PodStateManager podStateManager, BasalSchedule basalSchedule) {
        return communicationService.executeAction(new SetBasalScheduleAction(podStateManager, basalSchedule,
                true, podStateManager.getScheduleOffset(), false));
    }

    public StatusResponse executeExpirationRemindersAlertCommand(OmnipodRileyLinkCommunicationManager communicationService,
                                                                 PodStateManager podStateManager) {
        AlertConfiguration lowReservoirAlertConfiguration = AlertConfigurationUtil.createLowReservoirAlertConfiguration(OmnipodConstants.LOW_RESERVOIR_ALERT);

        DateTime endOfServiceTime = podStateManager.getActivatedAt().plus(OmnipodConstants.SERVICE_DURATION);

        Duration timeUntilExpirationAdvisoryAlarm = new Duration(DateTime.now(),
                endOfServiceTime.minus(OmnipodConstants.EXPIRATION_ADVISORY_WINDOW));
        Duration timeUntilShutdownImminentAlarm = new Duration(DateTime.now(),
                endOfServiceTime.minus(OmnipodConstants.END_OF_SERVICE_IMMINENT_WINDOW));

        AlertConfiguration expirationAdvisoryAlertConfiguration = AlertConfigurationUtil.createExpirationAdvisoryAlertConfiguration(
                timeUntilExpirationAdvisoryAlarm, OmnipodConstants.EXPIRATION_ADVISORY_WINDOW);
        AlertConfiguration shutdownImminentAlertConfiguration = AlertConfigurationUtil.createShutdownImminentAlertConfiguration(
                timeUntilShutdownImminentAlarm);
        AlertConfiguration autoOffAlertConfiguration = AlertConfigurationUtil.createAutoOffAlertConfiguration(
                false, Duration.ZERO);

        List<AlertConfiguration> alertConfigurations = Arrays.asList( //
                lowReservoirAlertConfiguration, //
                expirationAdvisoryAlertConfiguration, //
                shutdownImminentAlertConfiguration, //
                autoOffAlertConfiguration //
        );

        return communicationService.executeAction(new ConfigureAlertsAction(podStateManager, alertConfigurations));
    }

    public StatusResponse executeInsertionBolusCommand(OmnipodRileyLinkCommunicationManager communicationService, PodStateManager podStateManager) {
        return communicationService.executeAction(new BolusAction(podStateManager, OmnipodConstants.POD_CANNULA_INSERTION_BOLUS_UNITS,
                Duration.standardSeconds(1), false, false));
    }
}

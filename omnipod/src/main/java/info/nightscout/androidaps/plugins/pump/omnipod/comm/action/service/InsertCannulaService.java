package info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.BolusAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.ConfigureAlertsAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.SetBasalScheduleAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertConfigurationFactory;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class InsertCannulaService {
    public StatusResponse programInitialBasalSchedule(OmnipodCommunicationManager communicationService,
                                                      PodStateManager podStateManager, BasalSchedule basalSchedule) {
        return communicationService.executeAction(new SetBasalScheduleAction(podStateManager, basalSchedule,
                true, podStateManager.getScheduleOffset(), false));
    }

    public StatusResponse executeExpirationRemindersAlertCommand(OmnipodCommunicationManager communicationService,
                                                                 PodStateManager podStateManager) {
        AlertConfiguration lowReservoirAlertConfiguration = AlertConfigurationFactory.createLowReservoirAlertConfiguration(OmnipodConst.LOW_RESERVOIR_ALERT);

        DateTime endOfServiceTime = podStateManager.getActivatedAt().plus(OmnipodConst.SERVICE_DURATION);

        Duration timeUntilExpirationAdvisoryAlarm = new Duration(DateTime.now(),
                endOfServiceTime.minus(OmnipodConst.EXPIRATION_ADVISORY_WINDOW));
        Duration timeUntilShutdownImminentAlarm = new Duration(DateTime.now(),
                endOfServiceTime.minus(OmnipodConst.END_OF_SERVICE_IMMINENT_WINDOW));

        AlertConfiguration expirationAdvisoryAlertConfiguration = AlertConfigurationFactory.createExpirationAdvisoryAlertConfiguration(
                timeUntilExpirationAdvisoryAlarm, OmnipodConst.EXPIRATION_ADVISORY_WINDOW);
        AlertConfiguration shutdownImminentAlertConfiguration = AlertConfigurationFactory.createShutdownImminentAlertConfiguration(
                timeUntilShutdownImminentAlarm);
        AlertConfiguration autoOffAlertConfiguration = AlertConfigurationFactory.createAutoOffAlertConfiguration(
                false, Duration.ZERO);

        List<AlertConfiguration> alertConfigurations = Arrays.asList( //
                lowReservoirAlertConfiguration, //
                expirationAdvisoryAlertConfiguration, //
                shutdownImminentAlertConfiguration, //
                autoOffAlertConfiguration //
        );

        return communicationService.executeAction(new ConfigureAlertsAction(podStateManager, alertConfigurations));
    }

    public StatusResponse executeInsertionBolusCommand(OmnipodCommunicationManager communicationService, PodStateManager podStateManager) {
        return communicationService.executeAction(new BolusAction(podStateManager, OmnipodConst.POD_CANNULA_INSERTION_BOLUS_UNITS,
                Duration.standardSeconds(1), false, false));
    }
}

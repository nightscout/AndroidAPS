package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import org.joda.time.Duration;

import java.util.List;
import java.util.Optional;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.service.ExpirationReminderBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class InsertCannulaAction implements OmnipodAction<StatusResponse> {

    private final PodStateManager podStateManager;
    private final BasalSchedule initialBasalSchedule;
    private final Duration expirationReminderTimeBeforeShutdown;
    private final Integer lowReservoirAlertUnits;

    public InsertCannulaAction(PodStateManager podStateManager, BasalSchedule initialBasalSchedule,
                               Duration expirationReminderTimeBeforeShutdown, Integer lowReservoirAlertUnits) {
        if (podStateManager == null) {
            throw new ActionInitializationException("Pod state manager cannot be null");
        }
        if (initialBasalSchedule == null) {
            throw new ActionInitializationException("Initial basal schedule cannot be null");
        }
        this.podStateManager = podStateManager;
        this.initialBasalSchedule = initialBasalSchedule;
        this.expirationReminderTimeBeforeShutdown = expirationReminderTimeBeforeShutdown;
        this.lowReservoirAlertUnits = lowReservoirAlertUnits;
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        if (!podStateManager.isPodInitialized() || podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.PRIMING_COMPLETED)) {
            throw new IllegalPodProgressException(PodProgressStatus.PRIMING_COMPLETED, podStateManager.isPodInitialized() ? podStateManager.getPodProgressStatus() : null);
        }

        if (podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.BASAL_INITIALIZED)) {
            podStateManager.setBasalSchedule(initialBasalSchedule);
            communicationService.executeAction(new SetBasalScheduleAction(podStateManager, initialBasalSchedule,
                    true, podStateManager.getScheduleOffset(), false));
        }

        if (podStateManager.getPodProgressStatus().isBefore(PodProgressStatus.INSERTING_CANNULA)) {
            communicationService.executeAction(new ConfigureAlertsAction(podStateManager, buildAlertConfigurations()));

            podStateManager.setExpirationAlertTimeBeforeShutdown(expirationReminderTimeBeforeShutdown);
            podStateManager.setLowReservoirAlertUnits(lowReservoirAlertUnits);

            return communicationService.executeAction(new BolusAction(podStateManager, OmnipodConstants.POD_CANNULA_INSERTION_BOLUS_UNITS,
                    Duration.standardSeconds(1), false, false));
        } else if (podStateManager.getPodProgressStatus().equals(PodProgressStatus.INSERTING_CANNULA)) {
            // Check status
            return communicationService.executeAction(new GetStatusAction(podStateManager));
        } else {
            throw new IllegalPodProgressException(null, podStateManager.getPodProgressStatus());
        }
    }

    private List<AlertConfiguration> buildAlertConfigurations() {
        ExpirationReminderBuilder builder = new ExpirationReminderBuilder(podStateManager).defaults();
        builder.expirationAdvisory(expirationReminderTimeBeforeShutdown != null,
                Optional.ofNullable(expirationReminderTimeBeforeShutdown).orElse(Duration.ZERO));
        builder.lowReservoir(lowReservoirAlertUnits != null, Optional.ofNullable(lowReservoirAlertUnits).orElse(0));
        return builder.build();
    }
}

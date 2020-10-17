package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import org.joda.time.Duration;

import java.util.List;
import java.util.Optional;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.service.ExpirationReminderBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalSetupProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class InsertCannulaAction implements OmnipodAction<Void> {

    private final PodStateManager podStateManager;
    private final BasalSchedule initialBasalSchedule;
    private final Duration expirationReminderTimeBeforeShutdown;
    private final Integer lowReservoirAlertUnits;

    public InsertCannulaAction(PodStateManager podStateManager, BasalSchedule initialBasalSchedule,
                               Duration expirationReminderTimeBeforeShutdown, Integer lowReservoirAlertUnits) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        if (initialBasalSchedule == null) {
            throw new IllegalArgumentException("Initial basal schedule cannot be null");
        }
        this.podStateManager = podStateManager;
        this.initialBasalSchedule = initialBasalSchedule;
        this.expirationReminderTimeBeforeShutdown = expirationReminderTimeBeforeShutdown;
        this.lowReservoirAlertUnits = lowReservoirAlertUnits;
    }

    @Override
    public Void execute(OmnipodRileyLinkCommunicationManager communicationService) {
        if (podStateManager.getSetupProgress().isBefore(SetupProgress.PRIMING_COMPLETED)) {
            throw new IllegalSetupProgressException(SetupProgress.PRIMING_COMPLETED, podStateManager.getSetupProgress());
        }

        if (podStateManager.getSetupProgress().needsBasalSchedule()) {
            podStateManager.setBasalSchedule(initialBasalSchedule);
            communicationService.executeAction(new SetBasalScheduleAction(podStateManager, initialBasalSchedule,
                    true, podStateManager.getScheduleOffset(), false));
            podStateManager.setSetupProgress(SetupProgress.BASAL_INITIALIZED);
        }

        if (podStateManager.getSetupProgress().needsExpirationReminders()) {
            communicationService.executeAction(new ConfigureAlertsAction(podStateManager, buildAlertConfigurations()));

            podStateManager.setExpirationAlertTimeBeforeShutdown(expirationReminderTimeBeforeShutdown);
            podStateManager.setLowReservoirAlertUnits(lowReservoirAlertUnits);
            podStateManager.setSetupProgress(SetupProgress.EXPIRATION_REMINDERS_SET);
        }

        if (podStateManager.getSetupProgress().needsCannulaInsertion()) {
            communicationService.executeAction(new BolusAction(podStateManager, OmnipodConstants.POD_CANNULA_INSERTION_BOLUS_UNITS,
                    Duration.standardSeconds(1), false, false));
            podStateManager.setSetupProgress(SetupProgress.INSERTING_CANNULA);
        }

        return null;
    }

    private List<AlertConfiguration> buildAlertConfigurations() {
        ExpirationReminderBuilder builder = new ExpirationReminderBuilder(podStateManager).defaults();
        builder.expirationAdvisory(expirationReminderTimeBeforeShutdown != null,
                Optional.ofNullable(expirationReminderTimeBeforeShutdown).orElse(Duration.ZERO));
        builder.lowReservoir(lowReservoirAlertUnits != null, Optional.ofNullable(lowReservoirAlertUnits).orElse(0));
        return builder.build();
    }
}

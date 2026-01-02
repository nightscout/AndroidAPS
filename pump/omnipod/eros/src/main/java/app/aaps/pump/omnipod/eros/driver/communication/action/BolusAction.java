package app.aaps.pump.omnipod.eros.driver.communication.action;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import java.util.Arrays;

import app.aaps.pump.omnipod.eros.driver.communication.message.OmnipodMessage;
import app.aaps.pump.omnipod.eros.driver.communication.message.command.BolusExtraCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.command.SetInsulinScheduleCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import app.aaps.pump.omnipod.eros.driver.definition.schedule.BolusDeliverySchedule;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class BolusAction implements OmnipodAction<StatusResponse> {
    private final ErosPodStateManager podStateManager;
    private final double units;
    @NonNull private final Duration timeBetweenPulses;
    private final boolean acknowledgementBeep;
    private final boolean completionBeep;

    public BolusAction(@NonNull ErosPodStateManager podStateManager, double units, Duration timeBetweenPulses,
                       boolean acknowledgementBeep, boolean completionBeep) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        if (timeBetweenPulses == null) {
            throw new IllegalArgumentException("Time between pulses cannot be null");
        }
        this.podStateManager = podStateManager;
        this.units = units;
        this.timeBetweenPulses = timeBetweenPulses;
        this.acknowledgementBeep = acknowledgementBeep;
        this.completionBeep = completionBeep;
    }

    public BolusAction(@NonNull ErosPodStateManager podStateManager, double units, boolean acknowledgementBeep, boolean completionBeep) {
        this(podStateManager, units, Duration.standardSeconds(2), acknowledgementBeep, completionBeep);
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        BolusDeliverySchedule bolusDeliverySchedule = new BolusDeliverySchedule(units, timeBetweenPulses);
        SetInsulinScheduleCommand setInsulinScheduleCommand = new SetInsulinScheduleCommand(
                podStateManager.getCurrentNonce(), bolusDeliverySchedule);
        BolusExtraCommand bolusExtraCommand = new BolusExtraCommand(units, timeBetweenPulses,
                acknowledgementBeep, completionBeep);
        OmnipodMessage bolusMessage = new OmnipodMessage(podStateManager.getAddress(),
                Arrays.asList(setInsulinScheduleCommand, bolusExtraCommand), podStateManager.getMessageNumber());
        return communicationService.exchangeMessages(StatusResponse.class, podStateManager, bolusMessage);
    }
}

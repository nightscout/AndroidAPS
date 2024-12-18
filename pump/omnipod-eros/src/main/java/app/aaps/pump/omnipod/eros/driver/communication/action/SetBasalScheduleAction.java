package app.aaps.pump.omnipod.eros.driver.communication.action;

import org.joda.time.Duration;

import java.util.Arrays;

import app.aaps.pump.omnipod.eros.driver.communication.message.OmnipodMessage;
import app.aaps.pump.omnipod.eros.driver.communication.message.command.BasalScheduleExtraCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.command.SetInsulinScheduleCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import app.aaps.pump.omnipod.eros.driver.definition.schedule.BasalSchedule;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class SetBasalScheduleAction implements OmnipodAction<StatusResponse> {
    private final ErosPodStateManager podStateManager;
    private final BasalSchedule basalSchedule;
    private final boolean confidenceReminder;
    private final Duration scheduleOffset;
    private final boolean acknowledgementBeep;

    public SetBasalScheduleAction(ErosPodStateManager podStateManager, BasalSchedule basalSchedule,
                                  boolean confidenceReminder, Duration scheduleOffset, boolean acknowledgementBeep) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        if (basalSchedule == null) {
            throw new IllegalArgumentException("Basal schedule cannot be null");
        }
        if (scheduleOffset == null) {
            throw new IllegalArgumentException("Schedule offset cannot be null");
        }
        this.podStateManager = podStateManager;
        this.basalSchedule = basalSchedule;
        this.confidenceReminder = confidenceReminder;
        this.scheduleOffset = scheduleOffset;
        this.acknowledgementBeep = acknowledgementBeep;
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        SetInsulinScheduleCommand setBasal = new SetInsulinScheduleCommand(podStateManager.getCurrentNonce(), basalSchedule, scheduleOffset);
        BasalScheduleExtraCommand extraCommand = new BasalScheduleExtraCommand(basalSchedule, scheduleOffset,
                acknowledgementBeep, confidenceReminder, Duration.ZERO);
        OmnipodMessage basalMessage = new OmnipodMessage(podStateManager.getAddress(), Arrays.asList(setBasal, extraCommand),
                podStateManager.getMessageNumber());

        return communicationService.exchangeMessages(StatusResponse.class, podStateManager, basalMessage);
    }
}

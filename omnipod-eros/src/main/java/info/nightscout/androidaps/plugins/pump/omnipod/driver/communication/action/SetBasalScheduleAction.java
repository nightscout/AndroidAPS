package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import org.joda.time.Duration;

import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.BasalScheduleExtraCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.SetInsulinScheduleCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class SetBasalScheduleAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;
    private final BasalSchedule basalSchedule;
    private final boolean confidenceReminder;
    private final Duration scheduleOffset;
    private final boolean acknowledgementBeep;

    public SetBasalScheduleAction(PodStateManager podStateManager, BasalSchedule basalSchedule,
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

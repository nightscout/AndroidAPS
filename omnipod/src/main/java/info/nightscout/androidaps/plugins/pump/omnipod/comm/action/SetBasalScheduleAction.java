package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.Duration;

import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.BasalScheduleExtraCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.SetInsulinScheduleCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;

public class SetBasalScheduleAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;
    private final BasalSchedule basalSchedule;
    private final boolean confidenceReminder;
    private final Duration scheduleOffset;
    private final boolean acknowledgementBeep;

    public SetBasalScheduleAction(PodStateManager podStateManager, BasalSchedule basalSchedule,
                                  boolean confidenceReminder, Duration scheduleOffset, boolean acknowledgementBeep) {
        if (podStateManager == null) {
            throw new ActionInitializationException("Pod state manager cannot be null");
        }
        if (basalSchedule == null) {
            throw new ActionInitializationException("Basal schedule cannot be null");
        }
        if (scheduleOffset == null) {
            throw new ActionInitializationException("Schedule offset cannot be null");
        }
        this.podStateManager = podStateManager;
        this.basalSchedule = basalSchedule;
        this.confidenceReminder = confidenceReminder;
        this.scheduleOffset = scheduleOffset;
        this.acknowledgementBeep = acknowledgementBeep;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        SetInsulinScheduleCommand setBasal = new SetInsulinScheduleCommand(podStateManager.getCurrentNonce(), basalSchedule, scheduleOffset);
        BasalScheduleExtraCommand extraCommand = new BasalScheduleExtraCommand(basalSchedule, scheduleOffset,
                acknowledgementBeep, confidenceReminder, Duration.ZERO);
        OmnipodMessage basalMessage = new OmnipodMessage(podStateManager.getAddress(), Arrays.asList(setBasal, extraCommand),
                podStateManager.getMessageNumber());

        StatusResponse statusResponse = communicationService.exchangeMessages(StatusResponse.class, podStateManager, basalMessage);
        podStateManager.setBasalSchedule(basalSchedule);
        return statusResponse;
    }
}

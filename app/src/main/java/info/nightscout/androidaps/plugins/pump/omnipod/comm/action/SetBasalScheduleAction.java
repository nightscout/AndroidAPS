package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.Duration;

import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.BasalScheduleExtraCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.SetInsulinScheduleCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;

public class SetBasalScheduleAction implements OmnipodAction<StatusResponse> {
    private final PodSessionState podState;
    private final BasalSchedule basalSchedule;
    private final boolean confidenceReminder;
    private final Duration scheduleOffset;
    private final boolean acknowledgementBeep;

    public SetBasalScheduleAction(PodSessionState podState, BasalSchedule basalSchedule,
                                  boolean confidenceReminder, Duration scheduleOffset, boolean acknowledgementBeep) {
        if (podState == null) {
            throw new ActionInitializationException("Pod state cannot be null");
        }
        if (basalSchedule == null) {
            throw new ActionInitializationException("Basal schedule cannot be null");
        }
        if (scheduleOffset == null) {
            throw new ActionInitializationException("Schedule offset cannot be null");
        }
        this.podState = podState;
        this.basalSchedule = basalSchedule;
        this.confidenceReminder = confidenceReminder;
        this.scheduleOffset = scheduleOffset;
        this.acknowledgementBeep = acknowledgementBeep;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        SetInsulinScheduleCommand setBasal = new SetInsulinScheduleCommand(podState.getCurrentNonce(), basalSchedule, scheduleOffset);
        BasalScheduleExtraCommand extraCommand = new BasalScheduleExtraCommand(basalSchedule, scheduleOffset,
                acknowledgementBeep, confidenceReminder, Duration.ZERO);
        OmnipodMessage basalMessage = new OmnipodMessage(podState.getAddress(), Arrays.asList(setBasal, extraCommand),
                podState.getMessageNumber());

        StatusResponse statusResponse = communicationService.exchangeMessages(StatusResponse.class, podState, basalMessage);
        podState.setBasalSchedule(basalSchedule);
        return statusResponse;
    }
}

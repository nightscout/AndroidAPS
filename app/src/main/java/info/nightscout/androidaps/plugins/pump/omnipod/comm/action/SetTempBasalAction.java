package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.Duration;

import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.SetInsulinScheduleCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.TempBasalExtraCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;

public class SetTempBasalAction implements OmnipodAction<StatusResponse> {
    private final PodSessionState podState;
    private final double rate;
    private final Duration duration;
    private final boolean acknowledgementBeep;
    private final boolean completionBeep;

    public SetTempBasalAction(PodSessionState podState, double rate, Duration duration,
                              boolean acknowledgementBeep, boolean completionBeep) {
        if (podState == null) {
            throw new ActionInitializationException("Pod state cannot be null");
        }
        if (duration == null) {
            throw new ActionInitializationException("Duration cannot be null");
        }
        this.podState = podState;
        this.rate = rate;
        this.duration = duration;
        this.acknowledgementBeep = acknowledgementBeep;
        this.completionBeep = completionBeep;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        List<MessageBlock> messageBlocks = Arrays.asList( //
                new SetInsulinScheduleCommand(podState.getCurrentNonce(), rate, duration),
                new TempBasalExtraCommand(rate, duration, acknowledgementBeep, completionBeep, Duration.ZERO));

        OmnipodMessage message = new OmnipodMessage(podState.getAddress(), messageBlocks, podState.getMessageNumber());
        return communicationService.exchangeMessages(StatusResponse.class, podState, message);
    }
}

package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.SetInsulinScheduleCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.TempBasalExtraCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class SetTempBasalAction implements OmnipodAction<StatusResponse> {
    private final PodStateManager podStateManager;
    private final double rate;
    private final Duration duration;
    private final boolean acknowledgementBeep;
    private final boolean completionBeep;

    public SetTempBasalAction(PodStateManager podStateManager, double rate, Duration duration,
                              boolean acknowledgementBeep, boolean completionBeep) {
        if (podStateManager == null) {
            throw new ActionInitializationException("Pod state manager cannot be null");
        }
        if (duration == null) {
            throw new ActionInitializationException("Duration cannot be null");
        }
        this.podStateManager = podStateManager;
        this.rate = rate;
        this.duration = duration;
        this.acknowledgementBeep = acknowledgementBeep;
        this.completionBeep = completionBeep;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationManager communicationService) {
        List<MessageBlock> messageBlocks = Arrays.asList( //
                new SetInsulinScheduleCommand(podStateManager.getCurrentNonce(), rate, duration),
                new TempBasalExtraCommand(rate, duration, acknowledgementBeep, completionBeep, Duration.ZERO));

        OmnipodMessage message = new OmnipodMessage(podStateManager.getAddress(), messageBlocks, podStateManager.getMessageNumber());
        StatusResponse statusResponse = communicationService.exchangeMessages(StatusResponse.class, podStateManager, message);
        podStateManager.setLastTempBasal(DateTime.now().minus(OmnipodConst.AVERAGE_TEMP_BASAL_COMMAND_COMMUNICATION_DURATION), rate, duration);
        return statusResponse;
    }
}

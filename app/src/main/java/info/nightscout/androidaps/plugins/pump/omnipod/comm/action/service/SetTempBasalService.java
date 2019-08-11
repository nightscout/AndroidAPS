package info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service;

import org.joda.time.Duration;

import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.CancelDeliveryAction;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.SetInsulinScheduleCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.TempBasalExtraCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;

public class SetTempBasalService {
    public StatusResponse cancelTempBasal(OmnipodCommunicationService communicationService, PodSessionState podState) {
        return communicationService.executeAction(new CancelDeliveryAction(podState, DeliveryType.TEMP_BASAL, false));
    }

    public StatusResponse executeTempBasalCommand(OmnipodCommunicationService communicationService,
                                                  PodSessionState podState, double rate, Duration duration,
                                                  boolean acknowledgementBeep, boolean completionBeep) {
        List<MessageBlock> messageBlocks = Arrays.asList( //
                new SetInsulinScheduleCommand(podState.getCurrentNonce(), rate, duration),
                new TempBasalExtraCommand(rate, duration, acknowledgementBeep, completionBeep, Duration.ZERO));

        OmnipodMessage message = new OmnipodMessage(podState.getAddress(), messageBlocks, podState.getMessageNumber());
        return communicationService.exchangeMessages(StatusResponse.class, podState, message);
    }
}

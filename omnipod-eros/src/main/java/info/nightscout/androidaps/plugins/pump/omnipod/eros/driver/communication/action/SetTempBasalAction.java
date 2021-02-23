package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action;

import org.joda.time.Duration;

import java.util.Arrays;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command.SetInsulinScheduleCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command.TempBasalExtraCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class SetTempBasalAction implements OmnipodAction<StatusResponse> {
    private final ErosPodStateManager podStateManager;
    private final double rate;
    private final Duration duration;
    private final boolean acknowledgementBeep;
    private final boolean completionBeep;

    public SetTempBasalAction(ErosPodStateManager podStateManager, double rate, Duration duration,
                              boolean acknowledgementBeep, boolean completionBeep) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager cannot be null");
        }
        if (duration == null) {
            throw new IllegalArgumentException("Duration cannot be null");
        }
        this.podStateManager = podStateManager;
        this.rate = rate;
        this.duration = duration;
        this.acknowledgementBeep = acknowledgementBeep;
        this.completionBeep = completionBeep;
    }

    @Override
    public StatusResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        List<MessageBlock> messageBlocks = Arrays.asList( //
                new SetInsulinScheduleCommand(podStateManager.getCurrentNonce(), rate, duration),
                new TempBasalExtraCommand(rate, duration, acknowledgementBeep, completionBeep, Duration.ZERO));

        OmnipodMessage message = new OmnipodMessage(podStateManager.getAddress(), messageBlocks, podStateManager.getMessageNumber());
        return communicationService.exchangeMessages(StatusResponse.class, podStateManager, message);
    }
}

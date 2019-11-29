package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.SetTempBasalService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.ActionInitializationException;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.IllegalDeliveryStatusException;

public class SetTempBasalAction implements OmnipodAction<StatusResponse> {
    private final SetTempBasalService service;
    private final PodSessionState podState;
    private final double rate;
    private final Duration duration;
    private final boolean acknowledgementBeep;
    private final boolean completionBeep;

    public SetTempBasalAction(SetTempBasalService setTempBasalService, PodSessionState podState,
                              double rate, Duration duration, boolean acknowledgementBeep, boolean completionBeep) {
        if (setTempBasalService == null) {
            throw new ActionInitializationException("Set temp basal service cannot be null");
        }
        if (podState == null) {
            throw new ActionInitializationException("Pod state cannot be null");
        }
        if (duration == null) {
            throw new ActionInitializationException("Duration cannot be null");
        }
        this.service = setTempBasalService;
        this.podState = podState;
        this.rate = rate;
        this.duration = duration;
        this.acknowledgementBeep = acknowledgementBeep;
        this.completionBeep = completionBeep;
    }

    @Override
    public StatusResponse execute(OmnipodCommunicationService communicationService) {
        StatusResponse statusResponse = service.cancelTempBasal(communicationService, podState);

        if (statusResponse.getDeliveryStatus() != DeliveryStatus.NORMAL) {
            throw new IllegalDeliveryStatusException(DeliveryStatus.NORMAL, statusResponse.getDeliveryStatus());
        }

        return service.executeTempBasalCommand(communicationService, podState, rate, duration,
                acknowledgementBeep, completionBeep);
    }
}

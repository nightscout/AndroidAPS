package info.nightscout.androidaps.plugins.pump.omnipod.comm.action;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Random;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service.PairService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.VersionResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSetupState;

public class PairAction implements OmnipodAction<PodSessionState> {
    private final PairService service;
    private final int address;

    public PairAction(PairService pairService, int address) {
        if (pairService == null) {
            throw new IllegalArgumentException("Pair service cannot be null");
        }
        this.service = pairService;
        this.address = address;
    }

    public PairAction(PairService service) {
        this(service, generateRandomAddress());
    }

    private static int generateRandomAddress() {
        return 0x1f000000 | (new Random().nextInt() & 0x000fffff);
    }

    @Override
    public PodSessionState execute(OmnipodCommunicationService communicationService) {
        PodSetupState setupState = new PodSetupState(address, 0x00, 0x00);

        VersionResponse assignAddressResponse = service.executeAssignAddressCommand(communicationService, setupState);

        DateTimeZone timeZone = DateTimeZone.getDefault();
        DateTime activationDate = DateTime.now(timeZone);

        VersionResponse confirmPairingResponse = service.executeConfigurePodCommand(communicationService, setupState,
                assignAddressResponse.getLot(), assignAddressResponse.getTid(), activationDate);

        PodSessionState podState = new PodSessionState(timeZone, address, activationDate, confirmPairingResponse.getPiVersion(),
                confirmPairingResponse.getPmVersion(), confirmPairingResponse.getLot(), confirmPairingResponse.getTid(),
                setupState.getPacketNumber(), setupState.getMessageNumber());
        podState.setSetupProgress(SetupProgress.POD_CONFIGURED);

        return podState;
    }
}

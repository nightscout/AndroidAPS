package info.nightscout.androidaps.plugins.pump.omnipod.comm.action.service;

import org.joda.time.DateTime;

import java.util.Collections;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationService;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.AssignAddressCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command.ConfigurePodCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.VersionResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSetupState;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class PairService {
    public VersionResponse executeAssignAddressCommand(OmnipodCommunicationService communicationService, PodSetupState setupState) {
        AssignAddressCommand assignAddress = new AssignAddressCommand(setupState.getAddress());
        OmnipodMessage assignAddressMessage = new OmnipodMessage(OmnipodConst.DEFAULT_ADDRESS,
                Collections.singletonList(assignAddress), setupState.getMessageNumber());

        return communicationService.exchangeMessages(VersionResponse.class, setupState, assignAddressMessage,
                OmnipodConst.DEFAULT_ADDRESS, setupState.getAddress());
    }

    public VersionResponse executeConfigurePodCommand(OmnipodCommunicationService communicationService,
                                                      PodSetupState setupState, int lot, int tid, DateTime activationDate) {
        // at this point for an unknown reason PDM starts counting messages from 0 again
        setupState.setMessageNumber(0x00);

        ConfigurePodCommand configurePodCommand = new ConfigurePodCommand(setupState.getAddress(), activationDate,
                lot, tid);
        OmnipodMessage message = new OmnipodMessage(OmnipodConst.DEFAULT_ADDRESS,
                Collections.singletonList(configurePodCommand), setupState.getMessageNumber());
        VersionResponse configurePodResponse = communicationService.exchangeMessages(VersionResponse.class, setupState,
                message, OmnipodConst.DEFAULT_ADDRESS, setupState.getAddress());

        if (configurePodResponse.getPodProgressStatus() != PodProgressStatus.PAIRING_SUCCESS) {
            throw new IllegalPodProgressException(PodProgressStatus.PAIRING_SUCCESS, configurePodResponse.getPodProgressStatus());
        }

        return configurePodResponse;
    }
}

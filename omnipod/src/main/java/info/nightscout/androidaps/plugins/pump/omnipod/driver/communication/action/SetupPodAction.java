package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action;

import org.joda.time.DateTime;

import java.util.Collections;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.OmnipodMessage;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.command.SetupPodCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.VersionResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalMessageAddressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalPodProgressException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.exception.IllegalVersionResponseTypeException;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class SetupPodAction implements OmnipodAction<VersionResponse> {
    private final PodStateManager podStateManager;

    public SetupPodAction(PodStateManager podStateManager) {
        if (podStateManager == null) {
            throw new IllegalArgumentException("Pod state manager can not be null");
        }
        this.podStateManager = podStateManager;
    }

    @Override
    public VersionResponse execute(OmnipodRileyLinkCommunicationManager communicationService) {
        if (!podStateManager.isPodInitialized() || !podStateManager.getPodProgressStatus().equals(PodProgressStatus.REMINDER_INITIALIZED)) {
            throw new IllegalPodProgressException(PodProgressStatus.REMINDER_INITIALIZED, podStateManager.isPodInitialized() ? podStateManager.getPodProgressStatus() : null);
        }
        DateTime activationDate = DateTime.now(podStateManager.getTimeZone());

        SetupPodCommand setupPodCommand = new SetupPodCommand(podStateManager.getAddress(), activationDate,
                podStateManager.getLot(), podStateManager.getTid());
        OmnipodMessage message = new OmnipodMessage(OmnipodConstants.DEFAULT_ADDRESS,
                Collections.singletonList(setupPodCommand), podStateManager.getMessageNumber());
        VersionResponse setupPodResponse;
        setupPodResponse = communicationService.exchangeMessages(VersionResponse.class, podStateManager,
                message, OmnipodConstants.DEFAULT_ADDRESS, podStateManager.getAddress());

        if (!setupPodResponse.isSetupPodVersionResponse()) {
            throw new IllegalVersionResponseTypeException("setupPod", "assignAddress");
        }
        if (setupPodResponse.getAddress() != podStateManager.getAddress()) {
            throw new IllegalMessageAddressException(podStateManager.getAddress(), setupPodResponse.getAddress());
        }

        return setupPodResponse;
    }
}

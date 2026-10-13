package app.aaps.pump.omnipod.eros.driver.communication.action;

import androidx.annotation.NonNull;

import org.joda.time.DateTime;

import java.util.Collections;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.pump.omnipod.eros.driver.communication.message.OmnipodMessage;
import app.aaps.pump.omnipod.eros.driver.communication.message.command.SetupPodCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.VersionResponse;
import app.aaps.pump.omnipod.eros.driver.definition.ActivationProgress;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;
import app.aaps.pump.omnipod.eros.driver.definition.PacketType;
import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalMessageAddressException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalPacketTypeException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalPodProgressException;
import app.aaps.pump.omnipod.eros.driver.exception.IllegalVersionResponseTypeException;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class SetupPodAction implements OmnipodAction<Void> {
    @NonNull private final ErosPodStateManager podStateManager;
    private final AAPSLogger aapsLogger;

    public SetupPodAction(@NonNull ErosPodStateManager podStateManager, @NonNull AAPSLogger aapsLogger) {
        this.podStateManager = podStateManager;
        this.aapsLogger = aapsLogger;
    }

    @Override
    public Void execute(@NonNull OmnipodRileyLinkCommunicationManager communicationService) {
        if (!podStateManager.isPodInitialized()) {
            throw new IllegalPodProgressException(PodProgressStatus.REMINDER_INITIALIZED, podStateManager.isPodInitialized() ? podStateManager.getPodProgressStatus() : null);
        }

        if (podStateManager.getActivationProgress().needsPairing()) {
            DateTime activationDate = DateTime.now(podStateManager.getTimeZone());

            SetupPodCommand setupPodCommand = new SetupPodCommand(podStateManager.getAddress(), activationDate,
                    podStateManager.getLot(), podStateManager.getTid());
            OmnipodMessage message = new OmnipodMessage(OmnipodConstants.DEFAULT_ADDRESS,
                    Collections.singletonList(setupPodCommand), podStateManager.getMessageNumber());

            try {
                VersionResponse setupPodResponse = communicationService.exchangeMessages(VersionResponse.class, podStateManager,
                        message, OmnipodConstants.DEFAULT_ADDRESS, podStateManager.getAddress());

                if (!setupPodResponse.isSetupPodVersionResponse()) {
                    throw new IllegalVersionResponseTypeException("setupPod", "assignAddress");
                }
                if (setupPodResponse.getAddress() != podStateManager.getAddress()) {
                    throw new IllegalMessageAddressException(podStateManager.getAddress(), setupPodResponse.getAddress());
                }
            } catch (IllegalPacketTypeException ex) {
                if (PacketType.ACK.equals(ex.getActual())) {
                    // Pod is already configured
                    aapsLogger.debug("Received ACK instead of response in SetupPodAction. Ignoring");
                } else {
                    throw ex;
                }
            }

            podStateManager.setActivationProgress(ActivationProgress.PAIRING_COMPLETED);
        }

        return null;
    }
}

package app.aaps.pump.omnipod.eros.driver.communication.action;

import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public interface OmnipodAction<T> {
    T execute(OmnipodRileyLinkCommunicationManager communicationService);
}

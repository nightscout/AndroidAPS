package info.nightscout.androidaps.plugins.PumpInsight.connector;

import sugar.free.sightparser.applayer.descriptors.ActiveBolusType;
import sugar.free.sightparser.applayer.descriptors.MessagePriority;
import sugar.free.sightparser.applayer.descriptors.alerts.Warning38BolusCancelled;
import sugar.free.sightparser.applayer.messages.AppLayerMessage;
import sugar.free.sightparser.applayer.messages.remote_control.CancelBolusMessage;
import sugar.free.sightparser.applayer.messages.remote_control.DismissAlertMessage;
import sugar.free.sightparser.applayer.messages.status.ActiveAlertMessage;
import sugar.free.sightparser.applayer.messages.status.ActiveBolusesMessage;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.TaskRunner;

// by Tebbe Ubben

public class CancelBolusSilentlyTaskRunner extends TaskRunner {

    private ActiveBolusType bolusType;
    private long cancelledAt;
    private int bolusId;

    public CancelBolusSilentlyTaskRunner(SightServiceConnector serviceConnector, ActiveBolusType bolusType) {
        super(serviceConnector);
        this.bolusType = bolusType;
    }

    @Override
    protected AppLayerMessage run(AppLayerMessage message) throws Exception {
        if (message == null) return new ActiveBolusesMessage();
        else if (message instanceof ActiveBolusesMessage) {
            ActiveBolusesMessage bolusesMessage = (ActiveBolusesMessage) message;
            CancelBolusMessage cancelBolusMessage = new CancelBolusMessage();
            if (bolusesMessage.getBolus1().getBolusType() == bolusType)
                bolusId = bolusesMessage.getBolus1().getBolusID();
            else if (bolusesMessage.getBolus2().getBolusType() == bolusType)
                bolusId = bolusesMessage.getBolus2().getBolusID();
            else if (bolusesMessage.getBolus3().getBolusType() == bolusType)
                bolusId = bolusesMessage.getBolus3().getBolusID();
            else finish(null);
            cancelBolusMessage.setBolusId(bolusId);
            return cancelBolusMessage;
        } else if (message instanceof CancelBolusMessage) {
            cancelledAt = System.currentTimeMillis();
            ActiveAlertMessage activeAlertMessage = new ActiveAlertMessage();
            activeAlertMessage.setMessagePriority(MessagePriority.HIGHER);
            return activeAlertMessage;
        } else if (message instanceof ActiveAlertMessage) {
            ActiveAlertMessage activeAlertMessage = (ActiveAlertMessage) message;
            if (activeAlertMessage.getAlert() == null) {
                if (System.currentTimeMillis() - cancelledAt >= 10000) finish(bolusId);
                else {
                    ActiveAlertMessage activeAlertMessage2 = new ActiveAlertMessage();
                    activeAlertMessage2.setMessagePriority(MessagePriority.HIGHER);
                    return activeAlertMessage2;
                }
            } else if (!(activeAlertMessage.getAlert() instanceof Warning38BolusCancelled)) finish(bolusId);
            else {
                DismissAlertMessage dismissAlertMessage = new DismissAlertMessage();
                dismissAlertMessage.setAlertID(activeAlertMessage.getAlertID());
                dismissAlertMessage.setMessagePriority(MessagePriority.HIGHER);
                return dismissAlertMessage;
            }
        } else if (message instanceof DismissAlertMessage) finish(bolusId);
        return null;
    }
}
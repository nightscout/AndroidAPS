package info.nightscout.androidaps.plugins.PumpInsight.connector;

import sugar.free.sightparser.applayer.descriptors.MessagePriority;
import sugar.free.sightparser.applayer.descriptors.alerts.Warning36TBRCancelled;
import sugar.free.sightparser.applayer.messages.AppLayerMessage;
import sugar.free.sightparser.applayer.messages.remote_control.CancelTBRMessage;
import sugar.free.sightparser.applayer.messages.remote_control.DismissAlertMessage;
import sugar.free.sightparser.applayer.messages.status.ActiveAlertMessage;
import sugar.free.sightparser.applayer.messages.status.CurrentTBRMessage;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.TaskRunner;

public class CancelTBRSilentlyTaskRunner extends TaskRunner {

    private long cancelledAt;

    public CancelTBRSilentlyTaskRunner(SightServiceConnector serviceConnector) {
        super(serviceConnector);
    }

    @Override
    protected AppLayerMessage run(AppLayerMessage message) throws Exception {
        if (message == null) return new CurrentTBRMessage();
        else if (message instanceof CurrentTBRMessage) {
            if (((CurrentTBRMessage) message).getPercentage() == 100) finish(false);
            else return new CancelTBRMessage();
        } else if (message instanceof CancelTBRMessage) {
            ActiveAlertMessage activeAlertMessage = new ActiveAlertMessage();
            activeAlertMessage.setMessagePriority(MessagePriority.HIGHER);
            return activeAlertMessage;
        } else if (message instanceof ActiveAlertMessage) {
            ActiveAlertMessage activeAlertMessage = (ActiveAlertMessage) message;
            if (activeAlertMessage.getAlert() == null) {
                if (System.currentTimeMillis() - cancelledAt >= 10000) finish(true);
                else {
                    ActiveAlertMessage activeAlertMessage2 = new ActiveAlertMessage();
                    activeAlertMessage2.setMessagePriority(MessagePriority.HIGHER);
                    return activeAlertMessage2;
                }
            } else if (!(activeAlertMessage.getAlert() instanceof Warning36TBRCancelled)) finish(true);
            else {
                DismissAlertMessage dismissAlertMessage = new DismissAlertMessage();
                dismissAlertMessage.setAlertID(activeAlertMessage.getAlertID());
                dismissAlertMessage.setMessagePriority(MessagePriority.HIGHER);
                return dismissAlertMessage;
            }
        } else if (message instanceof DismissAlertMessage) finish(true);
        return null;
    }
}

package info.nightscout.androidaps.plugins.PumpInsight.connector;

import sugar.free.sightparser.applayer.messages.AppLayerMessage;
import sugar.free.sightparser.applayer.messages.remote_control.ChangeTBRMessage;
import sugar.free.sightparser.applayer.messages.remote_control.SetTBRMessage;
import sugar.free.sightparser.applayer.messages.status.CurrentTBRMessage;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.TaskRunner;

// from Tebbe - note this uses 1 minute duration to silently cancel existing TBR

public class SetTBRTaskRunner extends TaskRunner {

    private int amount;
    private int duration;

    public SetTBRTaskRunner(SightServiceConnector serviceConnector, int amount, int duration) {
        super(serviceConnector);
        this.amount = amount;
        this.duration = duration;
    }

    @Override
    protected AppLayerMessage run(AppLayerMessage message) throws Exception {
        if (message == null) return new CurrentTBRMessage();
        else if (message instanceof CurrentTBRMessage) {
            if (((CurrentTBRMessage) message).getPercentage() == 100) {
                if (amount == 100) finish(amount);
                else {
                    SetTBRMessage setTBRMessage = new SetTBRMessage();
                    setTBRMessage.setDuration(duration);
                    setTBRMessage.setAmount(amount);
                    return setTBRMessage;
                }
            } else {
                if (amount == 100) {
                    ChangeTBRMessage changeTBRMessage = new ChangeTBRMessage();
                    changeTBRMessage.setDuration(1);
                    changeTBRMessage.setAmount(90);
                    return changeTBRMessage;
                } else {
                    ChangeTBRMessage changeTBRMessage = new ChangeTBRMessage();
                    changeTBRMessage.setDuration(duration);
                    changeTBRMessage.setAmount(amount);
                    return changeTBRMessage;
                }
            }
        } else if (message instanceof SetTBRMessage) finish(amount);
        return null;
    }
}

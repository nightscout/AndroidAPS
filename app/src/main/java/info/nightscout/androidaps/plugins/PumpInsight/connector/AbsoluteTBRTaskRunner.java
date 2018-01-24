package info.nightscout.androidaps.plugins.PumpInsight.connector;

import sugar.free.sightparser.applayer.AppLayerMessage;
import sugar.free.sightparser.applayer.remote_control.ChangeTBRMessage;
import sugar.free.sightparser.applayer.remote_control.SetTBRMessage;
import sugar.free.sightparser.applayer.status.CurrentBasalMessage;
import sugar.free.sightparser.applayer.status.CurrentTBRMessage;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.TaskRunner;

// by Tebbe Ubben

public class AbsoluteTBRTaskRunner extends TaskRunner {

    private float absolute;
    private int amount;
    private int duration;

    public AbsoluteTBRTaskRunner(SightServiceConnector serviceConnector, float absolute, int duration) {
        super(serviceConnector);
        this.absolute = absolute;
    }

    @Override
    protected AppLayerMessage run(AppLayerMessage message) throws Exception {
        if (message == null) return new CurrentBasalMessage();
        else if (message instanceof CurrentBasalMessage) {
            float currentBasal = ((CurrentBasalMessage) message).getCurrentBasalAmount();
            amount = (int) (100F / currentBasal * absolute);
            amount = ((int) amount / 10) * 10;
            if (amount > 250) amount = 250;
            return new CurrentTBRMessage();
        } else if (message instanceof CurrentTBRMessage) {
            SetTBRMessage setTBRMessage;
            if (((CurrentTBRMessage) message).getPercentage() == 100)
                setTBRMessage = new SetTBRMessage();
            else setTBRMessage = new ChangeTBRMessage();
            setTBRMessage.setAmount((short) amount);
            setTBRMessage.setDuration((short) duration);
            return setTBRMessage;
        } else if (message instanceof SetTBRMessage) finish(amount);
        return null;
    }
}

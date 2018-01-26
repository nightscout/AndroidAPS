package info.nightscout.androidaps.plugins.PumpInsight.connector;

import info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers;
import sugar.free.sightparser.applayer.AppLayerMessage;
import sugar.free.sightparser.applayer.remote_control.ChangeTBRMessage;
import sugar.free.sightparser.applayer.remote_control.SetTBRMessage;
import sugar.free.sightparser.applayer.status.CurrentBasalMessage;
import sugar.free.sightparser.applayer.status.CurrentTBRMessage;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.TaskRunner;

// by Tebbe Ubben

public class AbsoluteTBRTaskRunner extends TaskRunner {

    private double absolute;
    private int amount;
    private int duration;
    private int calculated_percentage;
    private double calculated_absolute;

    public AbsoluteTBRTaskRunner(SightServiceConnector serviceConnector, double absolute, int duration) {
        super(serviceConnector);
        if (absolute < 0) absolute = 0;
        this.absolute = absolute;
        this.duration = duration;
    }

    public int getCalculatedPercentage() {
        return calculated_percentage;
    }

    public double getCalculatedAbsolute() {
        return calculated_absolute;
    }

    @Override
    protected AppLayerMessage run(AppLayerMessage message) throws Exception {
        if (message == null) return new CurrentBasalMessage();
        else if (message instanceof CurrentBasalMessage) {
            float currentBasal = ((CurrentBasalMessage) message).getCurrentBasalAmount();
            amount = (int) (100d / currentBasal * absolute);
            amount = ((int) amount / 10) * 10;
            if (amount > 250) amount = 250;
            calculated_percentage = amount;
            calculated_absolute = Helpers.roundDouble(calculated_percentage * (double) currentBasal / 100d, 3);
            Connector.log("Asked: " + absolute + " current: " + currentBasal + " calculated as: " + amount + "%" + " = " + calculated_absolute);
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

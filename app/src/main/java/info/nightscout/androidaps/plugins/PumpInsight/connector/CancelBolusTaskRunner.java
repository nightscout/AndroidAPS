package info.nightscout.androidaps.plugins.PumpInsight.connector;

import sugar.free.sightparser.applayer.AppLayerMessage;
import sugar.free.sightparser.applayer.remote_control.CancelBolusMessage;
import sugar.free.sightparser.applayer.status.ActiveBolusesMessage;
import sugar.free.sightparser.applayer.status.BolusType;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.TaskRunner;

// by Tebbe Ubben

public class CancelBolusTaskRunner extends TaskRunner {

    private BolusType bolusType;

    public CancelBolusTaskRunner(SightServiceConnector serviceConnector, BolusType bolusType) {
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
                cancelBolusMessage.setBolusId(bolusesMessage.getBolus1().getBolusID());
            else if (bolusesMessage.getBolus2().getBolusType() == bolusType)
                cancelBolusMessage.setBolusId(bolusesMessage.getBolus2().getBolusID());
            else if (bolusesMessage.getBolus3().getBolusType() == bolusType)
                cancelBolusMessage.setBolusId(bolusesMessage.getBolus3().getBolusID());
            else finish(null);
            return cancelBolusMessage;
        } else if (message instanceof CancelBolusMessage) finish(null);
        return null;
    }
}

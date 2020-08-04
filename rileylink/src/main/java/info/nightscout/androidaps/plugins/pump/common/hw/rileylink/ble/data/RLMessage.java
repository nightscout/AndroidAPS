package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data;

/**
 * Created by andy on 5/6/18.
 */
public interface RLMessage {

    byte[] getTxData();


    boolean isValid();

}

package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.interlock;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable;

public class BasalInterlock implements Encodable {
    @Override public byte[] getEncoded() {
        return new byte[0];
    }
}

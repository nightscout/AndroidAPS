package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import info.nightscout.androidaps.interfaces.PumpInterface;

public interface OmnipodPumpPluginInterface extends PumpInterface {

    void addPodStatusRequest(OmnipodStatusRequest pumpStatusRequest);

}

package info.nightscout.androidaps.plugins.pump.omnipod.defs;

import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodDriverState;

public interface OmnipodPumpPluginInterface extends PumpInterface {

    void addPodStatusRequest(OmnipodStatusRequest pumpStatusRequest);

    void setDriverState(OmnipodDriverState state);

    @Deprecated
    RxBusWrapper getRxBus();

}

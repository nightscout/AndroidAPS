package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService;

public interface RileyLinkPumpDevice {

    void setIsBusy(boolean isBusy_);

    boolean isBusy();

    void resetRileyLinkConfiguration();

    boolean hasTuneUp();

    void doTuneUpDevice();

    void triggerPumpConfigurationChangedEvent();

    RileyLinkService getRileyLinkService();

}

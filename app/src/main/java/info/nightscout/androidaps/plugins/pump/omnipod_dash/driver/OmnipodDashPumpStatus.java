package info.nightscout.androidaps.plugins.pump.omnipod_dash.driver;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class OmnipodDashPumpStatus extends OmnipodPumpStatus {

    @Inject
    public OmnipodDashPumpStatus(ResourceHelper resourceHelper,
                                 SP sp,
                                 RxBusWrapper rxBus,
                                 RileyLinkUtil rileyLinkUtil) {
        super(resourceHelper, sp, rxBus, rileyLinkUtil);
        this.pumpType = PumpType.Insulet_Omnipod_Dash;
    }
}

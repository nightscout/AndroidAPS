package info.nightscout.androidaps.plugins.PumpVirtual;

import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.PumpCommon.data.PumpStatus;

/**
 * Created by andy on 4/28/18.
 */

public class VirtualPumpStatus extends PumpStatus {

    public VirtualPumpStatus(PumpDescription pumpDescription) {
        super(pumpDescription);
    }

    @Override
    public void initSettings() {

    }

    @Override
    public String getErrorInfo() {
        return null;
    }

    @Override
    public void refreshConfiguration() {

    }
}

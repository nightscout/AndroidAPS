package info.nightscout.androidaps.plugins.PumpCommon.driver;

import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.PumpCommon.data.PumpStatus;

/**
 * Created by andy on 4/28/18.
 */

public interface PumpDriverInterface extends PumpInterface {


    PumpStatus getPumpStatusData();

}

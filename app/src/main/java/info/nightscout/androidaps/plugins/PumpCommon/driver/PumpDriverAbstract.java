package info.nightscout.androidaps.plugins.PumpCommon.driver;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.PumpCommon.data.PumpStatus;

/**
 * Created by andy on 4/28/18.
 */

public abstract class PumpDriverAbstract implements PumpDriverInterface {

    protected static final PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult().success(false)
        .enacted(false).comment(MainApp.gs(R.string.pump_operation_not_supported_by_pump));
    protected static final PumpEnactResult OPERATION_NOT_YET_SUPPORTED = new PumpEnactResult().success(false)
        .enacted(false).comment(MainApp.gs(R.string.pump_operation_not_yet_supported_by_pump));
    protected PumpDescription pumpDescription;
    protected PumpStatus pumpStatusData;


    protected PumpDriverAbstract() {
    }


    public void initDriver(PumpStatus pumpStatus, PumpDescription pumpDescription) {
        this.pumpDescription = pumpDescription;
        this.pumpStatusData = pumpStatus;
    }


    @Override
    public String deviceID() {
        return null;
    }


    @Override
    public PumpStatus getPumpStatusData() {
        return this.pumpStatusData;
    }


    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }


    @Override
    public Date lastDataTime() {
        if (this.pumpStatusData == null || this.pumpStatusData.lastDataTime == null) {
            return new Date();
        }

        return this.pumpStatusData.lastDataTime.toDate();
    }
}

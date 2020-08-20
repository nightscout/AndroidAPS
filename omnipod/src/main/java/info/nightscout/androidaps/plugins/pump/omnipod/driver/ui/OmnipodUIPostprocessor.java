package info.nightscout.androidaps.plugins.pump.omnipod.driver.ui;

import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;

/**
 * Created by andy on 4.8.2019
 */
// TODO remove once OmnipodPumpStatus has been removed
@Singleton
public class OmnipodUIPostprocessor {
    private final AAPSLogger aapsLogger;
    private final OmnipodPumpStatus pumpStatus;

    @Inject
    public OmnipodUIPostprocessor(AAPSLogger aapsLogger, OmnipodPumpStatus pumpStatus) {
        this.aapsLogger = aapsLogger;
        this.pumpStatus = pumpStatus;
    }

    // this is mostly intended for command that return certain statuses (Remaining Insulin, ...), and
    // where responses won't be directly used
    public void postProcessData(OmnipodUITask uiTask) {

        switch (uiTask.commandType) {
            case SetBolus:
                if (uiTask.returnData != null) {

                    PumpEnactResult result = uiTask.returnData;

                    DetailedBolusInfo detailedBolusInfo = (DetailedBolusInfo) uiTask.getObjectFromParameters(0);

                    if (result.success) {
                        boolean isSmb = detailedBolusInfo.isSMB;

                        if (!isSmb) {
                            pumpStatus.lastBolusAmount = detailedBolusInfo.insulin;
                            pumpStatus.lastBolusTime = new Date();
                        }
                    }
                }
                break;

            case CancelTemporaryBasal:
                pumpStatus.tempBasalStart = 0;
                pumpStatus.tempBasalEnd = 0;
                pumpStatus.tempBasalAmount = null;
                pumpStatus.tempBasalLength = null;
                break;

            default:
                aapsLogger.debug(LTag.PUMP, "Post-processing not implemented for {}.", uiTask.commandType.name());

        }
    }
}

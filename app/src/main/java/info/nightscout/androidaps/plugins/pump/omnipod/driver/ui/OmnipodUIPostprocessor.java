package info.nightscout.androidaps.plugins.pump.omnipod.driver.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCustomActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodResponseType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;

/**
 * Created by andy on 4.8.2019
 */

public class OmnipodUIPostprocessor {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    private OmnipodPumpStatus pumpStatus;
    private OmnipodPumpPlugin omnipodPumpPlugin;


    public OmnipodUIPostprocessor(OmnipodPumpPlugin plugin, OmnipodPumpStatus pumpStatus) {
        this.pumpStatus = pumpStatus;
        this.omnipodPumpPlugin = plugin;
    }


    // this is mostly intended for command that return certain statuses (Remaining Insulin, ...), and
    // where responses won't be directly used
    public void postProcessData(OmnipodUITask uiTask) {

        switch (uiTask.commandType) {

            case SetBolus: {
                if (uiTask.returnData!=null) {

                    PumpEnactResult result = uiTask.returnData;

                    if (result.success) {
                        boolean isSmb = uiTask.getBooleanFromParameters(1);

                        if (!isSmb) {
                            pumpStatus.lastBolusAmount = uiTask.getDoubleFromParameters(0);
                            pumpStatus.lastBolusTime = new Date();
                        }
                    }
                }
            } break;

            case CancelTemporaryBasal: {
                pumpStatus.tempBasalStart = 0;
                pumpStatus.tempBasalEnd = 0;
                pumpStatus.tempBasalAmount = null;
                pumpStatus.tempBasalLength = null;
            }
            break;

//            case PairAndPrimePod: {
//                if (uiTask.returnData.success) {
//                    omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.PairAndPrime, false);
//                    omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.FillCanulaSetBasalProfile, true);
//                }
//                omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.DeactivatePod, true);
//            }
//            break;
//
//            case FillCanulaAndSetBasalProfile: {
//                if (uiTask.returnData.success) {
//                    omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.FillCanulaSetBasalProfile, false);
//                }
//                omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.DeactivatePod, true);
//            }
//            break;
//
//            case DeactivatePod:
//            case ResetPodStatus: {
//                omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.PairAndPrime, true);
//                omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.DeactivatePod, false);
//            }
//            break;


            default:
                if (isLogEnabled())
                    LOG.trace("Post-processing not implemented for {}.", uiTask.commandType.name());

        }





    }


    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMP);
    }

}

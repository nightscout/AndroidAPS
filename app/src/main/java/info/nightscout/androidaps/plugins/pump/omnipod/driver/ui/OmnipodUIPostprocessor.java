package info.nightscout.androidaps.plugins.pump.omnipod.driver.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodPumpPluginInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;

/**
 * Created by andy on 4.8.2019
 */

public class OmnipodUIPostprocessor {


    private static final Logger LOG = LoggerFactory.getLogger(LTag.PUMP.name());

    private OmnipodPumpStatus pumpStatus;
    private OmnipodPumpPluginInterface omnipodPumpPlugin;
    private RxBusWrapper rxBus;


    public OmnipodUIPostprocessor(OmnipodPumpPluginInterface plugin, OmnipodPumpStatus pumpStatus) {
        this.pumpStatus = pumpStatus;
        this.omnipodPumpPlugin = plugin;
        this.rxBus = plugin.getRxBus();
    }


    // this is mostly intended for command that return certain statuses (Remaining Insulin, ...), and
    // where responses won't be directly used
    public void postProcessData(OmnipodUITask uiTask) {

        switch (uiTask.commandType) {

            case SetBolus: {
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
            }
            break;

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
        return L.isEnabled(LTag.PUMP);
    }

    public RxBusWrapper getRxBus() {
        return this.rxBus;
    }
}

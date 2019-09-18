package info.nightscout.androidaps.plugins.pump.omnipod.comm.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCustomActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.service.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

/**
 * Created by andy on 4.8.2019
 */

public class OmnipodUIPostprocessor {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    OmnipodPumpStatus pumpStatus;
    OmnipodPumpPlugin omnipodPumpPlugin;


    public OmnipodUIPostprocessor() {
        pumpStatus = OmnipodUtil.getPumpStatus();
        omnipodPumpPlugin = OmnipodPumpPlugin.getPlugin();
    }


    // this is mostly intended for command that return certain statuses (Remaining Insulin, ...), and
    // where responses won't be directly used
    public void postProcessData(OmnipodUITask uiTask) {

        switch (uiTask.commandType) {

            case PairAndPrimePod: {
                if (uiTask.returnData.success) {
                    omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.PairAndPrime, false);
                    omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.FillCanulaSetBasalProfile, true);
                }
                omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.DeactivatePod, true);
            }
            break;

            case FillCanulaAndSetBasalProfile: {
                if (uiTask.returnData.success) {
                    omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.FillCanulaSetBasalProfile, false);
                }
                omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.DeactivatePod, true);
            }
            break;

            case DeactivatePod:
            case ResetPodStatus: {
                omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.PairAndPrime, true);
                omnipodPumpPlugin.setEnableCustomAction(OmnipodCustomActionType.DeactivatePod, false);
            }
            break;


            default:
                if (isLogEnabled())
                    LOG.trace("Post-processing not implemented for {}.", uiTask.commandType.name());

        }

    }


    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMP);
    }

}

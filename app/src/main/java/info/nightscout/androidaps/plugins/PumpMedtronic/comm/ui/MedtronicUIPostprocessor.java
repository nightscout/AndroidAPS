package info.nightscout.androidaps.plugins.PumpMedtronic.comm.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;


/**
 * Created by andy on 6/15/18.
 */

public class MedtronicUIPostprocessor {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicUIPostprocessor.class);

    MedtronicPumpStatus pumpStatus;

    public MedtronicUIPostprocessor() {
        pumpStatus = MedtronicUtil.getPumpStatus();
    }


    // this is mostly intended for command that return certain statuses (Remaining Insulin, ...), and
    // where responses won't be directly used
    public void postProcessData(MedtronicUITask uiTask) {

        switch (uiTask.commandType) {

            case GetBasalProfileSTD: {
                BasalProfile basalProfile = (BasalProfile) uiTask.returnData;
                pumpStatus.basalsByHour = basalProfile.getProfilesByHour();
            }
            break;

            case SetBolus: {
                pumpStatus.lastBolusAmount = uiTask.getDoubleFromParameters(0);
                pumpStatus.lastBolusTime = new Date();
            }
            break;

            case GetRemainingInsulin: {
                pumpStatus.reservoirRemainingUnits = (Float) uiTask.returnData;
            }
            break;


            // no postprocessing
            case PumpModel:
                break;

            default:
                LOG.warn("Post-processing not implemented for {}.", uiTask.commandType.name());

        }


    }


}

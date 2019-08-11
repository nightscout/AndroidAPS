package info.nightscout.androidaps.plugins.pump.omnipod.comm.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodResponseType;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodDeviceStatusChange;

/**
 * Created by andy on 4.8.2019
 */

public class OmnipodUITask {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    public OmnipodCommandType commandType;
    public Object returnData;
    private String errorDescription;
    private Object[] parameters;
    private PodResponseType responseType;


    public OmnipodUITask(OmnipodCommandType commandType) {
        this.commandType = commandType;
    }


    public OmnipodUITask(OmnipodCommandType commandType, Object... parameters) {
        this.commandType = commandType;
        this.parameters = parameters;
    }


    public void execute(OmnipodCommunicationManager communicationManager) {

        if (isLogEnabled())
            LOG.debug("OmnipodUITask: @@@ In execute. {}", commandType);

        switch (commandType) {
            // TODO add commands this is just sample
//            case PumpModel: {
//                returnData = communicationManager.getPumpModel();
//            }
//            break;

            case InitPod:
                returnData = communicationManager.initPod();
                break;

            case DeactivatePod:
                returnData = communicationManager.deactivatePod();
                break;

            case ResetPodStatus:
                returnData = communicationManager.resetPodStatus();
                break;

            case SetBasalProfile:
                returnData = communicationManager.setBasalProfile((Profile) parameters[0]);
                break;

            case SetBolus: {
                Double amount = getDoubleFromParameters(0);

                if (amount != null)
                    returnData = communicationManager.setBolus(amount);
            }
            break;

            case CancelBolus:
                returnData = communicationManager.cancelBolus();
                break;

            case SetTemporaryBasal: {
                TempBasalPair tbr = getTBRSettings();
                if (tbr != null) {
                    returnData = communicationManager.setTemporaryBasal(tbr);
                }
            }
            break;

            case CancelTemporaryBasal:
                returnData = communicationManager.cancelTemporaryBasal();
                break;


            default: {
                LOG.warn("This commandType is not supported (yet) - {}.", commandType);
            }

        }

    }


    private TempBasalPair getTBRSettings() {
        return new TempBasalPair(getDoubleFromParameters(0), //
                false, //
                getIntegerFromParameters(1));
    }


    private Float getFloatFromParameters(int index) {
        return (Float) parameters[index];
    }


    public Double getDoubleFromParameters(int index) {
        return (Double) parameters[index];
    }


    public Integer getIntegerFromParameters(int index) {
        return (Integer) parameters[index];
    }


    public Object getResult() {
        return returnData;
    }


    public boolean isReceived() {
        return (returnData != null || errorDescription != null);
    }


    public void postProcess(OmnipodUIPostprocessor postprocessor) {

        EventOmnipodDeviceStatusChange statusChange;
        if (isLogEnabled())
            LOG.debug("OmnipodUITask: @@@ In execute. {}", commandType);

        if (responseType == PodResponseType.Data || responseType == PodResponseType.Acknowledgment) {
            postprocessor.postProcessData(this);
        }

        if (responseType == PodResponseType.Invalid) {
            statusChange = new EventOmnipodDeviceStatusChange(PumpDeviceState.ErrorWhenCommunicating,
                    "Unsupported command in OmnipodUITask");
            MainApp.bus().post(statusChange);
        } else if (responseType == PodResponseType.Error) {
            statusChange = new EventOmnipodDeviceStatusChange(PumpDeviceState.ErrorWhenCommunicating,
                    errorDescription);
            MainApp.bus().post(statusChange);
        } else {
            MainApp.bus().post(new EventMedtronicPumpValuesChanged());
            MedtronicUtil.getPumpStatus().setLastCommunicationToNow();
        }

        MedtronicUtil.setCurrentCommand(null);
    }


    public boolean hasData() {
        return (responseType == PodResponseType.Data || responseType == PodResponseType.Acknowledgment);
    }


    public Object getParameter(int index) {
        return parameters[index];
    }


    private boolean isLogEnabled() {
        return L.isEnabled(L.PUMP);
    }


    public PodResponseType getResponseType() {
        return this.responseType;
    }

}

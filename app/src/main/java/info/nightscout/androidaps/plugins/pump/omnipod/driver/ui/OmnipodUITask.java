package info.nightscout.androidaps.plugins.pump.omnipod.driver.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodDeviceState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodResponseType;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

/**
 * Created by andy on 4.8.2019
 */

public class OmnipodUITask {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    public OmnipodCommandType commandType;
    public PumpEnactResult returnData;
    private String errorDescription;
    private Object[] parameters;
    private PodResponseType responseType;
    public Object returnDataObject;


    public OmnipodUITask(OmnipodCommandType commandType) {
        this.commandType = commandType;
    }


    public OmnipodUITask(OmnipodCommandType commandType, Object... parameters) {
        this.commandType = commandType;
        this.parameters = parameters;
    }


    public void execute(OmnipodCommunicationManagerInterface communicationManager) {

        if (isLogEnabled())
            LOG.debug("OmnipodUITask: @@@ In execute. {}", commandType);

        switch (commandType) {
            // TODO add commands this is just sample
//            case PumpModel: {
//                returnData = communicationManager.getPumpModel();
//            }
//            break;

            case PairAndPrimePod:
                returnData = communicationManager.initPod((PodInitActionType) parameters[0], (PodInitReceiver) parameters[1], null);
// TODO                returnData = communicationManager.pairAndPrime();
                break;

            case FillCanulaAndSetBasalProfile:
                returnData = communicationManager.initPod((PodInitActionType) parameters[0], (PodInitReceiver) parameters[1], (Profile) parameters[2]);
                break;

            case DeactivatePod:
                returnData = communicationManager.deactivatePod((PodInitReceiver) parameters[0]);
                break;

            case ResetPodStatus:
                returnData = communicationManager.resetPodStatus();
// TODO                returnData = communicationManager.resetPodState();
                break;

            case SetBasalProfile:
                returnData = communicationManager.setBasalProfile((Profile) parameters[0]);
                break;

            case SetBolus: {
                Double amount = getDoubleFromParameters(0);
                boolean isSmb = getBooleanFromParameters(1);

                if (amount != null)
                    returnData = communicationManager.setBolus(amount, isSmb);
// TODO                    returnData = communicationManager.bolus(amount);
            }
            break;

            case GetPodPulseLog:
                try {
                    returnDataObject = communicationManager.readPulseLog();
                } catch(Exception ex) {
                    if(isLogEnabled()) {
                        LOG.warn("Failed to retrieve pulse log", ex);
                    }
                    returnDataObject = null;
                }
                break;

            case GetPodStatus:
                returnData = communicationManager.getPodStatus();
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

            case AcknowledgeAlerts:
                returnData = communicationManager.acknowledgeAlerts();
                break;

            case SetTime:
                returnData = communicationManager.setTime();
                break;

            default: {
                LOG.warn("This commandType is not supported (yet) - {}.", commandType);
            }

        }

        // TODO response

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

    public boolean getBooleanFromParameters(int index) {
        return (boolean) parameters[index];
    }

    public Integer getIntegerFromParameters(int index) {
        return (Integer) parameters[index];
    }


    public <T> T getResult() {
        return (T) returnData;
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
            statusChange = new EventOmnipodDeviceStatusChange(PodDeviceState.ErrorWhenCommunicating,
                    "Unsupported command in OmnipodUITask");
            RxBus.INSTANCE.send(statusChange);
        } else if (responseType == PodResponseType.Error) {
            statusChange = new EventOmnipodDeviceStatusChange(PodDeviceState.ErrorWhenCommunicating,
                    errorDescription);
            RxBus.INSTANCE.send(statusChange);
        } else {
            OmnipodUtil.getPumpStatus().setLastCommunicationToNow();
            RxBus.INSTANCE.send(new EventOmnipodPumpValuesChanged());
        }

        OmnipodUtil.setPodDeviceState(PodDeviceState.Sleeping);
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

    public boolean wasCommandSuccessful() {
        if (returnData == null) {
            return false;
        }
        return returnData.success;
    }


}

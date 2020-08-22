package info.nightscout.androidaps.plugins.pump.omnipod.driver.ui;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.IOmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodResponseType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

/**
 * Created by andy on 4.8.2019
 */

public class OmnipodUITask {

    @Inject RxBusWrapper rxBus;
    @Inject AAPSLogger aapsLogger;
    @Inject OmnipodPumpStatus omnipodPumpStatus;
    @Inject OmnipodUtil omnipodUtil;

    private final HasAndroidInjector injector;

    public OmnipodCommandType commandType;
    public PumpEnactResult returnData;
    private String errorDescription;
    private Object[] parameters;
    public PodResponseType responseType;
    public Object returnDataObject;


    public OmnipodUITask(HasAndroidInjector injector, OmnipodCommandType commandType) {
        this.injector = injector;
        this.injector.androidInjector().inject(this);
        this.commandType = commandType;
    }


    public OmnipodUITask(HasAndroidInjector injector, OmnipodCommandType commandType, Object... parameters) {
        this.injector = injector;
        this.injector.androidInjector().inject(this);
        this.commandType = commandType;
        this.parameters = parameters;
    }


    public void execute(IOmnipodManager communicationManager) {


        aapsLogger.debug(LTag.PUMP, "OmnipodUITask: @@@ In execute. {}", commandType);

        switch (commandType) {

            case PairAndPrimePod:
                returnData = communicationManager.initPod((PodInitActionType) parameters[0], (PodInitReceiver) parameters[1], null);
                break;

            case FillCanulaAndSetBasalProfile:
                returnData = communicationManager.initPod((PodInitActionType) parameters[0], (PodInitReceiver) parameters[1], (Profile) parameters[2]);
                break;

            case DeactivatePod:
                returnData = communicationManager.deactivatePod((PodInitReceiver) parameters[0]);
                break;

            case ResetPodStatus:
                returnData = communicationManager.resetPodStatus();
                break;

            case SetBasalProfile:
                returnData = communicationManager.setBasalProfile((Profile) parameters[0]);
                break;

            case SetBolus: {
                DetailedBolusInfo detailedBolusInfo = (DetailedBolusInfo) parameters[0];

                if (detailedBolusInfo != null)
                    returnData = communicationManager.setBolus(detailedBolusInfo);
            }
            break;

            case GetPodPulseLog:
                // This command is very error prone, so retry a few times if it fails
                // Can take some time, but that's ok since this is a very specific feature for experts
                // And will not be used by normal users
                for (int i = 0; 3 > i; i++) {
                    try {
                        returnDataObject = communicationManager.readPulseLog();
                        responseType = PodResponseType.Acknowledgment;
                        break;
                    } catch (Exception ex) {
                        {
                            aapsLogger.warn(LTag.PUMP, "Failed to retrieve pulse log", ex);
                        }
                        returnDataObject = null;
                        responseType = PodResponseType.Error;
                    }
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
                aapsLogger.warn(LTag.PUMP, "This commandType is not supported (yet) - {}.", commandType);
                responseType = PodResponseType.Error;
            }

        }

        if (returnData != null) {
            responseType = returnData.success ? PodResponseType.Acknowledgment : PodResponseType.Error;
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

    public Object getObjectFromParameters(int index) {
        return parameters[index];
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


    // FIXME remove once OmnipodPumpStatus has been removed
    public void postProcess(OmnipodUIPostprocessor postprocessor) {

        aapsLogger.debug(LTag.PUMP, "OmnipodUITask: @@@ postProcess. {}", commandType);
        aapsLogger.debug(LTag.PUMP, "OmnipodUITask: @@@ postProcess. responseType={}", responseType);

        if (responseType == PodResponseType.Data || responseType == PodResponseType.Acknowledgment) {
            postprocessor.postProcessData(this);
        }

        aapsLogger.debug(LTag.PUMP, "OmnipodUITask: @@@ postProcess. responseType={}", responseType);

        if (!responseType.isError()) {
            omnipodPumpStatus.setLastCommunicationToNow();
        }
    }


    public boolean hasData() {
        return (responseType == PodResponseType.Data || responseType == PodResponseType.Acknowledgment);
    }


    public Object getParameter(int index) {
        return parameters[index];
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

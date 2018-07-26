package info.nightscout.androidaps.plugins.PumpMedtronic.comm.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventMedtronicDeviceStatusChange;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;

/**
 * Created by andy on 6/14/18.
 */

public class MedtronicUITask {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicUITask.class);

    public MedtronicCommandType commandType;
    private Object[] parameters;
    private boolean received;
    public Object returnData;
    String errorDescription;
    boolean invalid = false;

    public MedtronicUITask(MedtronicCommandType commandType) {
        this.commandType = commandType;
    }

    public MedtronicUITask(MedtronicCommandType commandType, Object... parameters) {
        this.commandType = commandType;
        this.parameters = parameters;
    }

    public void execute(MedtronicCommunicationManager communicationManager) {

        LOG.warn("@@@ In execute. {}", commandType);

        switch (commandType) {
            case PumpModel: {
                returnData = communicationManager.getPumpModel();
            }
            break;

            case GetBasalProfileSTD: {
                returnData = communicationManager.getBasalProfile();
            }
            break;

            case GetRemainingInsulin: {
                returnData = communicationManager.getRemainingInsulin();
            }
            break;

            case RealTimeClock: {
                returnData = communicationManager.getPumpTime();
            }
            break;

            case GetBatteryStatus: {
                returnData = communicationManager.getRemainingBattery();
            }
            break;

            case SetTemporaryBasal: {
                TempBasalPair tbr = getTBRSettings();
                if (tbr != null) {
                    returnData = communicationManager.setTBR(tbr);
                }
            }
            break;

            case ReadTemporaryBasal: {
                returnData = communicationManager.getTemporaryBasal();
            }
            break;

            case PumpState: {
                // TODO maybe remove this, data returned is almost useless
                returnData = communicationManager.getPumpState();
            }
            break;

//            case "RefreshData.GetBolus": {
//                returnData = communicationManager.getBolusStatus();
//            }
//            break;

            case Settings:
            case Settings_512: {
                returnData = communicationManager.getPumpSettings();
            }
            break;

            case SetBolus: {
                Double amount = getDoubleFromParameters(0);

                if (amount != null)
                    returnData = communicationManager.setBolus(amount);
            }
            break;

            case CancelTBR: {
                // FIXME check if TBR is actually running
                returnData = communicationManager.cancelTBR();
            }
            break;

            case SetBasalProfileSTD:
            case SetBasalProfileA: {

//                Float amount = getAmount();
//
//                if (amount != null) {
//
//                    BasalProfile profile = new BasalProfile();
//
//                    int basalStrokes1 = MedtronicUtil.getBasalStrokesInt(amount);
//                    int basalStrokes2 = MedtronicUtil.getBasalStrokesInt(amount * 2);
//
//                    for (int i = 0; i < 24; i++) {
//                        profile.addEntry(new BasalProfileEntry(i % 2 == 0 ? basalStrokes1 : basalStrokes2, i * 2));
//                    }
//
//                    returnData = communicationManager.setBasalProfile(profile);
//                }

            }
            break;

            default: {
                LOG.warn("This commandType is not supported (yet) - {}.", commandType);
                invalid = true;
            }

        }


        if (returnData == null) {
            if (!invalid)
                errorDescription = communicationManager.getErrorResponse();
            received = true;
        } else {
            received = true;
        }


    }

    private TempBasalPair getTBRSettings() {
        TempBasalPair tempBasalPair = new TempBasalPair(
                getDoubleFromParameters(0), //
                false, //
                getIntegerFromParameters(1));

        return tempBasalPair;
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


    public boolean haveData() {
        return (returnData != null);
    }


    public void postProcess(MedtronicUIPostprocessor postprocessor) {

        EventMedtronicDeviceStatusChange statusChange;
        LOG.warn("@@@ In execute. {}", commandType);

        // should never happen
        if (invalid) {
            statusChange = new EventMedtronicDeviceStatusChange(PumpDeviceState.ErrorWhenCommunicating, "Unsupported command in MedtronicUITask");
            MainApp.bus().post(statusChange);
        }

        if (errorDescription != null) {
            statusChange = new EventMedtronicDeviceStatusChange(PumpDeviceState.ErrorWhenCommunicating, errorDescription);
            MainApp.bus().post(statusChange);
        }

        if (returnData != null) {
            postprocessor.postProcessData(this);
        }

        MedtronicUtil.getPumpStatus().setLastCommunicationToNow();

        MedtronicUtil.setCurrentCommand(null);
    }


    public boolean hasData() {
        return (returnData != null);
    }
}

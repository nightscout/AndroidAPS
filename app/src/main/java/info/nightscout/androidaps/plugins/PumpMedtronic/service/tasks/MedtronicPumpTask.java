package info.nightscout.androidaps.plugins.PumpMedtronic.service.tasks;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.tasks.PumpTask;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.PumpMedtronic.service.RileyLinkMedtronicService;
import info.nightscout.androidaps.plugins.PumpMedtronic.service.data.MedtronicPumpResult;

/**
 * Created by geoff on 7/9/16.
 */
public class MedtronicPumpTask extends PumpTask {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicPumpTask.class);

    MedtronicCommandType commandType;
    Object[] parameters;


    public MedtronicPumpTask(MedtronicCommandType commandType, Object... parameters) {
        this.commandType = commandType;
        this.parameters = parameters;
    }


    public MedtronicPumpTask(ServiceTransport transport) {
        super(transport);
    }


    @Override
    public void run() {

        MedtronicCommunicationManager communicationManager = RileyLinkMedtronicService.getCommunicationManager();
        MedtronicPumpResult medtronicPumpResult = new MedtronicPumpResult(commandType);

        switch (commandType) {

            case GetRemainingInsulin:
                Float remainingInsulin = communicationManager.getRemainingInsulin();
                if (remainingInsulin == null) {
                    medtronicPumpResult.setError();
                } else {
                    medtronicPumpResult.addParameter("RemainingInsulin", remainingInsulin);
                }


            case PumpModel: {
                MedtronicDeviceType pumpModel = communicationManager.getPumpModel();

                if (pumpModel == MedtronicDeviceType.Unknown_Device) {
                    medtronicPumpResult.setError();
                } else {
                    medtronicPumpResult.addParameter("PumpModel", pumpModel.name());
                }
            }
            break;

            case RealTimeClock: {
                LocalDateTime pumpResponse = communicationManager.getPumpTime();
                if (pumpResponse != null) {
                    LOG.info("ReadPumpClock: " + pumpResponse.toString("HH:mm:ss"));
                    medtronicPumpResult.addParameter("PumpTime", pumpResponse);
                } else {
                    LOG.warn("handleServiceCommand(" + mTransport.getOriginalCommandName() + ") pumpResponse is null");
                    medtronicPumpResult.setError();
                }
            }
            break;


            default:
                LOG.error("Type {} is NOT supported.");
                break;

        }

        getServiceTransport().setServiceResult(medtronicPumpResult);
    }
}

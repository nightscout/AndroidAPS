package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.RileyLinkService;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.data.ServiceNotification;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.data.ServiceResult;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.tasks.ServiceTask;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventMedtronicDeviceStatusChange;


/**
 * Created by andy on 17/05/2018.
 */

public class RileyLinkUtil {

    private static final Logger LOG = LoggerFactory.getLogger(RileyLinkUtil.class);

    private static Context context;
    private static RileyLinkBLE rileyLinkBLE;
    private static RileyLinkServiceData rileyLinkServiceData;
    private static List<RLHistoryItem> historyRileyLink = new ArrayList<>();
    //private static PumpType pumpType;
    //private static MedtronicPumpStatus medtronicPumpStatus;
    private static RileyLinkService rileyLinkService;
    protected static RileyLinkCommunicationManager rileyLinkCommunicationManager;
    //private static RileyLinkIPCConnection rileyLinkIPCConnection;
    private static MedtronicDeviceType medtronicPumpModel;
    private static RileyLinkTargetFrequency rileyLinkTargetFrequency;
    private static MedtronicPumpStatus pumpStatus;
    // BAD dependencies in Classes: RileyLinkService

    // Broadcasts: RileyLinkBLE, RileyLinkService,


    public static void setContext(Context contextIn) {
        RileyLinkUtil.context = contextIn;
    }


    public static void sendBroadcastMessage(String message) {
        Intent intent = new Intent(message);
        LocalBroadcastManager.getInstance(RileyLinkUtil.context).sendBroadcast(intent);
    }


    public static void setServiceState(RileyLinkServiceState newState) {
        setServiceState(newState, null);
    }


    public static RileyLinkServiceState getServiceState() {
        return RileyLinkUtil.rileyLinkServiceData.serviceState;
    }


    public static RileyLinkError getError() {
        return RileyLinkUtil.rileyLinkServiceData.errorCode;
    }


    public static void setServiceState(RileyLinkServiceState newState, RileyLinkError errorCode) {
        RileyLinkUtil.rileyLinkServiceData.serviceState = newState;
        RileyLinkUtil.rileyLinkServiceData.errorCode = errorCode;

        LOG.warn("RileyLink State Changed: {} {}", newState, errorCode == null ? "" : " - Error State: " + errorCode.name());

        RileyLinkUtil.historyRileyLink.add(new RLHistoryItem(RileyLinkUtil.rileyLinkServiceData.serviceState, RileyLinkUtil.rileyLinkServiceData.errorCode));
        MainApp.bus().post(new EventMedtronicDeviceStatusChange(newState, errorCode));
    }


    public static void setRileyLinkBLE(RileyLinkBLE rileyLinkBLEIn) {
        RileyLinkUtil.rileyLinkBLE = rileyLinkBLEIn;
    }


    public static RileyLinkBLE getRileyLinkBLE() {
        return RileyLinkUtil.rileyLinkBLE;
    }


    public static RileyLinkServiceData getRileyLinkServiceData() {
        return RileyLinkUtil.rileyLinkServiceData;
    }


    public static void setRileyLinkServiceData(RileyLinkServiceData rileyLinkServiceData) {
        RileyLinkUtil.rileyLinkServiceData = rileyLinkServiceData;
    }


//    public static void setMedtronicPumpStatus(MedtronicPumpStatus medtronicPumpStatus) {
//
//        RileyLinkUtil.medtronicPumpStatus = medtronicPumpStatus;
//    }

    //    public static void addHistoryEntry(RLHistoryItem rlHistoryItem) {
    //        historyRileyLink.add(rlHistoryItem);
    //    }


//    public static MedtronicPumpStatus getMedtronicPumpStatus() {
//
//        return RileyLinkUtil.medtronicPumpStatus;
//    }


    public static boolean hasPumpBeenTunned() {
        return RileyLinkUtil.rileyLinkServiceData.tuneUpDone;
    }


    public static void tuneUpPump() {
        RileyLinkUtil.rileyLinkService.doTunePump(); // FIXME thread
    }


    public static void setRileyLinkService(RileyLinkService rileyLinkService) {
        RileyLinkUtil.rileyLinkService = rileyLinkService;
    }


    public static RileyLinkService getRileyLinkService() {
        return RileyLinkUtil.rileyLinkService;
    }


    public static void setRileyLinkCommunicationManager(RileyLinkCommunicationManager rileyLinkCommunicationManager) {
        RileyLinkUtil.rileyLinkCommunicationManager = rileyLinkCommunicationManager;
    }


    public static RileyLinkCommunicationManager getRileyLinkCommunicationManager() {
        return RileyLinkUtil.rileyLinkCommunicationManager;
    }


    public static boolean sendNotification(ServiceNotification notification, Integer clientHashcode) {
        return rileyLinkService.sendNotification(notification, clientHashcode);
    }


    static ServiceTask currentTask;

    // FIXME remove ?
    public static void setCurrentTask(ServiceTask task) {
        if (currentTask == null) {
            currentTask = task;
        } else {
            LOG.error("setCurrentTask: Cannot replace current task");
        }
    }


    public static void finishCurrentTask(ServiceTask task) {
        if (task != currentTask) {
            LOG.error("finishCurrentTask: task does not match");
        }
        // hack to force deep copy of transport contents
        ServiceTransport transport = task.getServiceTransport().clone();

        if (transport.hasServiceResult()) {
            sendServiceTransportResponse(transport, transport.getServiceResult());
        }
        currentTask = null;
    }


    public static void sendServiceTransportResponse(ServiceTransport transport, ServiceResult serviceResult) {
        // get the key (hashcode) of the client who requested this
        Integer clientHashcode = transport.getSenderHashcode();
        // make a new bundle to send as the message data
        transport.setServiceResult(serviceResult);
        // FIXME
        //transport.setTransportType(RT2Const.IPC.MSG_ServiceResult);
        //rileyLinkIPCConnection.sendTransport(transport, clientHashcode);
    }


//    public static void setRileyLinkIPCConnection(RileyLinkIPCConnection rileyLinkIPCConnection) {
//        RileyLinkUtil.rileyLinkIPCConnection = rileyLinkIPCConnection;
//    }


    public static void setRileyLinkTargetFrequency(RileyLinkTargetFrequency rileyLinkTargetFrequency) {
        RileyLinkUtil.rileyLinkTargetFrequency = rileyLinkTargetFrequency;
    }


    public static RileyLinkTargetFrequency getRileyLinkTargetFrequency() {
        return RileyLinkUtil.rileyLinkTargetFrequency;
    }

    public static MedtronicPumpStatus getPumpStatus() {
        return RileyLinkUtil.pumpStatus;
    }
}

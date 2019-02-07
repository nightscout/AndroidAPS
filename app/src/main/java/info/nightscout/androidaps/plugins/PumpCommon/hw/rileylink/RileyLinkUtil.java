package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.encoding.Encoding4b6b;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.encoding.Encoding4b6bGeoff;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.data.BleAdvertisedData;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.RileyLinkService;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.data.ServiceNotification;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.data.ServiceResult;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.tasks.ServiceTask;
import info.nightscout.androidaps.plugins.PumpCommon.ui.RileyLinkSelectPreference;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventMedtronicDeviceStatusChange;

/**
 * Created by andy on 17/05/2018.
 */

public class RileyLinkUtil {

    private static final Logger LOG = LoggerFactory.getLogger(RileyLinkUtil.class);
    protected static List<RLHistoryItem> historyRileyLink = new ArrayList<>();
    protected static RileyLinkCommunicationManager rileyLinkCommunicationManager;
    static ServiceTask currentTask;
    private static Context context;
    private static RileyLinkBLE rileyLinkBLE;
    private static RileyLinkServiceData rileyLinkServiceData;
    // private static PumpType pumpType;
    // private static MedtronicPumpStatus medtronicPumpStatus;
    private static RileyLinkService rileyLinkService;
    // private static RileyLinkIPCConnection rileyLinkIPCConnection;
    // private static MedtronicDeviceType medtronicPumpModel;
    // BAD dependencies in Classes: RileyLinkService
    private static RileyLinkTargetFrequency rileyLinkTargetFrequency;

    // Broadcasts: RileyLinkBLE, RileyLinkService,
    // private static RileyLinkIPCConnection rileyLinkIPCConnection;
    private static RileyLinkTargetDevice targetDevice;
    private static RileyLinkEncodingType encoding;
    private static RileyLinkSelectPreference rileyLinkSelectPreference;
    private static Encoding4b6b encoding4b6b;
    private static RileyLinkFirmwareVersion firmwareVersion;


    public static void setContext(Context contextIn) {
        RileyLinkUtil.context = contextIn;
    }


    public static RileyLinkEncodingType getEncoding() {
        return encoding;

    }


    public static void setEncoding(RileyLinkEncodingType encoding) {
        RileyLinkUtil.encoding = encoding;

        if (encoding == RileyLinkEncodingType.FourByteSixByteLocal) {
            RileyLinkUtil.encoding4b6b = new Encoding4b6bGeoff();
        }
    }


    public static void sendBroadcastMessage(String message) {
        if (context != null) {
            Intent intent = new Intent(message);
            LocalBroadcastManager.getInstance(RileyLinkUtil.context).sendBroadcast(intent);
        }
    }


    public static void setServiceState(RileyLinkServiceState newState) {
        setServiceState(newState, null);
    }


    public static RileyLinkError getError() {
        return RileyLinkUtil.rileyLinkServiceData.errorCode;
    }


    public static RileyLinkServiceState getServiceState() {
        return workWithServiceState(null, null, false);
    }


    public static void setServiceState(RileyLinkServiceState newState, RileyLinkError errorCode) {
        workWithServiceState(newState, errorCode, true);
    }


    private static synchronized RileyLinkServiceState workWithServiceState(RileyLinkServiceState newState,
            RileyLinkError errorCode, boolean set) {

        if (set) {

            RileyLinkUtil.rileyLinkServiceData.serviceState = newState;
            RileyLinkUtil.rileyLinkServiceData.errorCode = errorCode;

            LOG.warn("RileyLink State Changed: {} {}", newState, errorCode == null ? "" : " - Error State: "
                + errorCode.name());

            RileyLinkUtil.historyRileyLink.add(new RLHistoryItem(RileyLinkUtil.rileyLinkServiceData.serviceState,
                RileyLinkUtil.rileyLinkServiceData.errorCode, targetDevice));
            MainApp.bus().post(new EventMedtronicDeviceStatusChange(newState, errorCode));
            return null;

        } else {

            return RileyLinkUtil.rileyLinkServiceData.serviceState;

        }

    }


    public static RileyLinkBLE getRileyLinkBLE() {
        return RileyLinkUtil.rileyLinkBLE;
    }


    public static void setRileyLinkBLE(RileyLinkBLE rileyLinkBLEIn) {
        RileyLinkUtil.rileyLinkBLE = rileyLinkBLEIn;
    }


    public static RileyLinkServiceData getRileyLinkServiceData() {
        return RileyLinkUtil.rileyLinkServiceData;
    }


    public static void setRileyLinkServiceData(RileyLinkServiceData rileyLinkServiceData) {
        RileyLinkUtil.rileyLinkServiceData = rileyLinkServiceData;
    }


    public static boolean hasPumpBeenTunned() {
        return RileyLinkUtil.rileyLinkServiceData.tuneUpDone;
    }


    public static RileyLinkService getRileyLinkService() {
        return RileyLinkUtil.rileyLinkService;
    }


    public static void setRileyLinkService(RileyLinkService rileyLinkService) {
        RileyLinkUtil.rileyLinkService = rileyLinkService;
    }


    public static RileyLinkCommunicationManager getRileyLinkCommunicationManager() {
        return RileyLinkUtil.rileyLinkCommunicationManager;
    }


    public static void setRileyLinkCommunicationManager(RileyLinkCommunicationManager rileyLinkCommunicationManager) {
        RileyLinkUtil.rileyLinkCommunicationManager = rileyLinkCommunicationManager;
    }


    public static boolean sendNotification(ServiceNotification notification, Integer clientHashcode) {
        // return RileyLinkUtil.rileyLinkIPCConnection.sendNotification(notification, clientHashcode);
        return false;
    }


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
        // transport.setTransportType(RT2Const.IPC.MSG_ServiceResult);
        // rileyLinkIPCConnection.sendTransport(transport, clientHashcode);
    }


    // public static void setRileyLinkIPCConnection(RileyLinkIPCConnection rileyLinkIPCConnection) {
    // RileyLinkUtil.rileyLinkIPCConnection = rileyLinkIPCConnection;
    // }

    // public static RileyLinkIPCConnection getRileyLinkIPCConnection() {
    // return RileyLinkUtil.rileyLinkIPCConnection;
    // }

    public static RileyLinkTargetFrequency getRileyLinkTargetFrequency() {
        return RileyLinkUtil.rileyLinkTargetFrequency;
    }


    public static void setRileyLinkTargetFrequency(RileyLinkTargetFrequency rileyLinkTargetFrequency) {
        RileyLinkUtil.rileyLinkTargetFrequency = rileyLinkTargetFrequency;
    }


    public static boolean isSame(Double d1, Double d2) {
        double diff = d1 - d2;

        return (Math.abs(diff) <= 0.000001);
    }


    @Deprecated
    public static BleAdvertisedData parseAdertisedData(byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();
        String name = null;
        if (advertisedData == null) {
            return new BleAdvertisedData(uuids, name);
        }

        ByteBuffer buffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.remaining() > 2) {
            byte length = buffer.get();
            if (length == 0)
                break;

            byte type = buffer.get();
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (length >= 2) {
                        uuids
                            .add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", buffer.getShort())));
                        length -= 2;
                    }
                    break;
                case 0x06: // Partial list of 128-bit UUIDs
                case 0x07: // Complete list of 128-bit UUIDs
                    while (length >= 16) {
                        long lsb = buffer.getLong();
                        long msb = buffer.getLong();
                        uuids.add(new UUID(msb, lsb));
                        length -= 16;
                    }
                    break;
                case 0x09:
                    byte[] nameBytes = new byte[length - 1];
                    buffer.get(nameBytes);
                    try {
                        name = new String(nameBytes, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    buffer.position(buffer.position() + length - 1);
                    break;
            }
        }
        return new BleAdvertisedData(uuids, name);
    }


    public static List<RLHistoryItem> getRileyLinkHistory() {
        return historyRileyLink;
    }


    public static RileyLinkTargetDevice getTargetDevice() {
        return targetDevice;
    }


    public static void setTargetDevice(RileyLinkTargetDevice targetDevice) {
        RileyLinkUtil.targetDevice = targetDevice;
    }


    public static void setRileyLinkSelectPreference(RileyLinkSelectPreference rileyLinkSelectPreference) {

        RileyLinkUtil.rileyLinkSelectPreference = rileyLinkSelectPreference;
    }


    public static RileyLinkSelectPreference getRileyLinkSelectPreference() {

        return rileyLinkSelectPreference;
    }


    public static Encoding4b6b getEncoding4b6b() {
        return RileyLinkUtil.encoding4b6b;
    }


    public static void setFirmwareVersion(RileyLinkFirmwareVersion firmwareVersion) {
        RileyLinkUtil.firmwareVersion = firmwareVersion;
    }


    public static RileyLinkFirmwareVersion getFirmwareVersion() {
        return firmwareVersion;
    }
}

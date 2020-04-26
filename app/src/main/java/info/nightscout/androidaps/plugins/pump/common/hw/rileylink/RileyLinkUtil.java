package info.nightscout.androidaps.plugins.pump.common.hw.rileylink;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkBLE;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6b;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.encoding.Encoding4b6bGeoff;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.BleAdvertisedData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkService;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceNotification;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceResult;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTask;
import info.nightscout.androidaps.plugins.pump.common.ui.RileyLinkSelectPreference;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicDeviceStatusChange;

/**
 * Created by andy on 17/05/2018.
 */

@Singleton
public class RileyLinkUtil {

    public List<RLHistoryItem> historyRileyLink = new ArrayList<>();
    public RileyLinkCommunicationManager rileyLinkCommunicationManager;
    static ServiceTask currentTask;
    private RileyLinkBLE rileyLinkBLE;
    private RileyLinkServiceData rileyLinkServiceData;
    private RileyLinkService rileyLinkService;
    private RileyLinkTargetFrequency rileyLinkTargetFrequency;

    private RileyLinkTargetDevice targetDevice;
    private RileyLinkEncodingType encoding;
    private RileyLinkSelectPreference rileyLinkSelectPreference;
    private Encoding4b6b encoding4b6b;
    private RileyLinkFirmwareVersion firmwareVersion;


    @NotNull private final Context context;
    @NotNull private final AAPSLogger aapsLogger;
    @NotNull private final RxBusWrapper rxBus;

    @Inject
    public RileyLinkUtil(
            Context context,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus

    ) {
        this.context = context;
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        instance = this;
    }

    private static RileyLinkUtil instance;

    // TODO: replace by injection
    @Deprecated
    public static RileyLinkUtil getInstance() {
        if (instance == null) throw new IllegalStateException("RileyLinkUtil not initialized");
        return instance;
    }

    public RileyLinkEncodingType getEncoding() {
        return encoding;
    }


    public void setEncoding(RileyLinkEncodingType encoding) {
        this.encoding = encoding;

        if (encoding == RileyLinkEncodingType.FourByteSixByteLocal) {
            this.encoding4b6b = new Encoding4b6bGeoff();
        }
    }


    public void sendBroadcastMessage(String message) {
        Intent intent = new Intent(message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }


    public void setServiceState(RileyLinkServiceState newState) {
        setServiceState(newState, null);
    }


    public RileyLinkError getError() {
        if (rileyLinkServiceData != null)
            return rileyLinkServiceData.errorCode;
        else
            return null;
    }


    public RileyLinkServiceState getServiceState() {
        return workWithServiceState(null, null, false);
    }


    public void setServiceState(RileyLinkServiceState newState, RileyLinkError errorCode) {
        workWithServiceState(newState, errorCode, true);
    }


    private synchronized RileyLinkServiceState workWithServiceState(RileyLinkServiceState newState,
                                                                    RileyLinkError errorCode, boolean set) {

        if (set) {

            rileyLinkServiceData.serviceState = newState;
            rileyLinkServiceData.errorCode = errorCode;

            aapsLogger.info(LTag.PUMP, "RileyLink State Changed: {} {}", newState, errorCode == null ? "" : " - Error State: "
                    + errorCode.name());

            historyRileyLink.add(new RLHistoryItem(rileyLinkServiceData.serviceState,
                    rileyLinkServiceData.errorCode, targetDevice));
            rxBus.send(new EventMedtronicDeviceStatusChange(newState, errorCode));
            return null;

        } else {
            return (rileyLinkServiceData == null || rileyLinkServiceData.serviceState == null) ? //
                    RileyLinkServiceState.NotStarted
                    : rileyLinkServiceData.serviceState;
        }

    }


    public RileyLinkBLE getRileyLinkBLE() {
        return rileyLinkBLE;
    }


    public void setRileyLinkBLE(RileyLinkBLE rileyLinkBLEIn) {
        rileyLinkBLE = rileyLinkBLEIn;
    }


    public RileyLinkServiceData getRileyLinkServiceData() {
        return rileyLinkServiceData;
    }


    public void setRileyLinkServiceData(RileyLinkServiceData rileyLinkServiceData) {
        this.rileyLinkServiceData = rileyLinkServiceData;
    }


    public boolean hasPumpBeenTunned() {
        return rileyLinkServiceData.tuneUpDone;
    }


    public RileyLinkService getRileyLinkService() {
        return rileyLinkService;
    }


    public void setRileyLinkService(RileyLinkService rileyLinkService) {
        this.rileyLinkService = rileyLinkService;
    }


    public RileyLinkCommunicationManager getRileyLinkCommunicationManager() {
        return rileyLinkCommunicationManager;
    }


    void setRileyLinkCommunicationManager(RileyLinkCommunicationManager rileyLinkCommunicationManager) {
        this.rileyLinkCommunicationManager = rileyLinkCommunicationManager;
    }


    public static boolean sendNotification(ServiceNotification notification, Integer clientHashcode) {
        return false;
    }


    // FIXME remove ?
    public static void setCurrentTask(ServiceTask task) {
        if (currentTask == null) {
            currentTask = task;
        } else {
            //LOG.error("setCurrentTask: Cannot replace current task");
        }
    }


    public static void finishCurrentTask(ServiceTask task) {
        if (task != currentTask) {
            //LOG.error("finishCurrentTask: task does not match");
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


    public RileyLinkTargetFrequency getRileyLinkTargetFrequency() {
        return rileyLinkTargetFrequency;
    }


    public void setRileyLinkTargetFrequency(RileyLinkTargetFrequency rileyLinkTargetFrequency) {
        this.rileyLinkTargetFrequency = rileyLinkTargetFrequency;
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
                    name = new String(nameBytes, StandardCharsets.UTF_8);
                    break;
                default:
                    buffer.position(buffer.position() + length - 1);
                    break;
            }
        }
        return new BleAdvertisedData(uuids, name);
    }


    public List<RLHistoryItem> getRileyLinkHistory() {
        return historyRileyLink;
    }


    public RileyLinkTargetDevice getTargetDevice() {
        return targetDevice;
    }


    public void setTargetDevice(RileyLinkTargetDevice targetDevice) {
        this.targetDevice = targetDevice;
    }


    public void setRileyLinkSelectPreference(RileyLinkSelectPreference rileyLinkSelectPreference) {
        this.rileyLinkSelectPreference = rileyLinkSelectPreference;
    }


    public RileyLinkSelectPreference getRileyLinkSelectPreference() {
        return rileyLinkSelectPreference;
    }


    public Encoding4b6b getEncoding4b6b() {
        return encoding4b6b;
    }


    public void setFirmwareVersion(RileyLinkFirmwareVersion firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }


    public RileyLinkFirmwareVersion getFirmwareVersion() {
        return firmwareVersion;
    }
}

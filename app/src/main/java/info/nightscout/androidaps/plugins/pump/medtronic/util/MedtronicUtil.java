package info.nightscout.androidaps.plugins.pump.medtronic.util;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.jetbrains.annotations.NotNull;
import org.joda.time.LocalTime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.PumpSettingDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BatteryType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicNotificationType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.medtronic.service.RileyLinkMedtronicService;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by andy on 5/9/18.
 */

@Singleton
public class MedtronicUtil {

    private int ENVELOPE_SIZE = 4; // 0xA7 S1 S2 S3 CMD PARAM_COUNT [PARAMS]
    int CRC_SIZE = 1;
    private boolean lowLevelDebug = true;
    private PumpDeviceState pumpDeviceState;
    private MedtronicDeviceType medtronicPumpModel;
    private RileyLinkMedtronicService medtronicService;
    @Deprecated // TODO remove this reference
    private MedtronicPumpStatus medtronicPumpStatus;
    private MedtronicCommandType currentCommand;
    private Map<String, PumpSettingDTO> settings;
    private int BIG_FRAME_LENGTH = 65;
    private int doneBit = 1 << 7;
    private ClockDTO pumpTime;
    public Gson gsonInstance = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    public Gson gsonInstanceCore = new GsonBuilder().create();
    private BatteryType batteryType = BatteryType.None;

    @NotNull private final AAPSLogger aapsLogger;
    @NotNull private final RxBusWrapper rxBus;
    @NotNull private final RileyLinkUtil rileyLinkUtil;

    @Inject
    public MedtronicUtil(
            Context context,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            RileyLinkUtil rileyLinkUtil

    ) {
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.rileyLinkUtil = rileyLinkUtil;
        instance = this;
    }

    private static MedtronicUtil instance;

    // TODO: replace by injection
    @Deprecated
    public static MedtronicUtil getInstance() {
        if (instance == null) throw new IllegalStateException("MedtronicUtil not initialized");
        return instance;
    }

    public Gson getGsonInstance() {
        return gsonInstance;
    }


    public Gson getGsonInstanceCore() {
        return gsonInstanceCore;
    }


    public LocalTime getTimeFrom30MinInterval(int interval) {
        if (interval % 2 == 0) {
            return new LocalTime(interval / 2, 0);
        } else {
            return new LocalTime((interval - 1) / 2, 30);
        }
    }


    public int getIntervalFromMinutes(int minutes) {
        return minutes / 30;
    }


    public int makeUnsignedShort(int b2, int b1) {
        int k = (b2 & 0xff) << 8 | b1 & 0xff;
        return k;
    }

    public boolean isMedtronicPump() {
        return MedtronicPumpPlugin.getPlugin().isEnabled(PluginType.PUMP);
        //return ConfigBuilderPlugin.getPlugin().getActivePump().deviceID().equals("Medtronic");
    }


    public byte[] getByteArrayFromUnsignedShort(int shortValue, boolean returnFixedSize) {
        byte highByte = (byte) (shortValue >> 8 & 0xFF);
        byte lowByte = (byte) (shortValue & 0xFF);

        if (highByte > 0) {
            return createByteArray(highByte, lowByte);
        } else {
            return returnFixedSize ? createByteArray(highByte, lowByte) : createByteArray(lowByte);
        }

    }


    public byte[] createByteArray(byte... data) {
        return data;
    }


    public byte[] createByteArray(List<Byte> data) {

        byte[] array = new byte[data.size()];

        for (int i = 0; i < data.size(); i++) {
            array[i] = data.get(i);
        }

        return array;
    }


    public double decodeBasalInsulin(int i, int j) {
        return decodeBasalInsulin(makeUnsignedShort(i, j));
    }


    public double decodeBasalInsulin(int i) {
        return (double) i / 40.0d;
    }


    public byte[] getBasalStrokes(double amount) {
        return getBasalStrokes(amount, false);
    }


    public byte[] getBasalStrokes(double amount, boolean returnFixedSize) {
        return getStrokes(amount, 40, returnFixedSize);
    }


    public int getBasalStrokesInt(double amount) {
        return getStrokesInt(amount, 40);
    }


    public byte[] getBolusStrokes(double amount) {

        int strokesPerUnit = medtronicPumpModel.getBolusStrokes();

        int length;
        int scrollRate;

        if (strokesPerUnit >= 40) {
            length = 2;

            // 40-stroke pumps scroll faster for higher unit values

            if (amount > 10)
                scrollRate = 4;
            else if (amount > 1)
                scrollRate = 2;
            else
                scrollRate = 1;

        } else {
            length = 1;
            scrollRate = 1;
        }

        int strokes = (int) (amount * ((strokesPerUnit * 1.0d) / (scrollRate * 1.0d))) * scrollRate;

        byte[] body = ByteUtil.fromHexString(String.format("%02x%0" + (2 * length) + "x", length, strokes));

        return body;
    }


    public byte[] createCommandBody(byte[] input) {

        return ByteUtil.concat((byte) input.length, input);
    }


    public byte[] getStrokes(double amount, int strokesPerUnit, boolean returnFixedSize) {

        int strokes = getStrokesInt(amount, strokesPerUnit);

        return getByteArrayFromUnsignedShort(strokes, returnFixedSize);

    }


    public int getStrokesInt(double amount, int strokesPerUnit) {

        int length = 1;
        int scrollRate = 1;

        if (strokesPerUnit >= 40) {
            length = 2;

            // 40-stroke pumps scroll faster for higher unit values
            if (amount > 10)
                scrollRate = 4;
            else if (amount > 1)
                scrollRate = 2;
        }

        int strokes = (int) (amount * (strokesPerUnit / (scrollRate * 1.0d)));

        strokes *= scrollRate;

        return strokes;

    }


    public void sendNotification(MedtronicNotificationType notificationType, ResourceHelper resourceHelper, RxBusWrapper rxBus) {
        Notification notification = new Notification( //
                notificationType.getNotificationType(), //
                resourceHelper.gs(notificationType.getResourceId()), //
                notificationType.getNotificationUrgency());
        rxBus.send(new EventNewNotification(notification));
    }


    public void sendNotification(MedtronicNotificationType notificationType, ResourceHelper resourceHelper, RxBusWrapper rxBus, Object... parameters) {
        Notification notification = new Notification( //
                notificationType.getNotificationType(), //
                resourceHelper.gs(notificationType.getResourceId(), parameters), //
                notificationType.getNotificationUrgency());
        rxBus.send(new EventNewNotification(notification));
    }


    public void dismissNotification(MedtronicNotificationType notificationType, RxBusWrapper rxBus) {
        rxBus.send(new EventDismissNotification(notificationType.getNotificationType()));
    }


//    public byte[] buildCommandPayload(MessageType commandType, byte[] parameters) {
//        return buildCommandPayload(commandType.getValue(), parameters);
//    }


    public byte[] buildCommandPayload(MedtronicCommandType commandType, byte[] parameters) {
        return buildCommandPayload((byte) commandType.commandCode, parameters);
    }


    public byte[] buildCommandPayload(byte commandType, byte[] parameters) {
        // A7 31 65 51 C0 00 52

        byte commandLength = (byte) (parameters == null ? 2 : 2 + parameters.length);

        ByteBuffer sendPayloadBuffer = ByteBuffer.allocate(ENVELOPE_SIZE + commandLength); // + CRC_SIZE
        sendPayloadBuffer.order(ByteOrder.BIG_ENDIAN);

        byte[] serialNumberBCD = rileyLinkUtil.getRileyLinkServiceData().pumpIDBytes;

        sendPayloadBuffer.put((byte) 0xA7);
        sendPayloadBuffer.put(serialNumberBCD[0]);
        sendPayloadBuffer.put(serialNumberBCD[1]);
        sendPayloadBuffer.put(serialNumberBCD[2]);

        sendPayloadBuffer.put(commandType);

        if (parameters == null) {
            sendPayloadBuffer.put((byte) 0x00);
        } else {
            sendPayloadBuffer.put((byte) parameters.length); // size

            for (byte val : parameters) {
                sendPayloadBuffer.put(val);
            }
        }

        byte[] payload = sendPayloadBuffer.array();

        aapsLogger.debug(LTag.PUMPBTCOMM, "buildCommandPayload [{}]", ByteUtil.shortHexString(payload));

        // int crc = computeCRC8WithPolynomial(payload, 0, payload.length - 1);

        // LOG.info("crc: " + crc);

        // sendPayloadBuffer.put((byte) crc);

        return sendPayloadBuffer.array();
    }


    // Note: at the moment supported only for 24 items, if you will use it for more than
    // that you will need to add
    public List<List<Byte>> getBasalProfileFrames(byte[] data) {

        boolean done = false;
        int start = 0;
        int frame = 1;

        List<List<Byte>> frames = new ArrayList<>();
        boolean lastFrame = false;

        do {
            int frameLength = BIG_FRAME_LENGTH - 1;

            if (start + frameLength > data.length) {
                frameLength = data.length - start;
            }

            // System.out.println("Framelength: " + frameLength);

            byte[] substring = ByteUtil.substring(data, start, frameLength);

            // System.out.println("Subarray: " + ByteUtil.getCompactString(substring));
            // System.out.println("Subarray Lenths: " + substring.length);

            List<Byte> frameData = ByteUtil.getListFromByteArray(substring);

            if (isEmptyFrame(frameData)) {
                byte b = (byte) frame;
                // b |= 0x80;
                b |= 0b1000_0000;
                // b |= doneBit;

                frameData.add(0, b);

                checkAndAppenLastFrame(frameData);

                lastFrame = true;

                done = true;
            } else {
                frameData.add(0, (byte) frame);
            }

            // System.out.println("Subarray: " + ByteUtil.getCompactString(substring));

            frames.add(frameData);

            frame++;
            start += (BIG_FRAME_LENGTH - 1);

            if (start == data.length) {
                done = true;
            }

        } while (!done);

        if (!lastFrame) {
            List<Byte> frameData = new ArrayList<>();

            byte b = (byte) frame;
            b |= 0b1000_0000;
            // b |= doneBit;

            frameData.add(b);

            checkAndAppenLastFrame(frameData);
        }

        return frames;

    }


    private void checkAndAppenLastFrame(List<Byte> frameData) {

        if (frameData.size() == BIG_FRAME_LENGTH)
            return;

        int missing = BIG_FRAME_LENGTH - frameData.size();

        for (int i = 0; i < missing; i++) {
            frameData.add((byte) 0x00);
        }
    }


    private boolean isEmptyFrame(List<Byte> frameData) {

        for (Byte frameDateEntry : frameData) {
            if (frameDateEntry != 0x00) {
                return false;
            }
        }

        return true;
    }


    public boolean isLowLevelDebug() {
        return lowLevelDebug;
    }


    public void setLowLevelDebug(boolean lowLevelDebug) {
        this.lowLevelDebug = lowLevelDebug;
    }


    public PumpDeviceState getPumpDeviceState() {
        return pumpDeviceState;
    }


    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.pumpDeviceState = pumpDeviceState;

        rileyLinkUtil.historyRileyLink.add(new RLHistoryItem(pumpDeviceState, RileyLinkTargetDevice.MedtronicPump));

        rxBus.send(new EventMedtronicDeviceStatusChange(pumpDeviceState));
    }


    public boolean isModelSet() {
        return medtronicPumpModel != null;
    }


    public MedtronicDeviceType getMedtronicPumpModel() {
        return medtronicPumpModel;
    }


    public void setMedtronicPumpModel(MedtronicDeviceType medtronicPumpModel) {
        this.medtronicPumpModel = medtronicPumpModel;
    }


    public MedtronicCommunicationManager getMedtronicCommunicationManager() {
        return (MedtronicCommunicationManager) rileyLinkUtil.rileyLinkCommunicationManager;
    }


    public RileyLinkMedtronicService getMedtronicService() {
        return medtronicService;
    }


    public void setMedtronicService(RileyLinkMedtronicService medtronicService) {
        this.medtronicService = medtronicService;
    }


    @Deprecated // TODO use singleton
    public MedtronicPumpStatus getPumpStatus() {
        return medtronicPumpStatus;
    }

    @Deprecated // TODO use singleton
    public void setPumpStatus(MedtronicPumpStatus medtronicPumpStatus) {
        this.medtronicPumpStatus = medtronicPumpStatus;
    }


    public MedtronicCommandType getCurrentCommand() {
        return this.currentCommand;
    }


    public void setCurrentCommand(MedtronicCommandType currentCommand) {
        this.currentCommand = currentCommand;

        if (currentCommand != null)
            rileyLinkUtil.historyRileyLink.add(new RLHistoryItem(currentCommand));

    }

    public int pageNumber;
    public Integer frameNumber;


    public void setCurrentCommand(MedtronicCommandType currentCommand, int pageNumber_, Integer frameNumber_) {
        pageNumber = pageNumber_;
        frameNumber = frameNumber_;

        if (this.currentCommand != currentCommand) {
            setCurrentCommand(currentCommand);
        }

        rxBus.send(new EventMedtronicDeviceStatusChange(pumpDeviceState));
    }


    public boolean isSame(Double d1, Double d2) {
        double diff = d1 - d2;

        return (Math.abs(diff) <= 0.000001);
    }


    public Map<String, PumpSettingDTO> getSettings() {
        return settings;
    }


    public void setSettings(Map<String, PumpSettingDTO> settings) {
        this.settings = settings;
    }


    public void setPumpTime(ClockDTO pumpTime) {
        this.pumpTime = pumpTime;
    }


    public ClockDTO getPumpTime() {
        return this.pumpTime;
    }

    public void setBatteryType(BatteryType batteryType) {
        this.batteryType = batteryType;
    }


    public BatteryType getBatteryType() {
        return this.batteryType;
    }


}

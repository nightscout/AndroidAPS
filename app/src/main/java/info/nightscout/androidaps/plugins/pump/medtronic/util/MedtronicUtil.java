package info.nightscout.androidaps.plugins.pump.medtronic.util;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.joda.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
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
import info.nightscout.androidaps.utils.OKDialog;

/**
 * Created by andy on 5/9/18.
 */

public class MedtronicUtil extends RileyLinkUtil {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);
    static int ENVELOPE_SIZE = 4; // 0xA7 S1 S2 S3 CMD PARAM_COUNT [PARAMS]
    static int CRC_SIZE = 1;
    private static boolean lowLevelDebug = true;
    private static PumpDeviceState pumpDeviceState;
    private static MedtronicDeviceType medtronicPumpModel;
    private static RileyLinkMedtronicService medtronicService;
    private static MedtronicPumpStatus medtronicPumpStatus;
    private static MedtronicCommandType currentCommand;
    private static Map<String, PumpSettingDTO> settings;
    private static int BIG_FRAME_LENGTH = 65;
    private static int doneBit = 1 << 7;
    private static ClockDTO pumpTime;
    public static Gson gsonInstance = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    public static Gson gsonInstancePretty = new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting().create();
    private static BatteryType batteryType = BatteryType.None;


    public static Gson getGsonInstance() {
        return gsonInstance;
    }

    public static Gson getGsonInstancePretty() {
        return gsonInstancePretty;
    }


    public static LocalTime getTimeFrom30MinInterval(int interval) {
        if (interval % 2 == 0) {
            return new LocalTime(interval / 2, 0);
        } else {
            return new LocalTime((interval - 1) / 2, 30);
        }
    }


    public static int getIntervalFromMinutes(int minutes) {
        return minutes / 30;
    }


    public static int makeUnsignedShort(int b2, int b1) {
        int k = (b2 & 0xff) << 8 | b1 & 0xff;
        return k;
    }

    public static boolean isMedtronicPump() {
        return MedtronicPumpPlugin.getPlugin().isEnabled(PluginType.PUMP);
        //return ConfigBuilderPlugin.getPlugin().getActivePump().deviceID().equals("Medtronic");
    }


    public static byte[] getByteArrayFromUnsignedShort(int shortValue, boolean returnFixedSize) {
        byte highByte = (byte) (shortValue >> 8 & 0xFF);
        byte lowByte = (byte) (shortValue & 0xFF);

        if (highByte > 0) {
            return createByteArray(highByte, lowByte);
        } else {
            return returnFixedSize ? createByteArray(highByte, lowByte) : createByteArray(lowByte);
        }

    }


    public static byte[] createByteArray(byte... data) {
        return data;
    }


    public static byte[] createByteArray(List<Byte> data) {

        byte[] array = new byte[data.size()];

        for (int i = 0; i < data.size(); i++) {
            array[i] = data.get(i);
        }

        return array;
    }


    public static double decodeBasalInsulin(int i, int j) {
        return decodeBasalInsulin(makeUnsignedShort(i, j));
    }


    public static double decodeBasalInsulin(int i) {
        return (double) i / 40.0d;
    }


    public static byte[] getBasalStrokes(double amount) {
        return getBasalStrokes(amount, false);
    }


    public static byte[] getBasalStrokes(double amount, boolean returnFixedSize) {
        return getStrokes(amount, 40, returnFixedSize);
    }


    public static int getBasalStrokesInt(double amount) {
        return getStrokesInt(amount, 40);
    }


    public static byte[] getBolusStrokes(double amount) {

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


    public static byte[] createCommandBody(byte[] input) {

        return ByteUtil.concat((byte) input.length, input);
    }


    public static byte[] getStrokes(double amount, int strokesPerUnit, boolean returnFixedSize) {

        int strokes = getStrokesInt(amount, strokesPerUnit);

        return getByteArrayFromUnsignedShort(strokes, returnFixedSize);

    }


    public static int getStrokesInt(double amount, int strokesPerUnit) {

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


    public static void sendNotification(MedtronicNotificationType notificationType) {
        Notification notification = new Notification( //
                notificationType.getNotificationType(), //
                MainApp.gs(notificationType.getResourceId()), //
                notificationType.getNotificationUrgency());
        RxBus.INSTANCE.send(new EventNewNotification(notification));
    }


    public static void sendNotification(MedtronicNotificationType notificationType, Object... parameters) {
        Notification notification = new Notification( //
                notificationType.getNotificationType(), //
                MainApp.gs(notificationType.getResourceId(), parameters), //
                notificationType.getNotificationUrgency());
        RxBus.INSTANCE.send(new EventNewNotification(notification));
    }


    public static void dismissNotification(MedtronicNotificationType notificationType) {
        RxBus.INSTANCE.send(new EventDismissNotification(notificationType.getNotificationType()));
    }


//    public static byte[] buildCommandPayload(MessageType commandType, byte[] parameters) {
//        return buildCommandPayload(commandType.getValue(), parameters);
//    }


    public static byte[] buildCommandPayload(MedtronicCommandType commandType, byte[] parameters) {
        return buildCommandPayload((byte) commandType.commandCode, parameters);
    }


    public static byte[] buildCommandPayload(byte commandType, byte[] parameters) {
        // A7 31 65 51 C0 00 52

        byte commandLength = (byte) (parameters == null ? 2 : 2 + parameters.length);

        ByteBuffer sendPayloadBuffer = ByteBuffer.allocate(ENVELOPE_SIZE + commandLength); // + CRC_SIZE
        sendPayloadBuffer.order(ByteOrder.BIG_ENDIAN);

        byte[] serialNumberBCD = RileyLinkUtil.getRileyLinkServiceData().pumpIDBytes;

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

        if (L.isEnabled(L.PUMPCOMM))
            LOG.debug("buildCommandPayload [{}]", ByteUtil.shortHexString(payload));

        // int crc = computeCRC8WithPolynomial(payload, 0, payload.length - 1);

        // LOG.info("crc: " + crc);

        // sendPayloadBuffer.put((byte) crc);

        return sendPayloadBuffer.array();
    }


    // Note: at the moment supported only for 24 items, if you will use it for more than
    // that you will need to add
    public static List<List<Byte>> getBasalProfileFrames(byte[] data) {

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


    private static void checkAndAppenLastFrame(List<Byte> frameData) {

        if (frameData.size() == BIG_FRAME_LENGTH)
            return;

        int missing = BIG_FRAME_LENGTH - frameData.size();

        for (int i = 0; i < missing; i++) {
            frameData.add((byte) 0x00);
        }
    }


    private static boolean isEmptyFrame(List<Byte> frameData) {

        for (Byte frameDateEntry : frameData) {
            if (frameDateEntry != 0x00) {
                return false;
            }
        }

        return true;
    }


    public static boolean isLowLevelDebug() {
        return lowLevelDebug;
    }


    public static void setLowLevelDebug(boolean lowLevelDebug) {
        MedtronicUtil.lowLevelDebug = lowLevelDebug;
    }


    public static PumpDeviceState getPumpDeviceState() {
        return pumpDeviceState;
    }


    public static void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        MedtronicUtil.pumpDeviceState = pumpDeviceState;

        historyRileyLink.add(new RLHistoryItem(pumpDeviceState, RileyLinkTargetDevice.MedtronicPump));

        RxBus.INSTANCE.send(new EventMedtronicDeviceStatusChange(pumpDeviceState));
    }


    public static boolean isModelSet() {
        return MedtronicUtil.medtronicPumpModel != null;
    }


    public static MedtronicDeviceType getMedtronicPumpModel() {
        return MedtronicUtil.medtronicPumpModel;
    }


    public static void setMedtronicPumpModel(MedtronicDeviceType medtronicPumpModel) {
        MedtronicUtil.medtronicPumpModel = medtronicPumpModel;
    }


    public static MedtronicCommunicationManager getMedtronicCommunicationManager() {
        return (MedtronicCommunicationManager) RileyLinkUtil.rileyLinkCommunicationManager;
    }


    public static RileyLinkMedtronicService getMedtronicService() {
        return MedtronicUtil.medtronicService;
    }


    public static void setMedtronicService(RileyLinkMedtronicService medtronicService) {
        MedtronicUtil.medtronicService = medtronicService;
    }


    public static MedtronicPumpStatus getPumpStatus() {
        return MedtronicUtil.medtronicPumpStatus;
    }


    public static void setPumpStatus(MedtronicPumpStatus medtronicPumpStatus) {
        MedtronicUtil.medtronicPumpStatus = medtronicPumpStatus;
    }


    public static MedtronicCommandType getCurrentCommand() {
        return MedtronicUtil.currentCommand;
    }


    public static void setCurrentCommand(MedtronicCommandType currentCommand) {
        MedtronicUtil.currentCommand = currentCommand;

        if (currentCommand != null)
            historyRileyLink.add(new RLHistoryItem(currentCommand));

    }

    public static int pageNumber;
    public static Integer frameNumber;


    public static void setCurrentCommand(MedtronicCommandType currentCommand, int pageNumber_, Integer frameNumber_) {
        pageNumber = pageNumber_;
        frameNumber = frameNumber_;

        if (MedtronicUtil.currentCommand != currentCommand) {
            setCurrentCommand(currentCommand);
        }

        RxBus.INSTANCE.send(new EventMedtronicDeviceStatusChange(pumpDeviceState));
    }


    public static boolean isSame(Double d1, Double d2) {
        double diff = d1 - d2;

        return (Math.abs(diff) <= 0.000001);
    }


    public static Map<String, PumpSettingDTO> getSettings() {
        return settings;
    }


    public static void setSettings(Map<String, PumpSettingDTO> settings) {
        MedtronicUtil.settings = settings;
    }


    public static void setPumpTime(ClockDTO pumpTime) {
        MedtronicUtil.pumpTime = pumpTime;
    }


    public static ClockDTO getPumpTime() {
        return MedtronicUtil.pumpTime;
    }

    public static void setBatteryType(BatteryType batteryType) {
        MedtronicUtil.batteryType = batteryType;
    }


    public static BatteryType getBatteryType() {
        return MedtronicUtil.batteryType;
    }


    public static void displayNotConfiguredDialog(Context context) {
        OKDialog.show(context, MainApp.gs(R.string.combo_warning),
                MainApp.gs(R.string.medtronic_error_operation_not_possible_no_configuration), null);
    }

}

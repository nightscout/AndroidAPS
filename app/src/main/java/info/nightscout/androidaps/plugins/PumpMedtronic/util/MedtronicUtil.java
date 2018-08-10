package info.nightscout.androidaps.plugins.PumpMedtronic.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.HexDump;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.message.MessageType;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.PumpSettingDTO;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventMedtronicDeviceStatusChange;
import info.nightscout.androidaps.plugins.PumpMedtronic.service.RileyLinkMedtronicService;

/**
 * Created by andy on 5/9/18.
 */

public class MedtronicUtil extends RileyLinkUtil {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicUtil.class);
    static int ENVELOPE_SIZE = 4; // 0xA7 S1 S2 S3 CMD PARAM_COUNT [PARAMS]
    static int CRC_SIZE = 1;
    private static boolean lowLevelDebug = true;
    private static PumpDeviceState pumpDeviceState;
    private static MedtronicDeviceType medtronicPumpModel;
    private static RileyLinkMedtronicService medtronicService;
    private static MedtronicPumpStatus medtronicPumpStatus;
    private static MedtronicCommandType currentCommand;
    private static Map<String, PumpSettingDTO> settings;


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


    public static byte[] getByteArrayFromUnsignedShort(int shortValue, boolean returnFixedSize) {
        byte highByte = (byte)(shortValue >> 8 & 0xFF);
        byte lowByte = (byte)(shortValue & 0xFF);

        if (highByte > 0) {
            return createByteArray(lowByte, highByte);
        } else {
            return returnFixedSize ? createByteArray(lowByte, highByte) : createByteArray(lowByte);
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
        return (double)i / 40.0d;
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
        return getStrokes(amount, medtronicPumpModel.getBolusStrokes(), false);
    }


    public static byte[] createCommandBody(byte[] input) {

        return ByteUtil.concat((byte)input.length, input);
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

        int strokes = (int)(amount * (strokesPerUnit / (scrollRate * 1.0d)));

        strokes *= scrollRate;

        return strokes;

    }


    public static byte[] buildCommandPayload(MessageType commandType, byte[] parameters) {
        return buildCommandPayload(commandType.getValue(), parameters);
    }


    public static byte[] buildCommandPayload(MedtronicCommandType commandType, byte[] parameters) {
        return buildCommandPayload((byte)commandType.commandCode, parameters);
    }


    public static byte[] buildCommandPayload(byte commandType, byte[] parameters) {
        // A7 31 65 51 C0 00 52

        byte commandLength = (byte)(parameters == null ? 2 : 2 + parameters.length);

        ByteBuffer sendPayloadBuffer = ByteBuffer.allocate(ENVELOPE_SIZE + commandLength); // + CRC_SIZE
        sendPayloadBuffer.order(ByteOrder.BIG_ENDIAN);

        byte[] serialNumberBCD = RileyLinkUtil.getRileyLinkServiceData().pumpIDBytes;

        sendPayloadBuffer.put((byte)0xA7);
        sendPayloadBuffer.put(serialNumberBCD[0]);
        sendPayloadBuffer.put(serialNumberBCD[1]);
        sendPayloadBuffer.put(serialNumberBCD[2]);

        sendPayloadBuffer.put(commandType);

        if (parameters == null) {
            sendPayloadBuffer.put((byte)0x00);
        } else {
            sendPayloadBuffer.put((byte)parameters.length); // size

            for (byte val : parameters) {
                sendPayloadBuffer.put(val);
            }
        }

        byte[] payload = sendPayloadBuffer.array();

        LOG.info(HexDump.toHexStringDisplayable(payload));

        // int crc = computeCRC8WithPolynomial(payload, 0, payload.length - 1);

        // LOG.info("crc: " + crc);

        // sendPayloadBuffer.put((byte) crc);

        return sendPayloadBuffer.array();
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

        MainApp.bus().post(new EventMedtronicDeviceStatusChange(pumpDeviceState));
    }


    public static boolean isModelSet() {
        return MedtronicUtil.medtronicPumpModel != null;
    }


    public static MedtronicDeviceType getMedtronicPumpModel() {
        return MedtronicUtil.medtronicPumpModel;
    }


    public static void setMedtronicPumpModel(MedtronicDeviceType medtronicPumpModel) {
        if (medtronicPumpModel != null && medtronicPumpModel != MedtronicDeviceType.Unknown_Device) {
            MedtronicUtil.medtronicPumpModel = medtronicPumpModel;
        }
    }


    public static MedtronicCommunicationManager getMedtronicCommunicationManager() {
        return (MedtronicCommunicationManager)RileyLinkUtil.rileyLinkCommunicationManager;
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
}

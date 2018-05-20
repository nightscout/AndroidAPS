package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble;

import android.content.Context;
import android.os.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.GattAttributes;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.RFSpyResponse;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.RadioPacket;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.RadioResponse;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.operations.BLECommOperationResult;
import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.StringUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.ThreadUtil;

/**
 * Created by geoff on 5/26/16.
 */
public class RFSpy {

    private static final Logger LOG = LoggerFactory.getLogger(RFSpy.class);

    public static final byte RFSPY_GET_STATE = 1;
    public static final byte RFSPY_GET_VERSION = 2;
    public static final byte RFSPY_GET_PACKET = 3; // aka Listen, receive
    public static final byte RFSPY_SEND = 4;
    public static final byte RFSPY_SEND_AND_LISTEN = 5;
    public static final byte RFSPY_UPDATE_REGISTER = 6;
    public static final byte RFSPY_RESET = 7;

    public static final long RILEYLINK_FREQ_XTAL = 24000000;

    public static final byte CC111X_REG_FREQ2 = 0x09;
    public static final byte CC111X_REG_FREQ1 = 0x0A;
    public static final byte CC111X_REG_FREQ0 = 0x0B;
    public static final byte CC111X_MDMCFG4 = 0x0C;
    public static final byte CC111X_MDMCFG3 = 0x0D;
    public static final byte CC111X_MDMCFG2 = 0x0E;
    public static final byte CC111X_MDMCFG1 = 0x0F;
    public static final byte CC111X_MDMCFG0 = 0x10;
    public static final byte CC111X_AGCCTRL2 = 0x17;
    public static final byte CC111X_AGCCTRL1 = 0x18;
    public static final byte CC111X_AGCCTRL0 = 0x19;
    public static final byte CC111X_FREND1 = 0x1A;
    public static final byte CC111X_FREND0 = 0x1B;

    public static final int EXPECTED_MAX_BLUETOOTH_LATENCY_MS = 1500;

    private RileyLinkBLE rileyLinkBle;
    private RFSpyReader reader;
    private Context context;
    UUID radioServiceUUID = UUID.fromString(GattAttributes.SERVICE_RADIO);
    UUID radioDataUUID = UUID.fromString(GattAttributes.CHARA_RADIO_DATA);
    UUID radioVersionUUID = UUID.fromString(GattAttributes.CHARA_RADIO_VERSION);
    UUID responseCountUUID = UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT);


    public RFSpy(Context context, RileyLinkBLE rileyLinkBle) {
        this.context = context;
        this.rileyLinkBle = rileyLinkBle;
        reader = new RFSpyReader(context, rileyLinkBle);
    }

    // Call this after the RL services are discovered.
    // Starts an async task to read when data is available
    public void startReader() {
        rileyLinkBle.registerRadioResponseCountNotification(new Runnable() {
            @Override
            public void run() {
                newDataIsAvailable();
            }
        });
        reader.start();
    }

    // Call this from the "response count" notification handler.
    public void newDataIsAvailable() {
        // pass the message to the reader (which should be internal to RFSpy)
        reader.newDataIsAvailable();
    }

    // This gets the version from the BLE113, not from the CC1110.
    // I.e., this gets the version from the BLE interface, not from the radio.
    public String getVersion() {
        BLECommOperationResult result = rileyLinkBle.readCharacteristic_blocking(radioServiceUUID, radioVersionUUID);
        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
            return StringUtil.fromBytes(result.value);
        } else {
            LOG.error("getVersion failed with code: " + result.resultCode);
            return "(null)";
        }
    }

    // The caller has to know how long the RFSpy will be busy with what was sent to it.
    private RFSpyResponse writeToData(byte[] bytes, int responseTimeout_ms) {
        SystemClock.sleep(100);
        // FIXME drain read queue?
        byte[] junkInBuffer = reader.poll(0);

        while (junkInBuffer != null) {
            LOG.warn(ThreadUtil.sig() + "writeToData: draining read queue, found this: " + ByteUtil.shortHexString(junkInBuffer));
            junkInBuffer = reader.poll(0);
        }

        // prepend length, and send it.
        byte[] prepended = ByteUtil.concat(new byte[]{(byte) (bytes.length)}, bytes);
        BLECommOperationResult writeCheck = rileyLinkBle.writeCharacteristic_blocking(radioServiceUUID, radioDataUUID, prepended);
        if (writeCheck.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
            LOG.error("BLE Write operation failed, code=" + writeCheck.resultCode);
            return new RFSpyResponse(); // will be a null (invalid) response
        }
        SystemClock.sleep(100);
        //Log.i(TAG,ThreadUtil.sig()+String.format(" writeToData:(timeout %d) %s",(responseTimeout_ms),ByteUtil.shortHexString(prepended)));
        byte[] rawResponse = reader.poll(responseTimeout_ms);
        RFSpyResponse resp = new RFSpyResponse(rawResponse);
        if (rawResponse == null) {
            LOG.error("writeToData: No response from RileyLink");
        } else {
            if (resp.wasInterrupted()) {
                LOG.error("writeToData: RileyLink was interrupted");
            } else if (resp.wasTimeout()) {
                LOG.error("writeToData: RileyLink reports timeout");
            } else if (resp.isOK()) {
                LOG.warn("writeToData: RileyLink reports OK");
            } else {
                if (resp.looksLikeRadioPacket()) {
                    RadioResponse radioResp = resp.getRadioResponse();
                    byte[] responsePayload = radioResp.getPayload();
                    LOG.info("writeToData: decoded radio response is " + ByteUtil.shortHexString(responsePayload));
                }
                //Log.i(TAG, "writeToData: raw response is " + ByteUtil.shortHexString(rawResponse));
            }
        }
        return resp;
    }

    public RFSpyResponse getRadioVersion() {
        RFSpyResponse resp = writeToData(new byte[]{RFSPY_GET_VERSION}, 1000);
        if (resp == null) {
            LOG.error("getRadioVersion returned null");
        }
        /*
        Log.d(TAG,"checking response count");
        BLECommOperationResult checkRC = rileyLinkBle.readCharacteristic_blocking(radioServiceUUID,responseCountUUID);
        if (checkRC.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
            Log.d(TAG,"Response count is: " + ByteUtil.shortHexString(checkRC.value));
        } else {
            LOG.error("Error getting response count, code is " + checkRC.resultCode);
        }
        */
        return resp;
    }

    public RFSpyResponse transmit(RadioPacket radioPacket, byte sendChannel, byte repeatCount, byte delay_ms) {
        // append checksum, encode data, send it.
        byte[] fullPacket = ByteUtil.concat(new byte[]{RFSPY_SEND, sendChannel, repeatCount, delay_ms}, radioPacket.getEncoded());
        RFSpyResponse response = writeToData(fullPacket, repeatCount * delay_ms);
        return response;
    }

    public RFSpyResponse receive(byte listenChannel, int timeout_ms, byte retryCount) {
        int receiveDelay = timeout_ms * (retryCount + 1);
        byte[] listen = {RFSPY_GET_PACKET, listenChannel,
                (byte) ((timeout_ms >> 24) & 0x0FF),
                (byte) ((timeout_ms >> 16) & 0x0FF),
                (byte) ((timeout_ms >> 8) & 0x0FF),
                (byte) (timeout_ms & 0x0FF),
                retryCount};
        return writeToData(listen, receiveDelay);
    }

    public RFSpyResponse transmitThenReceive(RadioPacket pkt, int timeout_ms) {
        return transmitThenReceive(pkt, (byte) 0, (byte) 0, (byte) 0, (byte) 0, timeout_ms, (byte) 0);
    }

    public RFSpyResponse transmitThenReceive(RadioPacket pkt, byte sendChannel, byte repeatCount, byte delay_ms, byte listenChannel, int timeout_ms, byte retryCount) {

        int sendDelay = repeatCount * delay_ms;
        int receiveDelay = timeout_ms * (retryCount + 1);
        byte[] sendAndListen = {RFSPY_SEND_AND_LISTEN, sendChannel, repeatCount, delay_ms, listenChannel,
                (byte) ((timeout_ms >> 24) & 0x0FF),
                (byte) ((timeout_ms >> 16) & 0x0FF),
                (byte) ((timeout_ms >> 8) & 0x0FF),
                (byte) (timeout_ms & 0x0FF),
                retryCount};
        byte[] fullPacket = ByteUtil.concat(sendAndListen, pkt.getEncoded());
        return writeToData(fullPacket, sendDelay + receiveDelay + EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
    }

    public RFSpyResponse updateRegister(byte addr, byte val) {
        byte[] updateRegisterPkt = new byte[]{RFSPY_UPDATE_REGISTER, addr, val};
        RFSpyResponse resp = writeToData(updateRegisterPkt, EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
        return resp;
    }

    public void setBaseFrequency(double freqMHz) {
        int value = (int) (freqMHz * 1000000 / ((double) (RILEYLINK_FREQ_XTAL) / Math.pow(2.0, 16.0)));
        updateRegister(CC111X_REG_FREQ0, (byte) (value & 0xff));
        updateRegister(CC111X_REG_FREQ1, (byte) ((value >> 8) & 0xff));
        updateRegister(CC111X_REG_FREQ2, (byte) ((value >> 16) & 0xff));
        LOG.warn("Set frequency to {}", freqMHz);
    }


//    func configureRadio(for region: PumpRegion) throws {
//        try session.resetRadioConfig()
//
//        switch region {
//        case .worldWide:
//            //try session.updateRegister(.mdmcfg4, value: 0x59)
//            try setRXFilterMode(.wide)
//            //try session.updateRegister(.mdmcfg3, value: 0x66)
//            //try session.updateRegister(.mdmcfg2, value: 0x33)
//            try session.updateRegister(.mdmcfg1, value: 0x62)
//            try session.updateRegister(.mdmcfg0, value: 0x1A)
//            try session.updateRegister(.deviatn, value: 0x13)
//        case .northAmerica:
//            //try session.updateRegister(.mdmcfg4, value: 0x99)
//            try setRXFilterMode(.narrow)
//            //try session.updateRegister(.mdmcfg3, value: 0x66)
//            //try session.updateRegister(.mdmcfg2, value: 0x33)
//            try session.updateRegister(.mdmcfg1, value: 0x61)
//            try session.updateRegister(.mdmcfg0, value: 0x7E)
//            try session.updateRegister(.deviatn, value: 0x15)
//        }
//    }

}

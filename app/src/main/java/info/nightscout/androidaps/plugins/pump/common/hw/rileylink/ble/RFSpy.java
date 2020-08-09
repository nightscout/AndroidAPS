package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble;

import android.os.SystemClock;

import java.util.UUID;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.Reset;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.RileyLinkCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SendAndListen;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SetHardwareEncoding;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.SetPreamble;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.UpdateRegister;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.GattAttributes;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RFSpyResponse;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.CC111XRegister;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RXFilterMode;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.operations.BLECommOperationResult;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.ThreadUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by geoff on 5/26/16.
 */
public class RFSpy {

    @Inject AAPSLogger aapsLogger;
    @Inject ResourceHelper resourceHelper;
    @Inject SP sp;
    @Inject RileyLinkServiceData rileyLinkServiceData;
    @Inject RileyLinkUtil rileyLinkUtil;

    private final HasAndroidInjector injector;

    private static final long RILEYLINK_FREQ_XTAL = 24000000;
    private static final int EXPECTED_MAX_BLUETOOTH_LATENCY_MS = 7500; // 1500
    public int notConnectedCount = 0;
    private RileyLinkBLE rileyLinkBle;
    private RFSpyReader reader;
    private UUID radioServiceUUID = UUID.fromString(GattAttributes.SERVICE_RADIO);
    private UUID radioDataUUID = UUID.fromString(GattAttributes.CHARA_RADIO_DATA);
    private UUID radioVersionUUID = UUID.fromString(GattAttributes.CHARA_RADIO_VERSION);
    //private UUID responseCountUUID = UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT);
    private RileyLinkFirmwareVersion firmwareVersion;
    private String bleVersion; // We don't use it so no need of sofisticated logic
    private Double currentFrequencyMHz;


    public RFSpy(HasAndroidInjector injector, RileyLinkBLE rileyLinkBle) {
        injector.androidInjector().inject(this);
        this.injector = injector;
        this.rileyLinkBle = rileyLinkBle;
        aapsLogger.debug("RileyLinkServiceData:" + rileyLinkServiceData);
        reader = new RFSpyReader(aapsLogger, rileyLinkBle);
    }


    public RileyLinkFirmwareVersion getRLVersionCached() {
        return firmwareVersion;
    }


    public String getBLEVersionCached() {
        return bleVersion;
    }


    // Call this after the RL services are discovered.
    // Starts an async task to read when data is available
    public void startReader() {
        rileyLinkBle.registerRadioResponseCountNotification(this::newDataIsAvailable);
        reader.start();
    }


    // Here should go generic RL initialisation + protocol adjustments depending on
    // firmware version
    public void initializeRileyLink() {
        bleVersion = getVersion();
        rileyLinkServiceData.firmwareVersion = getFirmwareVersion();
    }


    // Call this from the "response count" notification handler.
    private void newDataIsAvailable() {
        // pass the message to the reader (which should be internal to RFSpy)
        reader.newDataIsAvailable();
    }


    // This gets the version from the BLE113, not from the CC1110.
    // I.e., this gets the version from the BLE interface, not from the radio.
    public String getVersion() {
        BLECommOperationResult result = rileyLinkBle.readCharacteristic_blocking(radioServiceUUID, radioVersionUUID);
        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
            String version = StringUtil.fromBytes(result.value);
            aapsLogger.debug(LTag.PUMPBTCOMM, "BLE Version: " + version);
            return version;
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "getVersion failed with code: " + result.resultCode);
            return "(null)";
        }
    }

    public boolean isRileyLinkStillAvailable() {
        RileyLinkFirmwareVersion firmwareVersion = getFirmwareVersion();

        return (firmwareVersion != RileyLinkFirmwareVersion.UnknownVersion);
    }


    private RileyLinkFirmwareVersion getFirmwareVersion() {

        aapsLogger.debug(LTag.PUMPBTCOMM, "Firmware Version. Get Version - Start");

        for (int i = 0; i < 5; i++) {
            // We have to call raw version of communication to get firmware version
            // So that we can adjust other commands accordingly afterwords

            byte[] getVersionRaw = getByteArray(RileyLinkCommandType.GetVersion.code);
            byte[] response = writeToDataRaw(getVersionRaw, 5000);

            aapsLogger.debug(LTag.PUMPBTCOMM, "Firmware Version. GetVersion [response={}]", ByteUtil.shortHexString(response));

            if (response != null) { // && response[0] == (byte) 0xDD) {

                String versionString = StringUtil.fromBytes(response);

                RileyLinkFirmwareVersion version = RileyLinkFirmwareVersion.getByVersionString(StringUtil
                        .fromBytes(response));

                aapsLogger.debug(LTag.PUMPBTCOMM, "Firmware Version string: {}, resolved to {}.", versionString, version);

                if (version != RileyLinkFirmwareVersion.UnknownVersion)
                    return version;

                SystemClock.sleep(1000);
            }
        }

        aapsLogger.error(LTag.PUMPBTCOMM, "Firmware Version can't be determined. Checking with BLE Version [{}].", bleVersion);

        if (bleVersion.contains(" 2.")) {
            return RileyLinkFirmwareVersion.Version_2_0;
        }

        return RileyLinkFirmwareVersion.UnknownVersion;
    }


    private byte[] writeToDataRaw(byte[] bytes, int responseTimeout_ms) {
        SystemClock.sleep(100);
        // FIXME drain read queue?
        byte[] junkInBuffer = reader.poll(0);

        while (junkInBuffer != null) {
            aapsLogger.warn(LTag.PUMPBTCOMM, ThreadUtil.sig() + "writeToData: draining read queue, found this: "
                    + ByteUtil.shortHexString(junkInBuffer));
            junkInBuffer = reader.poll(0);
        }

        // prepend length, and send it.
        byte[] prepended = ByteUtil.concat(new byte[]{(byte) (bytes.length)}, bytes);

        aapsLogger.debug(LTag.PUMPBTCOMM, "writeToData (raw={})", ByteUtil.shortHexString(prepended));

        BLECommOperationResult writeCheck = rileyLinkBle.writeCharacteristic_blocking(radioServiceUUID, radioDataUUID,
                prepended);
        if (writeCheck.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
            aapsLogger.error(LTag.PUMPBTCOMM, "BLE Write operation failed, code=" + writeCheck.resultCode);
            return null; // will be a null (invalid) response
        }
        SystemClock.sleep(100);
        // Log.i(TAG,ThreadUtil.sig()+String.format(" writeToData:(timeout %d) %s",(responseTimeout_ms),ByteUtil.shortHexString(prepended)));
        byte[] rawResponse = reader.poll(responseTimeout_ms);
        return rawResponse;

    }


    // The caller has to know how long the RFSpy will be busy with what was sent to it.
    private RFSpyResponse writeToData(RileyLinkCommand command, int responseTimeout_ms) {

        byte[] bytes = command.getRaw();
        byte[] rawResponse = writeToDataRaw(bytes, responseTimeout_ms);

        RFSpyResponse resp = new RFSpyResponse(command, rawResponse);
        if (rawResponse == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: No response from RileyLink");
            notConnectedCount++;
        } else {
            if (resp.wasInterrupted()) {
                aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: RileyLink was interrupted");
            } else if (resp.wasTimeout()) {
                aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: RileyLink reports timeout");
                notConnectedCount++;
            } else if (resp.isOK()) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "writeToData: RileyLink reports OK");
                resetNotConnectedCount();
            } else {
                if (resp.looksLikeRadioPacket()) {
                    // RadioResponse radioResp = resp.getRadioResponse();
                    // byte[] responsePayload = radioResp.getPayload();
                    aapsLogger.debug(LTag.PUMPBTCOMM, "writeToData: received radio response. Will decode at upper level");
                    resetNotConnectedCount();
                }
                // Log.i(TAG, "writeToData: raw response is " + ByteUtil.shortHexString(rawResponse));
            }
        }
        return resp;
    }


    private void resetNotConnectedCount() {
        this.notConnectedCount = 0;
    }


    private byte[] getByteArray(byte... input) {
        return input;
    }


    private byte[] getCommandArray(RileyLinkCommandType command, byte[] body) {
        int bodyLength = body == null ? 0 : body.length;

        byte[] output = new byte[bodyLength + 1];

        output[0] = command.code;

        if (body != null) {
            for (int i = 0; i < body.length; i++) {
                output[i + 1] = body[i];
            }
        }

        return output;
    }


    public RFSpyResponse transmitThenReceive(RadioPacket pkt, byte sendChannel, byte repeatCount, byte delay_ms,
                                             byte listenChannel, int timeout_ms, byte retryCount) {
        return transmitThenReceive(pkt, sendChannel, repeatCount, delay_ms, listenChannel, timeout_ms, retryCount, null);
    }


    public RFSpyResponse transmitThenReceive(RadioPacket pkt, int timeout_ms) {
        return transmitThenReceive(pkt, (byte) 0, (byte) 0, (byte) 0, (byte) 0, timeout_ms, (byte) 0);
    }


    public RFSpyResponse transmitThenReceive(RadioPacket pkt, byte sendChannel, byte repeatCount, byte delay_ms,
                                             byte listenChannel, int timeout_ms, byte retryCount, Integer extendPreamble_ms) {

        int sendDelay = repeatCount * delay_ms;
        int receiveDelay = timeout_ms * (retryCount + 1);

        SendAndListen command = new SendAndListen(injector, sendChannel, repeatCount, delay_ms, listenChannel, timeout_ms,
                retryCount, extendPreamble_ms, pkt);

        return writeToData(command, sendDelay + receiveDelay + EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
    }


    private RFSpyResponse updateRegister(CC111XRegister reg, int val) {
        RFSpyResponse resp = writeToData(new UpdateRegister(reg, (byte) val), EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
        return resp;
    }


    public void setBaseFrequency(double freqMHz) {
        int value = (int) (freqMHz * 1000000 / ((double) (RILEYLINK_FREQ_XTAL) / Math.pow(2.0, 16.0)));
        updateRegister(CC111XRegister.freq0, (byte) (value & 0xff));
        updateRegister(CC111XRegister.freq1, (byte) ((value >> 8) & 0xff));
        updateRegister(CC111XRegister.freq2, (byte) ((value >> 16) & 0xff));
        aapsLogger.info(LTag.PUMPBTCOMM, "Set frequency to {} MHz", freqMHz);

        this.currentFrequencyMHz = freqMHz;

        configureRadioForRegion(rileyLinkServiceData.rileyLinkTargetFrequency);
    }


    private void configureRadioForRegion(RileyLinkTargetFrequency frequency) {

        // we update registers only on first run, or if region changed
        aapsLogger.error(LTag.PUMPBTCOMM, "RileyLinkTargetFrequency: " + frequency);

        switch (frequency) {
            case Medtronic_WorldWide: {
                // updateRegister(CC111X_MDMCFG4, (byte) 0x59);
                setRXFilterMode(RXFilterMode.Wide);
                // updateRegister(CC111X_MDMCFG3, (byte) 0x66);
                // updateRegister(CC111X_MDMCFG2, (byte) 0x33);
                updateRegister(CC111XRegister.mdmcfg1, 0x62);
                updateRegister(CC111XRegister.mdmcfg0, 0x1A);
                updateRegister(CC111XRegister.deviatn, 0x13);
                setMedtronicEncoding();
            }
            break;

            case Medtronic_US: {
                // updateRegister(CC111X_MDMCFG4, (byte) 0x99);
                setRXFilterMode(RXFilterMode.Narrow);
                // updateRegister(CC111X_MDMCFG3, (byte) 0x66);
                // updateRegister(CC111X_MDMCFG2, (byte) 0x33);
                updateRegister(CC111XRegister.mdmcfg1, 0x61);
                updateRegister(CC111XRegister.mdmcfg0, 0x7E);
                updateRegister(CC111XRegister.deviatn, 0x15);
                setMedtronicEncoding();
            }
            break;

            case Omnipod: {
                RFSpyResponse r = null;
                // RL initialization for Omnipod is a copy/paste from OmniKit implementation.
                // Last commit from original repository: 5c3beb4144
                // so if something is terribly wrong, please check git diff PodCommsSession.swift since that commit
                r = updateRegister(CC111XRegister.pktctrl1, 0x20);
                r = updateRegister(CC111XRegister.agcctrl0, 0x00);
                r = updateRegister(CC111XRegister.fsctrl1, 0x06);
                r = updateRegister(CC111XRegister.mdmcfg4, 0xCA);
                r = updateRegister(CC111XRegister.mdmcfg3, 0xBC);
                r = updateRegister(CC111XRegister.mdmcfg2, 0x06);
                r = updateRegister(CC111XRegister.mdmcfg1, 0x70);
                r = updateRegister(CC111XRegister.mdmcfg0, 0x11);
                r = updateRegister(CC111XRegister.deviatn, 0x44);
                r = updateRegister(CC111XRegister.mcsm0, 0x18);
                r = updateRegister(CC111XRegister.foccfg, 0x17);
                r = updateRegister(CC111XRegister.fscal3, 0xE9);
                r = updateRegister(CC111XRegister.fscal2, 0x2A);
                r = updateRegister(CC111XRegister.fscal1, 0x00);
                r = updateRegister(CC111XRegister.fscal0, 0x1F);

                r = updateRegister(CC111XRegister.test1, 0x31);
                r = updateRegister(CC111XRegister.test0, 0x09);
                r = updateRegister(CC111XRegister.paTable0, 0x84);
                r = updateRegister(CC111XRegister.sync1, 0xA5);
                r = updateRegister(CC111XRegister.sync0, 0x5A);

                r = setRileyLinkEncoding(RileyLinkEncodingType.Manchester);
                r = setPreamble(0x6665);

            }
            break;
            default:
                aapsLogger.warn(LTag.PUMPBTCOMM, "No region configuration for RfSpy and {}", frequency.name());
                break;

        }
    }


    private void setMedtronicEncoding() {
        RileyLinkEncodingType encoding = RileyLinkEncodingType.FourByteSixByteLocal;

        if (RileyLinkFirmwareVersion.isSameVersion(rileyLinkServiceData.firmwareVersion, RileyLinkFirmwareVersion.Version2AndHigher)) {
            if (sp.getString(MedtronicConst.Prefs.Encoding, "None").equals(resourceHelper.gs(R.string.key_medtronic_pump_encoding_4b6b_rileylink))) {
                encoding = RileyLinkEncodingType.FourByteSixByteRileyLink;
            }
        }

        setRileyLinkEncoding(encoding);

        aapsLogger.debug(LTag.PUMPBTCOMM, "Set Encoding for Medtronic: " + encoding.name());
    }


    private RFSpyResponse setPreamble(int preamble) {
        RFSpyResponse resp = null;
        try {
            resp = writeToData(new SetPreamble(injector, preamble), EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
        } catch (Exception e) {
            aapsLogger.error("Failed to set preamble", e);
        }
        return resp;
    }


    public RFSpyResponse setRileyLinkEncoding(RileyLinkEncodingType encoding) {
        RFSpyResponse resp = writeToData(new SetHardwareEncoding(encoding), EXPECTED_MAX_BLUETOOTH_LATENCY_MS);

        if (resp.isOK()) {
            reader.setRileyLinkEncodingType(encoding);
            rileyLinkUtil.setEncoding(encoding);
        }

        return resp;
    }


    private void setRXFilterMode(RXFilterMode mode) {

        byte drate_e = (byte) 0x9; // exponent of symbol rate (16kbps)
        byte chanbw = mode.value;

        updateRegister(CC111XRegister.mdmcfg4, (byte) (chanbw | drate_e));
    }

    /**
     * Reset RileyLink Configuration (set all updateRegisters)
     */
    public void resetRileyLinkConfiguration() {
        if (this.currentFrequencyMHz != null)
            this.setBaseFrequency(this.currentFrequencyMHz);
    }


    public void stopReader() {
        reader.stop();
    }
}

package app.aaps.pump.common.hw.rileylink.ble;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.resources.ResourceHelper;
import app.aaps.core.interfaces.rx.bus.RxBus;
import app.aaps.core.interfaces.rx.events.EventRefreshOverview;
import app.aaps.core.interfaces.sharedPreferences.SP;
import app.aaps.core.utils.StringUtil;
import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.core.utils.pump.ThreadUtil;
import app.aaps.pump.common.hw.rileylink.R;
import app.aaps.pump.common.hw.rileylink.RileyLinkConst;
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil;
import app.aaps.pump.common.hw.rileylink.ble.command.RileyLinkCommand;
import app.aaps.pump.common.hw.rileylink.ble.command.SendAndListen;
import app.aaps.pump.common.hw.rileylink.ble.command.SetHardwareEncoding;
import app.aaps.pump.common.hw.rileylink.ble.command.SetPreamble;
import app.aaps.pump.common.hw.rileylink.ble.command.UpdateRegister;
import app.aaps.pump.common.hw.rileylink.ble.data.GattAttributes;
import app.aaps.pump.common.hw.rileylink.ble.data.RFSpyResponse;
import app.aaps.pump.common.hw.rileylink.ble.data.RadioPacket;
import app.aaps.pump.common.hw.rileylink.ble.defs.CC111XRegister;
import app.aaps.pump.common.hw.rileylink.ble.defs.RXFilterMode;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency;
import app.aaps.pump.common.hw.rileylink.ble.operations.BLECommOperationResult;
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData;
import dagger.android.HasAndroidInjector;

/**
 * Created by geoff on 5/26/16.
 */
@Singleton
public class RFSpy {
    private static final long DEFAULT_BATTERY_CHECK_INTERVAL_MILLIS = 30 * 60 * 1_000L; // 30 minutes;
    private static final long LOW_BATTERY_BATTERY_CHECK_INTERVAL_MILLIS = 10 * 60 * 1_000L; // 10 minutes;
    private static final int LOW_BATTERY_PERCENTAGE_THRESHOLD = 20;
    private static final long RILEYLINK_FREQ_XTAL = 24000000;
    private static final int EXPECTED_MAX_BLUETOOTH_LATENCY_MS = 7500; // 1500
    private final HasAndroidInjector injector;
    private final RileyLinkBLE rileyLinkBle;
    private final UUID radioServiceUUID = UUID.fromString(GattAttributes.SERVICE_RADIO);
    private final UUID radioDataUUID = UUID.fromString(GattAttributes.CHARA_RADIO_DATA);
    private final UUID radioVersionUUID = UUID.fromString(GattAttributes.CHARA_RADIO_VERSION);
    private final UUID batteryServiceUUID = UUID.fromString(GattAttributes.SERVICE_BATTERY);
    private final UUID batteryLevelUUID = UUID.fromString(GattAttributes.CHARA_BATTERY_LEVEL);
    public int notConnectedCount = 0;
    @Inject AAPSLogger aapsLogger;
    @Inject ResourceHelper rh;
    @Inject SP sp;
    @Inject RileyLinkServiceData rileyLinkServiceData;
    @Inject RileyLinkUtil rileyLinkUtil;
    @Inject RxBus rxBus;
    private RFSpyReader reader;
    private String bleVersion; // We don't use it so no need of sofisticated logic
    private Double currentFrequencyMHz;
    private long nextBatteryCheck = 0;

    @Inject
    public RFSpy(HasAndroidInjector injector, RileyLinkBLE rileyLinkBle) {
        this.injector = injector;
        this.rileyLinkBle = rileyLinkBle;
    }

    static RileyLinkFirmwareVersion getFirmwareVersion(AAPSLogger aapsLogger, @NonNull String bleVersion, String cc1110Version) {
        if (cc1110Version != null) {
            RileyLinkFirmwareVersion version = RileyLinkFirmwareVersion.getByVersionString(cc1110Version);
            aapsLogger.debug(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Firmware Version string: %s, resolved to %s.", cc1110Version, version));

            if (version != RileyLinkFirmwareVersion.UnknownVersion) {
                return version;
            }
        }

        aapsLogger.error(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Firmware Version can't be determined. Checking with BLE Version [%s].", bleVersion));

        if (bleVersion.contains(" 2.")) {
            return RileyLinkFirmwareVersion.Version_2_0;
        }

        return RileyLinkFirmwareVersion.UnknownVersion;
    }

    @Inject
    public void onInit() {
        //aapsLogger.debug("RileyLinkServiceData:" + rileyLinkServiceData);
        reader = new RFSpyReader(aapsLogger, rileyLinkBle);
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
        String cc1110Version = getCC1110Version();
        rileyLinkServiceData.versionCC110 = cc1110Version;
        rileyLinkServiceData.firmwareVersion = getFirmwareVersion(aapsLogger, bleVersion, cc1110Version);

        aapsLogger.debug(LTag.PUMPBTCOMM,
                String.format("RileyLink - BLE Version: %s, CC1110 Version: %s, Firmware Version: %s",
                        bleVersion, cc1110Version, rileyLinkServiceData.firmwareVersion));
    }

    // Call this from the "response count" notification handler.
    private void newDataIsAvailable() {
        // pass the message to the reader (which should be internal to RFSpy)
        reader.newDataIsAvailable();
    }

    @Nullable public Integer retrieveBatteryLevel() {
        BLECommOperationResult result = rileyLinkBle.readCharacteristicBlocking(batteryServiceUUID, batteryLevelUUID);
        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
            if (ArrayUtils.isNotEmpty(result.value)) {
                int value = result.value[0];
                aapsLogger.debug(LTag.PUMPBTCOMM, "getBatteryLevel response received: " + value);
                return value;
            } else {
                aapsLogger.error(LTag.PUMPBTCOMM, "getBatteryLevel received an empty result. Value: " + result.value);
            }
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "getBatteryLevel failed with code: " + result.resultCode);
        }
        return null;
    }

    // This gets the version from the BLE113, not from the CC1110.
    // I.e., this gets the version from the BLE interface, not from the radio.
    public String getVersion() {
        BLECommOperationResult result = rileyLinkBle.readCharacteristicBlocking(radioServiceUUID, radioVersionUUID);
        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
            String version = StringUtil.INSTANCE.fromBytes(result.value);
            aapsLogger.debug(LTag.PUMPBTCOMM, "BLE Version: " + version);
            return version;
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "getVersion failed with code: " + result.resultCode);
            return "(null)";
        }
    }

    private String getCC1110Version() {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Firmware Version. Get Version - Start");

        for (int i = 0; i < 5; i++) {
            // We have to call raw version of communication to get firmware version
            // So that we can adjust other commands accordingly afterwords

            byte[] getVersionRaw = getByteArray(RileyLinkCommandType.GetVersion.code);
            byte[] response = writeToDataRaw(getVersionRaw, 5000);

            aapsLogger.debug(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Firmware Version. GetVersion [response=%s]", ByteUtil.INSTANCE.shortHexString(response)));

            if (response != null) { // && response[0] == (byte) 0xDD) {

                String versionString = StringUtil.INSTANCE.fromBytes(response);
                if (versionString.length() > 3) {
                    if (versionString.indexOf('s') >= 0) {
                        versionString = versionString.substring(versionString.indexOf('s'));
                    }
                    return versionString;
                }
                SystemClock.sleep(1000);
            }
        }

        return null;
    }

    private byte[] writeToDataRaw(@NonNull byte[] bytes, int responseTimeout_ms) {
        SystemClock.sleep(100);
        // FIXME drain read queue?
        byte[] junkInBuffer = reader.poll(0);

        while (junkInBuffer != null) {
            aapsLogger.warn(LTag.PUMPBTCOMM, ThreadUtil.sig() + "writeToData: draining read queue, found this: "
                    + ByteUtil.INSTANCE.shortHexString(junkInBuffer));
            junkInBuffer = reader.poll(0);
        }

        // prepend length, and send it.
        byte[] prepended = ByteUtil.INSTANCE.concat(new byte[]{(byte) (bytes.length)}, bytes);

        aapsLogger.debug(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "writeToData (raw=%s)", ByteUtil.INSTANCE.shortHexString(prepended)));

        BLECommOperationResult writeCheck = rileyLinkBle.writeCharacteristicBlocking(radioServiceUUID, radioDataUUID,
                prepended);
        if (writeCheck.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
            aapsLogger.error(LTag.PUMPBTCOMM, "BLE Write operation failed, code=" + writeCheck.resultCode);
            return null; // will be a null (invalid) response
        }

        SystemClock.sleep(100);

        return reader.poll(responseTimeout_ms);
    }

    // The caller has to know how long the RFSpy will be busy with what was sent to it.
    private RFSpyResponse writeToData(RileyLinkCommand command, int responseTimeout_ms) {

        byte[] bytes = command.getRaw();
        byte[] rawResponse = writeToDataRaw(bytes, responseTimeout_ms);

        RFSpyResponse resp = new RFSpyResponse(command, rawResponse);
        if (rawResponse == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: No response from RileyLink");
            notConnectedCount++;
        } else if (resp.wasInterrupted()) {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: RileyLink was interrupted");
        } else if (resp.wasTimeout()) {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: RileyLink reports timeout");
            notConnectedCount++;
        } else if (resp.isOK()) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "writeToData: RileyLink reports OK");
            resetNotConnectedCount();
        } else {
            if (resp.looksLikeRadioPacket()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "writeToData: received radio response. Will decode at upper level");
                resetNotConnectedCount();
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

    public RFSpyResponse transmitThenReceive(RadioPacket pkt, byte sendChannel, byte repeatCount, byte delay_ms,
                                             byte listenChannel, int timeout_ms, byte retryCount) {
        return transmitThenReceive(pkt, sendChannel, repeatCount, delay_ms, listenChannel, timeout_ms, retryCount, null);
    }

    public RFSpyResponse transmitThenReceive(RadioPacket pkt, byte sendChannel, byte repeatCount, byte delay_ms,
                                             byte listenChannel, int timeout_ms, byte retryCount, Integer extendPreamble_ms) {

        int sendDelay = repeatCount * delay_ms;
        int receiveDelay = timeout_ms * (retryCount + 1);

        SendAndListen command = new SendAndListen(injector, sendChannel, repeatCount, delay_ms, listenChannel, timeout_ms,
                retryCount, extendPreamble_ms, pkt);

        RFSpyResponse rfSpyResponse = writeToData(command, sendDelay + receiveDelay + EXPECTED_MAX_BLUETOOTH_LATENCY_MS);

        if (System.currentTimeMillis() >= nextBatteryCheck) {
            updateBatteryLevel();
        }

        return rfSpyResponse;
    }

    private void updateBatteryLevel() {
        rileyLinkServiceData.batteryLevel = retrieveBatteryLevel();
        nextBatteryCheck = System.currentTimeMillis() +
                (Optional.ofNullable(rileyLinkServiceData.batteryLevel).orElse(0) <= LOW_BATTERY_PERCENTAGE_THRESHOLD ? LOW_BATTERY_BATTERY_CHECK_INTERVAL_MILLIS : DEFAULT_BATTERY_CHECK_INTERVAL_MILLIS);

        // The Omnipod plugin reports the RL battery as the pump battery (as the Omnipod battery level is unknown)
        // So update overview when the battery level has been updated
        rxBus.send(new EventRefreshOverview("RL battery level updated", false));
    }

    @NonNull private RFSpyResponse updateRegister(CC111XRegister reg, int val) {
        return writeToData(new UpdateRegister(reg, (byte) val), EXPECTED_MAX_BLUETOOTH_LATENCY_MS);
    }

    public void setBaseFrequency(double freqMHz) {
        int value = (int) (freqMHz * 1000000 / ((double) (RILEYLINK_FREQ_XTAL) / Math.pow(2.0, 16.0)));
        updateRegister(CC111XRegister.freq0, (byte) (value & 0xff));
        updateRegister(CC111XRegister.freq1, (byte) ((value >> 8) & 0xff));
        updateRegister(CC111XRegister.freq2, (byte) ((value >> 16) & 0xff));
        aapsLogger.info(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Set frequency to %.3f MHz", freqMHz));

        this.currentFrequencyMHz = freqMHz;

        configureRadioForRegion(rileyLinkServiceData.rileyLinkTargetFrequency);
    }

    private void configureRadioForRegion(RileyLinkTargetFrequency frequency) {
        // we update registers only on first run, or if region changed
        aapsLogger.error(LTag.PUMPBTCOMM, "RileyLinkTargetFrequency: " + frequency);

        switch (frequency) {
            case MedtronicWorldWide:
                setRXFilterMode(RXFilterMode.Wide);
                updateRegister(CC111XRegister.mdmcfg1, 0x62);
                updateRegister(CC111XRegister.mdmcfg0, 0x1A);
                updateRegister(CC111XRegister.deviatn, 0x13);
                setMedtronicEncoding();
                break;

            case MedtronicUS:
                setRXFilterMode(RXFilterMode.Narrow);
                updateRegister(CC111XRegister.mdmcfg1, 0x61);
                updateRegister(CC111XRegister.mdmcfg0, 0x7E);
                updateRegister(CC111XRegister.deviatn, 0x15);
                setMedtronicEncoding();
                break;

            case Omnipod:
                // RL initialization for Omnipod is a copy/paste from OmniKit implementation.
                // Last commit from original repository: 5c3beb4144
                // so if something is terribly wrong, please check git diff PodCommsSession.swift since that commit
                updateRegister(CC111XRegister.pktctrl1, 0x20);
                updateRegister(CC111XRegister.agcctrl0, 0x00);
                updateRegister(CC111XRegister.fsctrl1, 0x06);
                updateRegister(CC111XRegister.mdmcfg4, 0xCA);
                updateRegister(CC111XRegister.mdmcfg3, 0xBC);
                updateRegister(CC111XRegister.mdmcfg2, 0x06);
                updateRegister(CC111XRegister.mdmcfg1, 0x70);
                updateRegister(CC111XRegister.mdmcfg0, 0x11);
                updateRegister(CC111XRegister.deviatn, 0x44);
                updateRegister(CC111XRegister.mcsm0, 0x18);
                updateRegister(CC111XRegister.foccfg, 0x17);
                updateRegister(CC111XRegister.fscal3, 0xE9);
                updateRegister(CC111XRegister.fscal2, 0x2A);
                updateRegister(CC111XRegister.fscal1, 0x00);
                updateRegister(CC111XRegister.fscal0, 0x1F);

                updateRegister(CC111XRegister.test1, 0x31);
                updateRegister(CC111XRegister.test0, 0x09);
                updateRegister(CC111XRegister.paTable0, 0x84);
                updateRegister(CC111XRegister.sync1, 0xA5);
                updateRegister(CC111XRegister.sync0, 0x5A);

                setRileyLinkEncoding(RileyLinkEncodingType.Manchester);
                setPreamble(0x6665);
                break;
            default:
                aapsLogger.warn(LTag.PUMPBTCOMM, "No region configuration for RfSpy and " + frequency.name());
                break;

        }
    }

    private void setMedtronicEncoding() {
        RileyLinkEncodingType encoding = RileyLinkEncodingType.FourByteSixByteLocal;

        if (rileyLinkServiceData.firmwareVersion != null &&
                rileyLinkServiceData.firmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher)) {
            if (sp.getString(RileyLinkConst.Prefs.Encoding, "None")
                    .equals(rh.gs(R.string.key_medtronic_pump_encoding_4b6b_rileylink))) {
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
        byte chanbw = mode.getValue();

        updateRegister(CC111XRegister.mdmcfg4, (byte) (chanbw | drate_e));
    }

    /**
     * Reset RileyLink Configuration (set all updateRegisters)
     */
    public void resetRileyLinkConfiguration() {
        if (this.currentFrequencyMHz != null)
            this.setBaseFrequency(this.currentFrequencyMHz);
    }
}

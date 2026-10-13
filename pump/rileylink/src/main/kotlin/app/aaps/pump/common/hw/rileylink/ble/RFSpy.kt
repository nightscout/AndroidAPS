package app.aaps.pump.common.hw.rileylink.ble

import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.StringUtil.fromBytes
import app.aaps.core.utils.pump.ByteUtil.concat
import app.aaps.core.utils.pump.ByteUtil.shortHexString
import app.aaps.core.utils.pump.ThreadUtil.sig
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.command.RileyLinkCommand
import app.aaps.pump.common.hw.rileylink.ble.command.SendAndListen
import app.aaps.pump.common.hw.rileylink.ble.command.SetHardwareEncoding
import app.aaps.pump.common.hw.rileylink.ble.command.SetPreamble
import app.aaps.pump.common.hw.rileylink.ble.command.UpdateRegister
import app.aaps.pump.common.hw.rileylink.ble.data.GattAttributes
import app.aaps.pump.common.hw.rileylink.ble.data.RFSpyResponse
import app.aaps.pump.common.hw.rileylink.ble.data.RadioPacket
import app.aaps.pump.common.hw.rileylink.ble.defs.CC111XRegister
import app.aaps.pump.common.hw.rileylink.ble.defs.RXFilterMode
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersionBase
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency
import app.aaps.pump.common.hw.rileylink.ble.operations.BLECommOperationResult
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringPreferenceKey
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import org.apache.commons.lang3.ArrayUtils
import java.util.Locale
import java.util.Optional
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Created by geoff on 5/26/16.
 */
@Singleton
class RFSpy @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val rileyLinkBle: RileyLinkBLE,
    private val rileyLinkServiceData: RileyLinkServiceData,
    private val rileyLinkUtil: RileyLinkUtil,
    private val rfSpyResponseProvider: Provider<RFSpyResponse>
) {

    private val radioServiceUUID: UUID = UUID.fromString(GattAttributes.SERVICE_RADIO)
    private val radioDataUUID: UUID = UUID.fromString(GattAttributes.CHARA_RADIO_DATA)
    private val radioVersionUUID: UUID = UUID.fromString(GattAttributes.CHARA_RADIO_VERSION)
    private val batteryServiceUUID: UUID = UUID.fromString(GattAttributes.SERVICE_BATTERY)
    private val batteryLevelUUID: UUID = UUID.fromString(GattAttributes.CHARA_BATTERY_LEVEL)
    var notConnectedCount: Int = 0

    private var reader: RFSpyReader = RFSpyReader(aapsLogger, rileyLinkBle)
    private var bleVersion: String? = null // We don't use it so no need of sophisticated logic
    private var currentFrequencyMHz: Double? = null
    private var nextBatteryCheck: Long = 0

    fun getBLEVersionCached(): String = bleVersion ?: "UNKNOWN"

    // Call this after the RL services are discovered.
    // Starts an async task to read when data is available
    fun startReader() {
        rileyLinkBle.registerRadioResponseCountNotification { this.newDataIsAvailable() }
        reader.start()
    }

    // Here should go generic RL initialisation + protocol adjustments depending on
    // firmware version
    fun initializeRileyLink() {
        bleVersion = getVersion()
        val cc1110Version = getCC1110Version()
        rileyLinkServiceData.versionCC110 = cc1110Version
        rileyLinkServiceData.firmwareVersion = getFirmwareVersion(aapsLogger, getBLEVersionCached(), cc1110Version)

        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            String.format(
                "RileyLink - BLE Version: %s, CC1110 Version: %s, Firmware Version: %s",
                bleVersion, cc1110Version, rileyLinkServiceData.firmwareVersion
            )
        )
    }

    // Call this from the "response count" notification handler.
    private fun newDataIsAvailable() {
        // pass the message to the reader (which should be internal to RFSpy)
        reader.newDataIsAvailable()
    }

    fun retrieveBatteryLevel(): Int? {
        val result = rileyLinkBle.readCharacteristicBlocking(batteryServiceUUID, batteryLevelUUID)
        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
            result.value?.let {
                if (ArrayUtils.isNotEmpty(it)) {
                    val value = it[0].toInt()
                    aapsLogger.debug(LTag.PUMPBTCOMM, "getBatteryLevel response received: $value")
                    return value
                } else {
                    aapsLogger.error(LTag.PUMPBTCOMM, "getBatteryLevel received an empty result. Value: $it")
                }
            }
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "getBatteryLevel failed with code: " + result.resultCode)
        }
        return null
    }

    // This gets the version from the BLE113, not from the CC1110.
    // I.e., this gets the version from the BLE interface, not from the radio.
    fun getVersion(): String {
        val result = rileyLinkBle.readCharacteristicBlocking(radioServiceUUID, radioVersionUUID)
        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
            val version = fromBytes(result.value)
            aapsLogger.debug(LTag.PUMPBTCOMM, "BLE Version: $version")
            return version
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "getVersion failed with code: " + result.resultCode)
            return "(null)"
        }
    }

    private fun getCC1110Version(): String? {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Firmware Version. Get Version - Start")

        (0..4).forEach { i ->
            // We have to call raw version of communication to get firmware version
            // So that we can adjust other commands accordingly afterwords

            val getVersionRaw = getByteArray(RileyLinkCommandType.GetVersion.code)
            val response = writeToDataRaw(getVersionRaw, 5000)

            aapsLogger.debug(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Firmware Version. GetVersion [response=%s]", shortHexString(response)))

            if (response != null) { // && response[0] == (byte) 0xDD) {

                var versionString = fromBytes(response)
                if (versionString.length > 3) {
                    if (versionString.indexOf('s') >= 0) {
                        versionString = versionString.substring(versionString.indexOf('s'))
                    }
                    return versionString
                }
                SystemClock.sleep(1000)
            }
        }

        return null
    }

    private fun writeToDataRaw(bytes: ByteArray, responseTimeoutMs: Int): ByteArray? {
        SystemClock.sleep(1)
        // FIXME drain read queue?
        var junkInBuffer = reader.poll(0)

        while (junkInBuffer != null) {
            aapsLogger.warn(
                LTag.PUMPBTCOMM, (sig() + "writeToData: draining read queue, found this: "
                    + shortHexString(junkInBuffer))
            )
            junkInBuffer = reader.poll(0)
        }

        // prepend length, and send it.
        val prepended = concat(byteArrayOf((bytes.size).toByte()), bytes)

        aapsLogger.debug(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "writeToData (raw=%s)", shortHexString(prepended)))

        val writeCheck = rileyLinkBle.writeCharacteristicBlocking(
            radioServiceUUID, radioDataUUID,
            prepended
        )
        if (writeCheck.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
            aapsLogger.error(LTag.PUMPBTCOMM, "BLE Write operation failed, code=" + writeCheck.resultCode)
            return null // will be a null (invalid) response
        }

        return reader.poll(responseTimeoutMs)
    }

    // The caller has to know how long the RFSpy will be busy with what was sent to it.
    private fun writeToData(command: RileyLinkCommand, responseTimeoutMs: Int): RFSpyResponse? {
        val bytes = command.getRaw()
        val rawResponse = writeToDataRaw(bytes, responseTimeoutMs)

        if (rawResponse == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: No response from RileyLink")
            notConnectedCount++
            return null
        }
        val resp = rfSpyResponseProvider.get().with(command, rawResponse)
        if (resp.wasInterrupted()) {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: RileyLink was interrupted")
        } else if (resp.wasTimeout()) {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeToData: RileyLink reports timeout")
            notConnectedCount++
        } else if (resp.isOK()) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "writeToData: RileyLink reports OK")
            resetNotConnectedCount()
        } else {
            if (resp.looksLikeRadioPacket()) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "writeToData: received radio response. Will decode at upper level")
                resetNotConnectedCount()
            }
        }
        return resp
    }

    private fun resetNotConnectedCount() {
        this.notConnectedCount = 0
    }

    private fun getByteArray(vararg input: Byte): ByteArray {
        return input
    }

    @JvmOverloads fun transmitThenReceive(
        pkt: RadioPacket, sendChannel: Byte, repeatCount: Byte, delayMs: Byte,
        listenChannel: Byte, timeoutMs: Int, retryCount: Byte, extendPreambleMs: Int = 0
    ): RFSpyResponse? {
        val sendDelay = repeatCount * delayMs
        val receiveDelay = timeoutMs * (retryCount + 1)

        val command = SendAndListen(
            rileyLinkServiceData, sendChannel, repeatCount, delayMs.toInt(), listenChannel, timeoutMs,
            retryCount, extendPreambleMs, pkt
        )

        val rfSpyResponse = writeToData(command, sendDelay + receiveDelay + EXPECTED_MAX_BLUETOOTH_LATENCY_MS)

        if (System.currentTimeMillis() >= nextBatteryCheck) {
            updateBatteryLevel()
        }

        return rfSpyResponse
    }

    private fun updateBatteryLevel() {
        rileyLinkServiceData.batteryLevel = retrieveBatteryLevel()
        nextBatteryCheck = System.currentTimeMillis() +
            (if (Optional.ofNullable<Int>(rileyLinkServiceData.batteryLevel).orElse(0) <= LOW_BATTERY_PERCENTAGE_THRESHOLD) LOW_BATTERY_BATTERY_CHECK_INTERVAL_MILLIS else DEFAULT_BATTERY_CHECK_INTERVAL_MILLIS)

        // The Omnipod plugin reports the RL battery as the pump battery (as the Omnipod battery level is unknown)
        // So update overview when the battery level has been updated
        rxBus.send(EventRefreshOverview("RL battery level updated", false))
    }

    private fun updateRegister(reg: CC111XRegister, `val`: Int): RFSpyResponse? {
        return writeToData(UpdateRegister(reg, `val`.toByte()), EXPECTED_MAX_BLUETOOTH_LATENCY_MS)
    }

    fun setBaseFrequency(freqMHz: Double) {
        val value = (freqMHz * 1000000 / ((RILEYLINK_FREQ_XTAL).toDouble() / 2.0.pow(16.0))).toInt()
        updateRegister(CC111XRegister.freq0, (value and 0xff).toByte().toInt())
        updateRegister(CC111XRegister.freq1, ((value shr 8) and 0xff).toByte().toInt())
        updateRegister(CC111XRegister.freq2, ((value shr 16) and 0xff).toByte().toInt())
        aapsLogger.info(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Set frequency to %.3f MHz", freqMHz))

        this.currentFrequencyMHz = freqMHz

        configureRadioForRegion(rileyLinkServiceData.rileyLinkTargetFrequency)
    }

    private fun configureRadioForRegion(frequency: RileyLinkTargetFrequency) {
        // we update registers only on first run, or if region changed
        aapsLogger.error(LTag.PUMPBTCOMM, "RileyLinkTargetFrequency: $frequency")

        when (frequency) {
            RileyLinkTargetFrequency.MedtronicWorldWide -> {
                setRXFilterMode(RXFilterMode.Wide)
                updateRegister(CC111XRegister.mdmcfg1, 0x62)
                updateRegister(CC111XRegister.mdmcfg0, 0x1A)
                updateRegister(CC111XRegister.deviatn, 0x13)
                setMedtronicEncoding()
            }

            RileyLinkTargetFrequency.MedtronicUS        -> {
                setRXFilterMode(RXFilterMode.Narrow)
                updateRegister(CC111XRegister.mdmcfg1, 0x61)
                updateRegister(CC111XRegister.mdmcfg0, 0x7E)
                updateRegister(CC111XRegister.deviatn, 0x15)
                setMedtronicEncoding()
            }

            RileyLinkTargetFrequency.Omnipod            -> {
                // RL initialization for Omnipod is a copy/paste from OmniKit implementation.
                // Last commit from original repository: 5c3beb4144
                // so if something is terribly wrong, please check git diff PodCommsSession.swift since that commit
                updateRegister(CC111XRegister.pktctrl1, 0x20)
                updateRegister(CC111XRegister.agcctrl0, 0x00)
                updateRegister(CC111XRegister.fsctrl1, 0x06)
                updateRegister(CC111XRegister.mdmcfg4, 0xCA)
                updateRegister(CC111XRegister.mdmcfg3, 0xBC)
                updateRegister(CC111XRegister.mdmcfg2, 0x06)
                updateRegister(CC111XRegister.mdmcfg1, 0x70)
                updateRegister(CC111XRegister.mdmcfg0, 0x11)
                updateRegister(CC111XRegister.deviatn, 0x44)
                updateRegister(CC111XRegister.mcsm0, 0x18)
                updateRegister(CC111XRegister.foccfg, 0x17)
                updateRegister(CC111XRegister.fscal3, 0xE9)
                updateRegister(CC111XRegister.fscal2, 0x2A)
                updateRegister(CC111XRegister.fscal1, 0x00)
                updateRegister(CC111XRegister.fscal0, 0x1F)

                updateRegister(CC111XRegister.test1, 0x31)
                updateRegister(CC111XRegister.test0, 0x09)
                updateRegister(CC111XRegister.paTable0, 0x84)
                updateRegister(CC111XRegister.sync1, 0xA5)
                updateRegister(CC111XRegister.sync0, 0x5A)

                setRileyLinkEncoding(RileyLinkEncodingType.Manchester)
                setPreamble(0x6665)
            }

            else                                        -> aapsLogger.warn(LTag.PUMPBTCOMM, "No region configuration for RfSpy and " + frequency.name)
        }
    }

    private fun setMedtronicEncoding() {
        var encoding = RileyLinkEncodingType.FourByteSixByteLocal

        if (rileyLinkServiceData.firmwareVersion?.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher) == true
        ) {
            if (preferences.get(RileyLinkStringPreferenceKey.Encoding) == RileyLinkEncodingType.FourByteSixByteRileyLink.key)
                encoding = RileyLinkEncodingType.FourByteSixByteRileyLink
        }

        setRileyLinkEncoding(encoding)

        aapsLogger.debug(LTag.PUMPBTCOMM, "Set Encoding for Medtronic: " + encoding.name)
    }

    private fun setPreamble(@Suppress("SameParameterValue") preamble: Int): RFSpyResponse? {
        try {
            return writeToData(SetPreamble(rileyLinkServiceData, preamble), EXPECTED_MAX_BLUETOOTH_LATENCY_MS)
        } catch (e: Exception) {
            aapsLogger.error("Failed to set preamble", e)
        }
        return null
    }

    fun setRileyLinkEncoding(encoding: RileyLinkEncodingType): RFSpyResponse? {
        val resp = writeToData(SetHardwareEncoding(encoding), EXPECTED_MAX_BLUETOOTH_LATENCY_MS)

        if (resp?.isOK() == true) {
            reader.setRileyLinkEncodingType(encoding)
            rileyLinkUtil.encoding = encoding
        }

        return resp
    }

    @Suppress("SpellCheckingInspection", "LocalVariableName")
    private fun setRXFilterMode(mode: RXFilterMode) {
        val drate_e = 0x9.toByte() // exponent of symbol rate (16kbps)
        val chanbw = mode.value

        updateRegister(CC111XRegister.mdmcfg4, (chanbw.toInt() or drate_e.toInt()).toByte().toInt())
    }

    /**
     * Reset RileyLink Configuration (set all updateRegisters)
     */
    fun resetRileyLinkConfiguration() {
        currentFrequencyMHz?.let { setBaseFrequency(it) }
    }

    companion object {

        private const val DEFAULT_BATTERY_CHECK_INTERVAL_MILLIS = 30 * 60 * 1000L // 30 minutes;
        private const val LOW_BATTERY_BATTERY_CHECK_INTERVAL_MILLIS = 10 * 60 * 1000L // 10 minutes;
        private const val LOW_BATTERY_PERCENTAGE_THRESHOLD = 20
        private const val RILEYLINK_FREQ_XTAL: Long = 24000000
        private const val EXPECTED_MAX_BLUETOOTH_LATENCY_MS = 7500 // 1500
        fun getFirmwareVersion(aapsLogger: AAPSLogger, bleVersion: String, cc1110Version: String?): RileyLinkFirmwareVersionBase {
            if (cc1110Version != null) {
                val version = RileyLinkFirmwareVersion.getByVersionString(cc1110Version)
                aapsLogger.debug(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Firmware Version string: %s, resolved to %s.", cc1110Version, version))

                if (version != null && version != RileyLinkFirmwareVersionBase.UnknownVersion) {
                    return version
                }
            }

            aapsLogger.error(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Firmware Version can't be determined. Checking with BLE Version [%s].", bleVersion))

            if (bleVersion.contains(" 2.")) {
                return RileyLinkFirmwareVersionBase.Version_2_0
            }

            return RileyLinkFirmwareVersionBase.UnknownVersion
        }
    }
}

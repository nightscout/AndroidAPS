package app.aaps.pump.common.hw.rileylink

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.interfaces.utils.Round.isSame
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.pump.ByteUtil.shortHexString
import app.aaps.pump.common.hw.rileylink.ble.RFSpy
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException
import app.aaps.pump.common.hw.rileylink.ble.data.FrequencyScanResults
import app.aaps.pump.common.hw.rileylink.ble.data.FrequencyTrial
import app.aaps.pump.common.hw.rileylink.ble.data.RLMessage
import app.aaps.pump.common.hw.rileylink.ble.data.RadioPacket
import app.aaps.pump.common.hw.rileylink.ble.data.RadioResponse
import app.aaps.pump.common.hw.rileylink.ble.defs.RLMessageType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkLongKey
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import java.util.Locale
import javax.inject.Provider

/**
 * This is abstract class for RileyLink Communication, this one needs to be extended by specific "Pump" class.
 *
 *
 * Created by andy on 5/10/18.
 */
abstract class RileyLinkCommunicationManager<T : RLMessage>(
    val aapsLogger: AAPSLogger,
    val preferences: Preferences,
    val rileyLinkServiceData: RileyLinkServiceData,
    val serviceTaskExecutor: ServiceTaskExecutor,
    val rfspy: RFSpy,
    val activePlugin: ActivePlugin,
    val rileyLinkUtil: RileyLinkUtil,
    val wakeAndTuneTaskProvider: Provider<WakeAndTuneTask>,
    val radioResponseProvider: Provider<RadioResponse>
) {

    @Suppress("PrivatePropertyName")
    private val ALLOWED_PUMP_UNREACHABLE = 10 * 60 * 1000 // 10 minutes

    protected var receiverDeviceAwakeForMinutes: Int = 1 // override this in constructor of specific implementation
    protected var receiverDeviceID: String? = null // String representation of receiver device (ex. Pump (xxxxxx) or Pod (yyyyyy))
    protected var lastGoodReceiverCommunicationTime: Long = 0
        get() {
            // If we have a value of zero, we need to load from prefs.
            if (field == 0L) {
                field = preferences.get(RileyLinkLongKey.LastGoodDeviceCommunicationTime)
                // Might still be zero, but that's fine.
            }
            val minutesAgo: Double = (System.currentTimeMillis() - field) / (1000.0 * 60.0)
            aapsLogger.debug(LTag.PUMPBTCOMM, "Last good pump communication was $minutesAgo minutes ago.")
            return field
        }

    private var nextWakeUpRequired = 0L
    private var timeoutCount = 0

    @Throws(RileyLinkCommunicationException::class)
    protected open fun sendAndListen(msg: T, timeoutMs: Int, repeatCount: Int = 0, retryCount: Int = 0, extendPreambleMs: Int = 0): T {
        // internal flag

        val showPumpMessages = true
        if (showPumpMessages) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Sent:" + shortHexString(msg.getTxData()))
        }

        val rfSpyResponse = rfspy.transmitThenReceive(
            RadioPacket(rileyLinkUtil, msg.getTxData()),
            0.toByte(), repeatCount.toByte(), 0.toByte(), 0.toByte(), timeoutMs, retryCount.toByte(), extendPreambleMs
        )

        val radioResponse = rfSpyResponse?.getRadioResponse() ?: throw RileyLinkCommunicationException(RileyLinkBLEError.Interrupted, null)
        val response = createResponseMessage(radioResponse.getPayload())

        if (response.isValid()) {
            // Mark this as the last time we heard from the pump.
            rememberLastGoodDeviceCommunicationTime()
        } else {
            aapsLogger.warn(
                LTag.PUMPBTCOMM, String.format(
                    Locale.ENGLISH, "isDeviceReachable. Response is invalid ! [noResponseFromRileyLink=%b, interrupted=%b, timeout=%b, unknownCommand=%b, invalidParam=%b]",
                    rfSpyResponse.wasNoResponseFromRileyLink(), rfSpyResponse.wasInterrupted(), rfSpyResponse.wasTimeout(), rfSpyResponse.isUnknownCommand(), rfSpyResponse.isInvalidParam()
                )
            )

            if (rfSpyResponse.wasTimeout()) {
                if (rileyLinkServiceData.targetDevice.tuneUpEnabled) {
                    timeoutCount++

                    val diff = System.currentTimeMillis() - getPumpDevice().lastConnectionTimeMillis

                    if (diff > ALLOWED_PUMP_UNREACHABLE) {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "We reached max time that Pump can be unreachable. Starting Tuning.")
                        serviceTaskExecutor.startTask(wakeAndTuneTaskProvider.get())
                        timeoutCount = 0
                    }
                }

                throw RileyLinkCommunicationException(RileyLinkBLEError.Timeout, null)
            } else if (rfSpyResponse.wasInterrupted()) {
                throw RileyLinkCommunicationException(RileyLinkBLEError.Interrupted, null)
            } else if (rfSpyResponse.wasNoResponseFromRileyLink()) {
                throw RileyLinkCommunicationException(RileyLinkBLEError.NoResponse, null)
            }
        }

        if (showPumpMessages) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Received:" + shortHexString(rfSpyResponse.getRadioResponse().getPayload()))
        }

        return response
    }

    abstract fun createResponseMessage(payload: ByteArray): T

    abstract fun setPumpDeviceState(pumpDeviceState: PumpDeviceState)

    fun wakeUp(force: Boolean) {
        wakeUp(receiverDeviceAwakeForMinutes, force)
    }

    fun getNotConnectedCount(): Int {
        return rfspy.notConnectedCount
    }

    // FIXME change wakeup
    // TODO we might need to fix this. Maybe make pump awake for shorter time (battery factor for pump) - Andy
    fun wakeUp(@Suppress("unused") durationMinutes: Int, force: Boolean) {
        // If it has been longer than n minutes, do wakeup. Otherwise assume pump is still awake.
        // **** FIXME: this wakeup doesn't seem to work well... must revisit
        // receiverDeviceAwakeForMinutes = duration_minutes;

        setPumpDeviceState(PumpDeviceState.WakingUp)

        if (force) nextWakeUpRequired = 0L

        if (System.currentTimeMillis() > nextWakeUpRequired) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Waking pump...")

            val pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData) // simple
            val resp = rfspy.transmitThenReceive(
                RadioPacket(rileyLinkUtil, pumpMsgContent), 0.toByte(), 200.toByte(),
                0.toByte(), 0.toByte(), 25000, 0.toByte()
            )
            aapsLogger.info(LTag.PUMPBTCOMM, "wakeup: raw response is " + shortHexString(resp?.raw))

            // FIXME wakeUp successful !!!!!!!!!!!!!!!!!!
            nextWakeUpRequired = System.currentTimeMillis() + (receiverDeviceAwakeForMinutes.toLong() * 60 * 1000)
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Last pump communication was recent, not waking pump.")
        }

        // long lastGoodPlus = getLastGoodReceiverCommunicationTime() + (receiverDeviceAwakeForMinutes * 60 * 1000);
        //
        // if (System.currentTimeMillis() > lastGoodPlus || force) {
        // LOG.info("Waking pump...");
        //
        // byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.PowerOn);
        // RFSpyResponse resp = rfspy.transmitThenReceive(new RadioPacket(pumpMsgContent), (byte) 0, (byte) 200, (byte)
        // 0, (byte) 0, 15000, (byte) 0);
        // LOG.info("wakeup: raw response is " + ByteUtil.INSTANCE.shortHexString(resp.getRaw()));
        // } else {
        // LOG.trace("Last pump communication was recent, not waking pump.");
        // }
    }

    fun setRadioFrequencyForPump(freqMHz: Double) {
        rfspy.setBaseFrequency(freqMHz)
    }

    fun tuneForDevice(): Double {
        return scanForDevice(rileyLinkServiceData.rileyLinkTargetFrequency.scanFrequencies)
    }

    /**
     * If user changes pump and one pump is running in US freq, and other in WW, then previously set frequency would be
     * invalid,
     * so we would need to retune. This checks that saved frequency is correct range.
     *
     * @param frequency
     * @return
     */
    fun isValidFrequency(frequency: Double): Boolean {
        val scanFrequencies = rileyLinkServiceData.rileyLinkTargetFrequency.scanFrequencies

        return if (scanFrequencies.size == 1) isSame(scanFrequencies[0], frequency)
        else (scanFrequencies[0] <= frequency && scanFrequencies[scanFrequencies.size - 1] >= frequency)
    }

    /**
     * Do device connection, with wakeup
     *
     * @return
     */
    abstract fun tryToConnectToDevice(): Boolean

    private fun scanForDevice(frequencies: DoubleArray): Double {
        aapsLogger.info(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Scanning for receiver (%s)", receiverDeviceID))
        wakeUp(receiverDeviceAwakeForMinutes, false)
        val results = FrequencyScanResults()

        for (i in frequencies.indices) {
            val tries = 3
            val trial = FrequencyTrial()
            trial.frequencyMHz = frequencies[i]
            rfspy.setBaseFrequency(frequencies[i])

            var sumRSSI = 0
            (0 until tries).forEach { j ->
                val pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData)
                val resp = rfspy.transmitThenReceive(
                    RadioPacket(rileyLinkUtil, pumpMsgContent), 0.toByte(), 0.toByte(),
                    0.toByte(), 0.toByte(), 1250, 0.toByte()
                )
                if (resp?.wasTimeout() == true) {
                    aapsLogger.error(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "scanForPump: Failed to find pump at frequency %.3f", frequencies[i]))
                } else if (resp?.looksLikeRadioPacket() == true) {
                    val radioResponse = radioResponseProvider.get()

                    try {
                        radioResponse.init(resp.raw)

                        if (radioResponse.isValid()) {
                            val rssi = calculateRssi(radioResponse.rssi)
                            sumRSSI += rssi
                            trial.rssiList.add(rssi)
                            trial.successes++
                        } else {
                            aapsLogger.warn(LTag.PUMPBTCOMM, "Failed to parse radio response: " + shortHexString(resp.raw))
                            trial.rssiList.add(-99)
                        }
                    } catch (_: RileyLinkCommunicationException) {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "Failed to decode radio response: " + shortHexString(resp.raw))
                        trial.rssiList.add(-99)
                    }
                } else {
                    aapsLogger.error(LTag.PUMPBTCOMM, "scanForPump: raw response is " + shortHexString(resp?.raw))
                    trial.rssiList.add(-99)
                }
                trial.tries++
            }
            sumRSSI = (sumRSSI + -99.0 * (trial.tries - trial.successes)).toInt()
            trial.averageRSSI2 = (sumRSSI).toDouble() / (trial.tries).toDouble()

            trial.calculateAverage()

            results.trials.add(trial)
        }

        results.dateTime = System.currentTimeMillis()

        val stringBuilder = StringBuilder("Scan results:\n")

        for (k in results.trials.indices) {
            val one = results.trials[k]

            stringBuilder.append(String.format("Scan Result[%s]: Freq=%s, avg RSSI = %s\n", k, one.frequencyMHz, one.averageRSSI.toString() + ", RSSIs =" + one.rssiList))
        }

        aapsLogger.info(LTag.PUMPBTCOMM, stringBuilder.toString())

        results.sort() // sorts in ascending order

        val bestTrial = results.trials[results.trials.size - 1]
        results.bestFrequencyMHz = bestTrial.frequencyMHz
        if (bestTrial.successes > 0) {
            rfspy.setBaseFrequency(results.bestFrequencyMHz)
            aapsLogger.debug(LTag.PUMPBTCOMM, "Best frequency found: " + results.bestFrequencyMHz)
            return results.bestFrequencyMHz
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "No pump response during scan.")
            return 0.0
        }
    }

    private fun calculateRssi(rssiIn: Int): Int {
        val rssiOffset = 73
        val outRssi =
            if (rssiIn >= 128) ((rssiIn - 256) / 2) - rssiOffset
            else (rssiIn / 2) - rssiOffset
        return outRssi
    }

    abstract fun createPumpMessageContent(type: RLMessageType): ByteArray

    protected fun rememberLastGoodDeviceCommunicationTime() {
        lastGoodReceiverCommunicationTime = System.currentTimeMillis()

        preferences.put(RileyLinkLongKey.LastGoodDeviceCommunicationTime, lastGoodReceiverCommunicationTime)

        getPumpDevice().setLastCommunicationToNow()
    }

    fun clearNotConnectedCount() {
        rfspy.notConnectedCount = 0
    }

    private fun getPumpDevice(): RileyLinkPumpDevice {
        return activePlugin.activePump as RileyLinkPumpDevice
    }

    abstract fun isDeviceReachable(): Boolean
}

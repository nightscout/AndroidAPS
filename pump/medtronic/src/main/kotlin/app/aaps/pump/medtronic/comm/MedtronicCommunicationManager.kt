package app.aaps.pump.medtronic.comm

import android.os.SystemClock
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.DateTimeUtil
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.pump.common.hw.rileylink.RileyLinkCommunicationManager
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.RFSpy
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkCommunicationException
import app.aaps.pump.common.hw.rileylink.ble.data.RadioPacket
import app.aaps.pump.common.hw.rileylink.ble.data.RadioResponse
import app.aaps.pump.common.hw.rileylink.ble.defs.RLMessageType
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkLongKey
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.pump.medtronic.comm.history.RawHistoryPage
import app.aaps.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryResult
import app.aaps.pump.medtronic.comm.message.CarelinkLongMessageBody
import app.aaps.pump.medtronic.comm.message.CarelinkShortMessageBody
import app.aaps.pump.medtronic.comm.message.GetHistoryPageCarelinkMessageBody
import app.aaps.pump.medtronic.comm.message.MessageBody
import app.aaps.pump.medtronic.comm.message.PacketType
import app.aaps.pump.medtronic.comm.message.PumpAckMessageBody
import app.aaps.pump.medtronic.comm.message.PumpMessage
import app.aaps.pump.medtronic.data.dto.BasalProfile
import app.aaps.pump.medtronic.data.dto.BatteryStatusDTO
import app.aaps.pump.medtronic.data.dto.ClockDTO
import app.aaps.pump.medtronic.data.dto.PumpSettingDTO
import app.aaps.pump.medtronic.data.dto.TempBasalPair
import app.aaps.pump.medtronic.defs.MedtronicCommandType
import app.aaps.pump.medtronic.defs.MedtronicCommandType.Companion.getSettings
import app.aaps.pump.medtronic.defs.MedtronicDeviceType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.util.MedtronicUtil
import app.aaps.pump.medtronic.util.MedtronicUtil.Companion.createByteArray
import app.aaps.pump.medtronic.util.MedtronicUtil.Companion.getByteArrayFromUnsignedShort
import org.joda.time.LocalDateTime
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Original file created by geoff on 5/30/16.
 *
 *
 * Split into 2 implementations, so that we can split it by target device. - Andy
 * This was mostly rewritten from Original version, and lots of commands and
 * functionality added.
 */
@Singleton
class MedtronicCommunicationManager @Inject constructor(
    private val medtronicPumpStatus: MedtronicPumpStatus,
    private val medtronicPumpPlugin: MedtronicPumpPlugin,
    private val medtronicConverter: MedtronicConverter,
    private val medtronicUtil: MedtronicUtil,
    private val medtronicPumpHistoryDecoder: MedtronicPumpHistoryDecoder,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    rileyLinkServiceData: RileyLinkServiceData,
    serviceTaskExecutor: ServiceTaskExecutor,
    rfspy: RFSpy,
    activePlugin: ActivePlugin,
    rileyLinkUtil: RileyLinkUtil,
    wakeAndTuneTaskProvider: Provider<WakeAndTuneTask>,
    radioResponseProvider: Provider<RadioResponse>
) : RileyLinkCommunicationManager<PumpMessage>(
    aapsLogger, preferences, rileyLinkServiceData, serviceTaskExecutor, rfspy, activePlugin, rileyLinkUtil, wakeAndTuneTaskProvider, radioResponseProvider
) {

    companion object {

        private const val MAX_COMMAND_TRIES = 3
        private const val DEFAULT_TIMEOUT = 2000
        private const val RILEYLINK_TIMEOUT: Long = 15 * 60 * 1000L // 15 min
    }

    var errorResponse: String? = null
        private set
    private val debugSetCommands = false
    private var doWakeUpBeforeCommand = true

    @Inject
    fun onInit() {
        // we can't do this in the constructor, as sp only gets injected after the constructor has returned
        medtronicPumpStatus.previousConnection = preferences.get(RileyLinkLongKey.LastGoodDeviceCommunicationTime)
    }

    override fun createResponseMessage(payload: ByteArray): PumpMessage {
        return PumpMessage(aapsLogger, payload)
    }

    override fun setPumpDeviceState(pumpDeviceState: PumpDeviceState) {
        medtronicPumpStatus.pumpDeviceState = pumpDeviceState
    }

    fun setDoWakeUpBeforeCommand(doWakeUp: Boolean) {
        doWakeUpBeforeCommand = doWakeUp
    }

    override fun isDeviceReachable(): Boolean {
        return isDeviceReachable(false)
    }

    /**
     * We do actual wakeUp and compare PumpModel with currently selected one. If returned model is
     * not Unknown, pump is reachable.
     *
     * @return
     */
    private fun isDeviceReachable(canPreventTuneUp: Boolean): Boolean {
        val state = medtronicPumpStatus.pumpDeviceState
        if (state !== PumpDeviceState.PumpUnreachable) medtronicPumpStatus.pumpDeviceState = PumpDeviceState.WakingUp
        for (retry in 0..4) {
            aapsLogger.debug(LTag.PUMPCOMM, "isDeviceReachable. Waking pump... " + if (retry != 0) " (retry $retry)" else "")
            val connected = connectToDevice()
            if (connected) return true
            SystemClock.sleep(1000)
        }
        if (state !== PumpDeviceState.PumpUnreachable) medtronicPumpStatus.pumpDeviceState = PumpDeviceState.PumpUnreachable
        if (!canPreventTuneUp) {
            val diff = System.currentTimeMillis() - medtronicPumpStatus.lastConnection
            if (diff > RILEYLINK_TIMEOUT) {
                serviceTaskExecutor.startTask(wakeAndTuneTaskProvider.get())
            }
        }
        return false
    }

    private fun connectToDevice(): Boolean {
        val state = medtronicPumpStatus.pumpDeviceState

        // check connection
        val pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData) // simple
        val rfSpyResponse = rfspy.transmitThenReceive(
            RadioPacket(rileyLinkUtil, pumpMsgContent), 0.toByte(), 200.toByte(), 0.toByte(), 0.toByte(), 25000, 0.toByte()
        )
        aapsLogger.info(LTag.PUMPCOMM, "wakeup: raw response is " + ByteUtil.shortHexString(rfSpyResponse?.raw))
        if (rfSpyResponse?.wasTimeout() == true) {
            aapsLogger.error(LTag.PUMPCOMM, "isDeviceReachable. Failed to find pump (timeout).")
        } else if (rfSpyResponse?.looksLikeRadioPacket() == true) {
            val radioResponse = radioResponseProvider.get()
            try {
                radioResponse.init(rfSpyResponse.raw)
                if (radioResponse.isValid()) {
                    val pumpResponse = createResponseMessage(radioResponse.getPayload())
                    if (!pumpResponse.isValid()) {
                        aapsLogger.warn(
                            LTag.PUMPCOMM, String.format(
                                Locale.ENGLISH, "Response is invalid ! [interrupted=%b, timeout=%b]", rfSpyResponse.wasInterrupted(),
                                rfSpyResponse.wasTimeout()
                            )
                        )
                    } else {

                        // radioResponse.rssi;
                        val dataResponse = medtronicConverter.decodeModel(pumpResponse.rawContent)
                        val pumpModel = dataResponse as MedtronicDeviceType?
                        val valid = pumpModel !== MedtronicDeviceType.Unknown_Device
                        if (!medtronicUtil.isModelSet && valid) {
                            medtronicUtil.medtronicPumpModel = pumpModel!!
                            medtronicUtil.isModelSet = true
                        }
                        aapsLogger.debug(
                            LTag.PUMPCOMM, String.format(
                                Locale.ENGLISH, "isDeviceReachable. PumpModel is %s - Valid: %b (rssi=%d)", medtronicUtil.medtronicPumpModel, valid,
                                radioResponse.rssi
                            )
                        )
                        if (valid) {
                            if (state === PumpDeviceState.PumpUnreachable)
                                medtronicPumpStatus.pumpDeviceState = PumpDeviceState.WakingUp
                            else
                                medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Sleeping
                            rememberLastGoodDeviceCommunicationTime()
                            return true
                        } else {
                            if (state !== PumpDeviceState.PumpUnreachable) medtronicPumpStatus.pumpDeviceState = PumpDeviceState.PumpUnreachable
                        }
                    }
                } else {
                    aapsLogger.warn(
                        LTag.PUMPCOMM, "isDeviceReachable. Failed to parse radio response: "
                            + ByteUtil.shortHexString(rfSpyResponse.raw)
                    )
                }
            } catch (_: RileyLinkCommunicationException) {
                aapsLogger.warn(
                    LTag.PUMPCOMM, "isDeviceReachable. Failed to decode radio response: "
                        + ByteUtil.shortHexString(rfSpyResponse.raw)
                )
            }
        } else {
            aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Unknown response: " + ByteUtil.shortHexString(rfSpyResponse?.raw))
        }
        return false
    }

    override fun tryToConnectToDevice(): Boolean {
        return isDeviceReachable(true)
    }

    @Throws(RileyLinkCommunicationException::class)
    private fun runCommandWithArgs(msg: PumpMessage): PumpMessage {
        if (debugSetCommands) aapsLogger.debug(LTag.PUMPCOMM, "Run command with Args: ")
        val rVal: PumpMessage
        val shortMessage = makePumpMessage(msg.commandType, CarelinkShortMessageBody(byteArrayOf(0)))
        // look for ack from short message
        val shortResponse = sendAndListen(shortMessage)
        return if (shortResponse.commandType === MedtronicCommandType.CommandACK) {
            if (debugSetCommands) aapsLogger.debug(LTag.PUMPCOMM, "Run command with Args: Got ACK response")
            rVal = sendAndListen(msg)
            if (debugSetCommands) aapsLogger.debug(LTag.PUMPCOMM, "2nd Response: $rVal")
            rVal
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "runCommandWithArgs: Pump did not ack Attention packet")
            PumpMessage(aapsLogger, "No ACK after Attention packet.")
        }
    }

    @Suppress("SameParameterValue")
    @Throws(RileyLinkCommunicationException::class)
    private fun runCommandWithFrames(commandType: MedtronicCommandType, frames: List<List<Byte>>): PumpMessage? {
        aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: " + commandType.name)
        var rVal: PumpMessage? = null
        val shortMessage = makePumpMessage(commandType, CarelinkShortMessageBody(byteArrayOf(0)))
        // look for ack from short message
        val shortResponse = sendAndListen(shortMessage)
        if (shortResponse.commandType !== MedtronicCommandType.CommandACK) {
            aapsLogger.error(LTag.PUMPCOMM, "runCommandWithFrames: Pump did not ack Attention packet")
            return PumpMessage(aapsLogger, "No ACK after start message.")
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: Got ACK response for Attention packet")
        }
        var frameNr = 1
        for (frame in frames) {
            val frameData = createByteArray(frame)

            // aapsLogger.debug(LTag.PUMPCOMM,"Frame {} data:\n{}", frameNr, ByteUtil.getCompactString(frameData));
            val msg = makePumpMessage(commandType, CarelinkLongMessageBody(frameData))
            rVal = sendAndListen(msg)

            // aapsLogger.debug(LTag.PUMPCOMM,"PumpResponse: " + rval);
            if (rVal.commandType !== MedtronicCommandType.CommandACK) {
                aapsLogger.error(LTag.PUMPCOMM, "runCommandWithFrames: Pump did not ACK frame #$frameNr")
                aapsLogger.error(
                    LTag.PUMPCOMM, String.format(
                        Locale.ENGLISH, "Run command with Frames FAILED (command=%s, response=%s)", commandType.name,
                        rVal.toString()
                    )
                )
                return PumpMessage(aapsLogger, "No ACK after frame #$frameNr")
            } else {
                aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: Got ACK response for frame #$frameNr")
            }
            frameNr++
        }
        return rVal
    }

    fun getPumpHistory(lastEntry: PumpHistoryEntry?, targetDate: LocalDateTime?): PumpHistoryResult {
        val pumpTotalResult = PumpHistoryResult(aapsLogger, lastEntry, if (targetDate == null) null else DateTimeUtil.toATechDate(targetDate))
        if (doWakeUpBeforeCommand) wakeUp(receiverDeviceAwakeForMinutes, false)
        aapsLogger.debug(LTag.PUMPCOMM, "Current command: " + medtronicUtil.getCurrentCommand())
        medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Active
        var doneWithError = false
        for (pageNumber in 0..4) {
            val rawHistoryPage = RawHistoryPage(aapsLogger)
            // wakeUp(receiverDeviceAwakeForMinutes, false);
            val getHistoryMsg = makePumpMessage(
                MedtronicCommandType.GetHistoryData,
                GetHistoryPageCarelinkMessageBody(pageNumber)
            )
            aapsLogger.info(LTag.PUMPCOMM, "getPumpHistory: Page $pageNumber")
            // aapsLogger.info(LTag.PUMPCOMM,"getPumpHistoryPage("+pageNumber+"): "+ByteUtil.shortHexString(getHistoryMsg.getTxData()));
            // Ask the pump to transfer history (we get first frame?)
            var firstResponse: PumpMessage? = null
            var failed = false
            medtronicUtil.setCurrentCommand(MedtronicCommandType.GetHistoryData, pageNumber, null)
            for (retries in 0 until MAX_COMMAND_TRIES) {
                try {
                    firstResponse = runCommandWithArgs(getHistoryMsg)
                    failed = false
                    break
                } catch (_: RileyLinkCommunicationException) {
                    aapsLogger.error(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "First call for PumpHistory failed (retry=%d)", retries))
                    failed = true
                }
            }
            if (failed) {
                medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Sleeping
                return pumpTotalResult
            }

            // aapsLogger.info(LTag.PUMPCOMM,"getPumpHistoryPage("+pageNumber+"): " + ByteUtil.shortHexString(firstResponse.getContents()));
            val ackMsg = makePumpMessage(MedtronicCommandType.CommandACK, PumpAckMessageBody())
            var currentResponse = GetHistoryPageCarelinkMessageBody(firstResponse!!.messageBody!!.txData)
            var expectedFrameNum = 1
            var done = false
            // while (expectedFrameNum == currentResponse.getFrameNumber()) {
            var failures = 0
            while (!done) {
                // examine current response for problems.
                val frameData = currentResponse.frameData
                if (frameData.isNotEmpty() && currentResponse.frameNumber == expectedFrameNum) {
                    // success! got a frame.
                    if (frameData.size != 64) {
                        aapsLogger.warn(LTag.PUMPCOMM, "Expected frame of length 64, got frame of length " + frameData.size)
                        // but append it anyway?
                    }
                    // handle successful frame data
                    rawHistoryPage.appendData(currentResponse.frameData)
                    // RileyLinkMedtronicService.getInstance().announceProgress(((100 / 16) *
                    // currentResponse.getFrameNumber() + 1));
                    medtronicUtil.setCurrentCommand(
                        MedtronicCommandType.GetHistoryData, pageNumber,
                        currentResponse.frameNumber
                    )
                    aapsLogger.info(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "getPumpHistory: Got frame %d of Page %d", currentResponse.frameNumber, pageNumber))
                    // Do we need to ask for the next frame?
                    if (expectedFrameNum < 16) { // This number may not be correct for pumps other than 522/722
                        expectedFrameNum++
                    } else {
                        done = true // successful completion
                    }
                } else {
                    if (frameData.isEmpty()) {
                        aapsLogger.error(LTag.PUMPCOMM, "null frame data, retrying")
                    } else if (currentResponse.frameNumber != expectedFrameNum) {
                        aapsLogger.warn(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Expected frame number %d, received %d (retrying)", expectedFrameNum, currentResponse.frameNumber))
                    }
                    failures++
                    if (failures == 6) {
                        aapsLogger.error(
                            LTag.PUMPCOMM, String.format(
                                Locale.ENGLISH, "getPumpHistory: 6 failures in attempting to download frame %d of page %d, giving up.",
                                expectedFrameNum, pageNumber
                            )
                        )
                        done = true // failure completion.
                        doneWithError = true
                    }
                }
                if (!done) {
                    // ask for next frame
                    var nextMsg: PumpMessage? = null
                    for (retries in 0 until MAX_COMMAND_TRIES) {
                        try {
                            nextMsg = sendAndListen(ackMsg)
                            break
                        } catch (_: RileyLinkCommunicationException) {
                            aapsLogger.error(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Problem acknowledging frame response. (retry=%d)", retries))
                        }
                    }
                    if (nextMsg != null)
                        currentResponse = GetHistoryPageCarelinkMessageBody(nextMsg.messageBody!!.txData)
                    else {
                        aapsLogger.error(LTag.PUMPCOMM, "We couldn't acknowledge frame from pump, aborting operation.")
                    }
                }
            }
            if (rawHistoryPage.length != 1024) {
                aapsLogger.warn(
                    LTag.PUMPCOMM, "getPumpHistory: short page.  Expected length of 1024, found length of "
                        + rawHistoryPage.length
                )
                doneWithError = true
            }
            if (!rawHistoryPage.isChecksumOK) {
                aapsLogger.error(LTag.PUMPCOMM, "getPumpHistory: checksum is wrong")
                doneWithError = true
            }
            if (doneWithError) {
                medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Sleeping
                return pumpTotalResult
            }
            rawHistoryPage.dumpToDebug()
            val medtronicHistoryEntries = medtronicPumpHistoryDecoder.processPageAndCreateRecords(rawHistoryPage)
            aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "getPumpHistory: Found %d history entries.", medtronicHistoryEntries.size))
            pumpTotalResult.addHistoryEntries(medtronicHistoryEntries) //, pageNumber)
            aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "getPumpHistory: Search status: Search finished: %b", pumpTotalResult.isSearchFinished))
            if (pumpTotalResult.isSearchFinished) {
                medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Sleeping
                return pumpTotalResult
            }
        }
        medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Sleeping
        return pumpTotalResult
    }

    override fun createPumpMessageContent(type: RLMessageType): ByteArray {
        return when (type) {
            RLMessageType.PowerOn        -> medtronicUtil.buildCommandPayload(rileyLinkServiceData, MedtronicCommandType.RFPowerOn, byteArrayOf(2, 1, receiverDeviceAwakeForMinutes.toByte()))
            RLMessageType.ReadSimpleData -> medtronicUtil.buildCommandPayload(rileyLinkServiceData, MedtronicCommandType.PumpModel, null)
            // else                         -> ByteArray(0)
        }
    }

    private fun makePumpMessage(messageType: MedtronicCommandType, body: ByteArray? = null): PumpMessage {
        return makePumpMessage(messageType, body?.let { CarelinkShortMessageBody(it) }
            ?: CarelinkShortMessageBody())
    }

    private fun makePumpMessage(messageType: MedtronicCommandType?, messageBody: MessageBody): PumpMessage {
        val msg = PumpMessage(aapsLogger)
        msg.init(PacketType.Carelink, rileyLinkServiceData.pumpIDBytes, messageType, messageBody)
        return msg
    }

    /**
     * Main wrapper method for sending data - (for getting responses)
     *
     * @param commandType
     * @param bodyData
     * @param timeoutMs
     * @return
     */
    @Throws(RileyLinkCommunicationException::class)
    private fun sendAndGetResponse(commandType: MedtronicCommandType, bodyData: ByteArray? = null, timeoutMs: Int = DEFAULT_TIMEOUT): PumpMessage {
        // wakeUp
        if (doWakeUpBeforeCommand) wakeUp(receiverDeviceAwakeForMinutes, false)
        medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Active

        // create message
        val msg: PumpMessage = bodyData?.let { makePumpMessage(commandType, it) } ?: makePumpMessage(commandType)

        // send and wait for response
        val response = sendAndListen(msg, timeoutMs)
        medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Sleeping
        return response
    }

    @Throws(RileyLinkCommunicationException::class)
    private fun sendAndListen(msg: PumpMessage): PumpMessage {
        return sendAndListen(msg, 4000) // 2000
    }

    private inline fun <reified T> sendAndGetResponseWithCheck(
        commandType: MedtronicCommandType,
        bodyData: ByteArray? = null,
        decode: (pumpType: PumpType, commandType: MedtronicCommandType, rawContent: ByteArray) -> T
    ): T? {
        aapsLogger.debug(LTag.PUMPCOMM, "getDataFromPump: $commandType")
        for (retries in 0 until MAX_COMMAND_TRIES) {
            try {
                val response = sendAndGetResponse(commandType, bodyData, DEFAULT_TIMEOUT + DEFAULT_TIMEOUT * retries)
                val check = checkResponseContent(response, commandType.commandDescription, commandType.expectedLength)
                if (check == null) {

                    checkResponseRawContent(response.rawContent, commandType) { return@sendAndGetResponseWithCheck null }

                    val dataResponse = decode(medtronicPumpStatus.pumpType, commandType, response.rawContent)
                    if (dataResponse != null) {
                        errorResponse = null
                        aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Converted response for %s is %s.", commandType.name, dataResponse))
                        return dataResponse
                    } else {
                        errorResponse = "Error decoding response."
                    }
                } else {
                    errorResponse = check
                    // return null;
                }
            } catch (e: RileyLinkCommunicationException) {
                aapsLogger.warn(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Error getting response from RileyLink (error=%s, retry=%d)", e.message, retries + 1))
            }
        }
        return null
    }

    private inline fun checkResponseRawContent(rawContent: ByteArray?, commandType: MedtronicCommandType, errorCase: () -> Unit) {
        if (rawContent?.isEmpty() != false && commandType != MedtronicCommandType.PumpModel) {
            aapsLogger.warn(
                LTag.PUMPCOMM, String.format(
                    Locale.ENGLISH, "Content is empty or too short, no data to convert (type=%s,isNull=%b,length=%s)",
                    commandType.name, rawContent == null, rawContent?.size ?: "-"
                )
            )
            errorCase.invoke()
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Raw response before convert: " + ByteUtil.shortHexString(rawContent))
        }
    }

    private fun checkResponseContent(response: PumpMessage, method: String, expectedLength: Int): String? {
        if (!response.isValid()) {
            val responseData = String.format("%s: Invalid response.", method)
            aapsLogger.warn(LTag.PUMPCOMM, responseData)
            return responseData
        }
        val contents = response.rawContent
        return if (contents.isNotEmpty()) {
            if (contents.size >= expectedLength) {
                aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "%s: Content: %s", method, ByteUtil.shortHexString(contents)))
                null
            } else {
                val responseData = String.format(
                    "%s: Cannot return data. Data is too short [expected=%s, received=%s].", method, ""
                        + expectedLength, "" + contents.size
                )
                aapsLogger.warn(LTag.PUMPCOMM, responseData)
                responseData
            }
        } else {
            val responseData = String.format("%s: Cannot return data. Zero length response.", method)
            aapsLogger.warn(LTag.PUMPCOMM, responseData)
            responseData
        }
    }

    // PUMP SPECIFIC COMMANDS
    fun getRemainingInsulin(): Double? {
        return sendAndGetResponseWithCheck(MedtronicCommandType.GetRemainingInsulin) { _, _, rawContent ->
            medtronicConverter.decodeRemainingInsulin(rawContent)
        }
    }

    fun getPumpModel(): MedtronicDeviceType? {
        return sendAndGetResponseWithCheck(MedtronicCommandType.PumpModel) { _, _, rawContent ->
            medtronicConverter.decodeModel(rawContent)
        }
    }

    fun getBasalProfile(): BasalProfile? {

        // wakeUp
        if (doWakeUpBeforeCommand) wakeUp(receiverDeviceAwakeForMinutes, false)
        val commandType = MedtronicCommandType.GetBasalProfileSTD
        aapsLogger.debug(LTag.PUMPCOMM, "getDataFromPump: $commandType")
        medtronicUtil.setCurrentCommand(commandType)
        medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Active
        for (retries in 0..MAX_COMMAND_TRIES) {
            try {
                // create message
                val msg: PumpMessage = makePumpMessage(commandType)

                // send and wait for response
                var response = sendAndListen(msg, DEFAULT_TIMEOUT + DEFAULT_TIMEOUT * retries)

//                aapsLogger.debug(LTag.PUMPCOMM,"1st Response: " + HexDump.toHexStringDisplayable(response.getRawContent()));
//                aapsLogger.debug(LTag.PUMPCOMM,"1st Response: " + HexDump.toHexStringDisplayable(response.getMessageBody().getTxData()));
                val check = checkResponseContent(response, commandType.commandDescription, 1)
                var data: ByteArray = byteArrayOf()
                if (check == null) {
                    data = response.rawContentOfFrame
                    val ackMsg = makePumpMessage(MedtronicCommandType.CommandACK, PumpAckMessageBody())
                    while (checkIfWeHaveMoreData(commandType, response, data)) {
                        response = sendAndListen(ackMsg, DEFAULT_TIMEOUT + DEFAULT_TIMEOUT * retries)

//                        aapsLogger.debug(LTag.PUMPCOMM,"{} Response: {}", runs, HexDump.toHexStringDisplayable(response2.getRawContent()));
//                        aapsLogger.debug(LTag.PUMPCOMM,"{} Response: {}", runs,
//                            HexDump.toHexStringDisplayable(response2.getMessageBody().getTxData()));
                        val check2 = checkResponseContent(response, commandType.commandDescription, 1)
                        if (check2 == null) {
                            data = ByteUtil.concat(data, response.rawContentOfFrame)
                        } else {
                            errorResponse = check2
                            aapsLogger.error(LTag.PUMPCOMM, "Error with response got GetProfile: $check2")
                        }
                    }
                } else {
                    errorResponse = check
                }

                aapsLogger.debug(LTag.PUMPCOMM, "End Response: {}", ByteUtil.getHex(data))

                val basalProfile: BasalProfile? = medtronicConverter.decodeBasalProfile(medtronicPumpPlugin.pumpDescription.pumpType, data)
                // checkResponseRawContent(data, commandType) {
                //     basalProfile = medtronicConverter.decodeBasalProfile(medtronicPumpPlugin.pumpDescription.pumpType, data)
                // }

                if (basalProfile != null) {
                    aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Converted response for %s is %s.", commandType.name, basalProfile))
                    medtronicUtil.setCurrentCommand(null)
                    medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Sleeping
                    return basalProfile
                }
            } catch (e: RileyLinkCommunicationException) {
                aapsLogger.error(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Error getting response from RileyLink (error=%s, retry=%d)", e.message, retries + 1))
            }
        }
        aapsLogger.warn(LTag.PUMPCOMM, "Error reading profile in max retries.")
        medtronicUtil.setCurrentCommand(null)
        medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Sleeping
        return null
    }

    private fun checkIfWeHaveMoreData(commandType: MedtronicCommandType, response: PumpMessage, data: ByteArray): Boolean {
        if (commandType === MedtronicCommandType.GetBasalProfileSTD || //
            commandType === MedtronicCommandType.GetBasalProfileA || //
            commandType === MedtronicCommandType.GetBasalProfileB
        ) {
            val responseRaw = response.rawContentOfFrame
            val last = responseRaw.size - 1
            aapsLogger.debug(LTag.PUMPCOMM, "Length: " + data.size)
            if (data.size >= BasalProfile.MAX_RAW_DATA_SIZE) {
                return false
            }
            return if (responseRaw.size < 2) {
                false
            } else !(responseRaw[last] == 0x00.toByte() && responseRaw[last - 1] == 0x00.toByte() && responseRaw[last - 2] == 0x00.toByte())
        }
        return false
    }

    fun getPumpTime(): ClockDTO? {
        val localTime = LocalDateTime()
        val responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.GetRealTimeClock) { _, _, rawContent ->
            medtronicConverter.decodeTime(rawContent)
        }
        if (responseObject != null) {
            return ClockDTO(localDeviceTime = localTime, pumpTime = responseObject)
        }
        return null
    }

    fun getTemporaryBasal(): TempBasalPair? {
        return sendAndGetResponseWithCheck(MedtronicCommandType.ReadTemporaryBasal) { _, _, rawContent ->
            if (rawContent.size >= 5) TempBasalPair(aapsLogger, rawContent)
            else {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Received invalid TempBasal response" + ByteUtil.getHex(rawContent))
                null
            }
        }
    }

    fun getPumpSettings(): Map<String, PumpSettingDTO>? {
        return sendAndGetResponseWithCheck(getSettings(medtronicUtil.medtronicPumpModel)) { _, _, rawContent ->
            medtronicConverter.decodeSettingsLoop(rawContent)
        }
    }

    fun setBolus(units: Double): Boolean {
        aapsLogger.info(LTag.PUMPCOMM, "setBolus: $units")
        return setCommand(MedtronicCommandType.SetBolus, medtronicUtil.getBolusStrokes(units))
    }

    fun setTemporaryBasal(tbr: TempBasalPair): Boolean {
        aapsLogger.info(LTag.PUMPCOMM, "setTBR: " + tbr.description)
        return setCommand(MedtronicCommandType.SetTemporaryBasal, tbr.asRawData)
    }

    fun setPumpTime(): Boolean {
        val gc = GregorianCalendar()
        gc.add(Calendar.SECOND, 5)
        aapsLogger.info(LTag.PUMPCOMM, "setPumpTime: " + DateTimeUtil.toString(gc))
        val yearByte = getByteArrayFromUnsignedShort(gc[Calendar.YEAR], true)
        // val i = 1
        // val data = ByteArray(8)
        // data[0] = 7
        // data[i] = gc[Calendar.HOUR_OF_DAY].toByte()
        // data[i + 1] = gc[Calendar.MINUTE].toByte()
        // data[i + 2] = gc[Calendar.SECOND].toByte()
        // val yearByte = getByteArrayFromUnsignedShort(gc[Calendar.YEAR], true)
        // data[i + 3] = yearByte[0]
        // data[i + 4] = yearByte[1]
        // data[i + 5] = (gc[Calendar.MONTH] + 1).toByte()
        // data[i + 6] = gc[Calendar.DAY_OF_MONTH].toByte()

        val timeData = byteArrayOf(
            7,
            gc[Calendar.HOUR_OF_DAY].toByte(),
            gc[Calendar.MINUTE].toByte(),
            gc[Calendar.SECOND].toByte(),
            yearByte[0],
            yearByte[1],
            (gc[Calendar.MONTH] + 1).toByte(),
            gc[Calendar.DAY_OF_MONTH].toByte()
        )

        //aapsLogger.info(LTag.PUMPCOMM,"setPumpTime: Body:  " + ByteUtil.getHex(data));
        return setCommand(MedtronicCommandType.SetRealTimeClock, timeData)
    }

    private fun setCommand(commandType: MedtronicCommandType, body: ByteArray): Boolean {
        for (retries in 0..MAX_COMMAND_TRIES) {
            try {
                if (doWakeUpBeforeCommand) wakeUp(false)
                if (debugSetCommands) aapsLogger.debug(
                    LTag.PUMPCOMM, String.format(
                        Locale.ENGLISH, "%s: Body - %s", commandType.commandDescription,
                        ByteUtil.getHex(body)
                    )
                )
                val msg = makePumpMessage(commandType, CarelinkLongMessageBody(body))
                val pumpMessage = runCommandWithArgs(msg)
                if (debugSetCommands) aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "%s: %s", commandType.commandDescription, pumpMessage.responseContent))
                if (pumpMessage.commandType === MedtronicCommandType.CommandACK) {
                    return true
                } else {
                    aapsLogger.warn(LTag.PUMPCOMM, "We received non-ACK response from pump: " + pumpMessage.responseContent)
                }
            } catch (e: RileyLinkCommunicationException) {
                aapsLogger.warn(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Error getting response from RileyLink (error=%s, retry=%d)", e.message, retries + 1))
            }
        }
        return false
    }

    fun cancelTBR(): Boolean {
        return setTemporaryBasal(TempBasalPair(0.0, false, 0))
    }

    fun getRemainingBattery(): BatteryStatusDTO? {
        return sendAndGetResponseWithCheck(MedtronicCommandType.GetBatteryStatus) { _, _, rawContent ->
            medtronicConverter.decodeBatteryStatus(rawContent)
        }
    }

    fun setBasalProfile(basalProfile: BasalProfile): Boolean {
        val basalProfileFrames = medtronicUtil.getBasalProfileFrames(basalProfile.rawData)
        for (retries in 0..MAX_COMMAND_TRIES) {
            var responseMessage: PumpMessage? = null
            try {
                responseMessage = runCommandWithFrames(
                    MedtronicCommandType.SetBasalProfileSTD,
                    basalProfileFrames
                )
                if (responseMessage!!.commandType === MedtronicCommandType.CommandACK) return true
            } catch (e: RileyLinkCommunicationException) {
                aapsLogger.warn(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Error getting response from RileyLink (error=%s, retry=%d)", e.message, retries + 1))
            }
            if (responseMessage != null) aapsLogger.warn(
                LTag.PUMPCOMM,
                String.format(
                    Locale.ENGLISH,
                    "Set Basal Profile: Invalid response: commandType=%s,rawData=%s",
                    responseMessage.commandType,
                    ByteUtil.shortHexString(responseMessage.rawContent)
                )
            ) else aapsLogger.warn(
                LTag.PUMPCOMM, "Set Basal Profile: Null response."
            )
        }
        return false
    }
}

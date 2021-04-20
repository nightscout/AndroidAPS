package info.nightscout.androidaps.plugins.pump.medtronic.comm

import android.os.SystemClock
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioResponse
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.RawHistoryPage
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryResult
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.*
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BatteryStatusDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.PumpSettingDTO
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType.Companion.getSettings
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil.Companion.createByteArray
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil.Companion.getByteArrayFromUnsignedShort
import org.joda.time.LocalDateTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.Throws

/**
 * Original file created by geoff on 5/30/16.
 *
 *
 * Split into 2 implementations, so that we can split it by target device. - Andy
 * This was mostly rewritten from Original version, and lots of commands and
 * functionality added.
 */
@Singleton
class MedtronicCommunicationManager  // This empty constructor must be kept, otherwise dagger injection might break!
@Inject constructor() : RileyLinkCommunicationManager<PumpMessage?>() {

    @Inject lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Inject lateinit var medtronicPumpPlugin: MedtronicPumpPlugin
    @Inject lateinit var medtronicConverter: MedtronicConverter
    @Inject lateinit var medtronicUtil: MedtronicUtil
    @Inject lateinit var medtronicPumpHistoryDecoder: MedtronicPumpHistoryDecoder
    
    private val MAX_COMMAND_TRIES = 3
    private val DEFAULT_TIMEOUT = 2000
    private val RILEYLINK_TIMEOUT: Long = 15 * 60 * 1000 // 15 min
        
    var errorResponse: String? = null
        private set
    private val debugSetCommands = false
    private var doWakeUpBeforeCommand = true

    @Inject
    open fun onInit(): Unit {
        // we can't do this in the constructor, as sp only gets injected after the constructor has returned
        medtronicPumpStatus.previousConnection = sp.getLong(
            RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L)
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
    fun isDeviceReachable(canPreventTuneUp: Boolean): Boolean {
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
                serviceTaskExecutor.startTask(WakeAndTuneTask(injector))
            }
        }
        return false
    }

    private fun connectToDevice(): Boolean {
        val state = medtronicPumpStatus.pumpDeviceState

        // check connection
        val pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData) // simple
        val rfSpyResponse = rfspy.transmitThenReceive(RadioPacket(injector, pumpMsgContent), 0.toByte(), 200.toByte(),
            0.toByte(), 0.toByte(), 25000, 0.toByte())
        aapsLogger.info(LTag.PUMPCOMM, "wakeup: raw response is " + ByteUtil.shortHexString(rfSpyResponse.raw))
        if (rfSpyResponse.wasTimeout()) {
            aapsLogger.error(LTag.PUMPCOMM, "isDeviceReachable. Failed to find pump (timeout).")
        } else if (rfSpyResponse.looksLikeRadioPacket()) {
            val radioResponse = RadioResponse(injector)
            try {
                radioResponse.init(rfSpyResponse.raw)
                if (radioResponse.isValid) {
                    val pumpResponse = createResponseMessage(radioResponse.payload)
                    if (!pumpResponse.isValid) {
                        aapsLogger.warn(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Response is invalid ! [interrupted=%b, timeout=%b]", rfSpyResponse.wasInterrupted(),
                            rfSpyResponse.wasTimeout()))
                    } else {

                        // radioResponse.rssi;
                        val dataResponse = medtronicConverter!!.convertResponse(medtronicPumpStatus.pumpType, MedtronicCommandType.PumpModel,
                            pumpResponse.rawContent)
                        val pumpModel = dataResponse as MedtronicDeviceType?
                        val valid = pumpModel !== MedtronicDeviceType.Unknown_Device
                        if (medtronicUtil.medtronicPumpModel == null && valid) {
                            medtronicUtil.medtronicPumpModel = pumpModel
                        }
                        aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "isDeviceReachable. PumpModel is %s - Valid: %b (rssi=%d)", pumpModel!!.name, valid,
                            radioResponse.rssi))
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
                    aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Failed to parse radio response: "
                        + ByteUtil.shortHexString(rfSpyResponse.raw))
                }
            } catch (e: RileyLinkCommunicationException) {
                aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Failed to decode radio response: "
                    + ByteUtil.shortHexString(rfSpyResponse.raw))
            }
        } else {
            aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Unknown response: " + ByteUtil.shortHexString(rfSpyResponse.raw))
        }
        return false
    }

    override fun tryToConnectToDevice(): Boolean {
        return isDeviceReachable(true)
    }

    @Throws(RileyLinkCommunicationException::class)
    private fun runCommandWithArgs(msg: PumpMessage): PumpMessage {
        if (debugSetCommands) aapsLogger.debug(LTag.PUMPCOMM, "Run command with Args: ")
        val rval: PumpMessage
        val shortMessage = makePumpMessage(msg.commandType, CarelinkShortMessageBody(byteArrayOf(0)))
        // look for ack from short message
        val shortResponse = sendAndListen(shortMessage)
        return if (shortResponse.commandType === MedtronicCommandType.CommandACK) {
            if (debugSetCommands) aapsLogger.debug(LTag.PUMPCOMM, "Run command with Args: Got ACK response")
            rval = sendAndListen(msg)
            if (debugSetCommands) aapsLogger.debug(LTag.PUMPCOMM, "2nd Response: $rval")
            rval
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "runCommandWithArgs: Pump did not ack Attention packet")
            PumpMessage(aapsLogger, "No ACK after Attention packet.")
        }
    }

    @Throws(RileyLinkCommunicationException::class)
    private fun runCommandWithFrames(commandType: MedtronicCommandType, frames: List<List<Byte>>): PumpMessage? {
        aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: " + commandType.name)
        var rval: PumpMessage? = null
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
            rval = sendAndListen(msg)

            // aapsLogger.debug(LTag.PUMPCOMM,"PumpResponse: " + rval);
            if (rval.commandType !== MedtronicCommandType.CommandACK) {
                aapsLogger.error(LTag.PUMPCOMM, "runCommandWithFrames: Pump did not ACK frame #$frameNr")
                aapsLogger.error(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Run command with Frames FAILED (command=%s, response=%s)", commandType.name,
                    rval.toString()))
                return PumpMessage(aapsLogger, "No ACK after frame #$frameNr")
            } else {
                aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: Got ACK response for frame #$frameNr")
            }
            frameNr++
        }
        return rval
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
            val getHistoryMsg = makePumpMessage(MedtronicCommandType.GetHistoryData,
                GetHistoryPageCarelinkMessageBody(pageNumber))
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
                } catch (e: RileyLinkCommunicationException) {
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
                if (frameData != null && frameData.size > 0
                    && currentResponse.frameNumber == expectedFrameNum) {
                    // success! got a frame.
                    if (frameData.size != 64) {
                        aapsLogger.warn(LTag.PUMPCOMM, "Expected frame of length 64, got frame of length " + frameData.size)
                        // but append it anyway?
                    }
                    // handle successful frame data
                    rawHistoryPage.appendData(currentResponse.frameData)
                    // RileyLinkMedtronicService.getInstance().announceProgress(((100 / 16) *
                    // currentResponse.getFrameNumber() + 1));
                    medtronicUtil.setCurrentCommand(MedtronicCommandType.GetHistoryData, pageNumber,
                        currentResponse.frameNumber)
                    aapsLogger.info(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "getPumpHistory: Got frame %d of Page %d", currentResponse.frameNumber, pageNumber))
                    // Do we need to ask for the next frame?
                    if (expectedFrameNum < 16) { // This number may not be correct for pumps other than 522/722
                        expectedFrameNum++
                    } else {
                        done = true // successful completion
                    }
                } else {
                    if (frameData == null) {
                        aapsLogger.error(LTag.PUMPCOMM, "null frame data, retrying")
                    } else if (currentResponse.frameNumber != expectedFrameNum) {
                        aapsLogger.warn(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Expected frame number %d, received %d (retrying)", expectedFrameNum,
                            currentResponse.frameNumber))
                    } else if (frameData.size == 0) {
                        aapsLogger.warn(LTag.PUMPCOMM, "Frame has zero length, retrying")
                    }
                    failures++
                    if (failures == 6) {
                        aapsLogger.error(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "getPumpHistory: 6 failures in attempting to download frame %d of page %d, giving up.",
                            expectedFrameNum, pageNumber))
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
                        } catch (e: RileyLinkCommunicationException) {
                            aapsLogger.error(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Problem acknowledging frame response. (retry=%d)", retries))
                        }
                    }
                    if (nextMsg != null) currentResponse = GetHistoryPageCarelinkMessageBody(nextMsg.messageBody!!.txData) else {
                        aapsLogger.error(LTag.PUMPCOMM, "We couldn't acknowledge frame from pump, aborting operation.")
                    }
                }
            }
            if (rawHistoryPage.length != 1024) {
                aapsLogger.warn(LTag.PUMPCOMM, "getPumpHistory: short page.  Expected length of 1024, found length of "
                    + rawHistoryPage.length)
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
            pumpTotalResult.addHistoryEntries(medtronicHistoryEntries, pageNumber)
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
            RLMessageType.PowerOn        -> medtronicUtil.buildCommandPayload(rileyLinkServiceData, MedtronicCommandType.RFPowerOn, byteArrayOf(2, 1, receiverDeviceAwakeForMinutes.toByte())) // maybe this is better FIXME
            RLMessageType.ReadSimpleData -> medtronicUtil.buildCommandPayload(rileyLinkServiceData, MedtronicCommandType.PumpModel, null)
        }
        return ByteArray(0)
    }

    private fun makePumpMessage(messageType: MedtronicCommandType, body: ByteArray? = null as ByteArray?): PumpMessage {
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
        val msg: PumpMessage
        msg = bodyData?.let { makePumpMessage(commandType, it) } ?: makePumpMessage(commandType)

        // send and wait for response
        val response = sendAndListen(msg, timeoutMs)
        medtronicPumpStatus.pumpDeviceState = PumpDeviceState.Sleeping
        return response
    }

    @Throws(RileyLinkCommunicationException::class)
    private fun sendAndListen(msg: PumpMessage): PumpMessage {
        return sendAndListen(msg, 4000) // 2000
    }

    // All pump communications go through this function.
    @Throws(RileyLinkCommunicationException::class)
    protected /*override*/ fun sendAndListen(msg: PumpMessage, timeout_ms: Int): PumpMessage {
        return super.sendAndListen(msg, timeout_ms)!!
    }

    private fun sendAndGetResponseWithCheck(commandType: MedtronicCommandType, bodyData: ByteArray? = null): Any? {
        aapsLogger.debug(LTag.PUMPCOMM, "getDataFromPump: $commandType")
        for (retries in 0 until MAX_COMMAND_TRIES) {
            try {
                val response = sendAndGetResponse(commandType, bodyData, DEFAULT_TIMEOUT + DEFAULT_TIMEOUT * retries)
                val check = checkResponseContent(response, commandType.commandDescription, commandType.expectedLength)
                if (check == null) {
                    val dataResponse = medtronicConverter.convertResponse(medtronicPumpStatus.pumpType, commandType, response.rawContent)
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

    // private fun <T> sendAndGetResponseWithCheck(commandType: MedtronicCommandType, bodyData: ByteArray, clazz: Class<T>): T? {
    //     aapsLogger.debug(LTag.PUMPCOMM, "getDataFromPump: $commandType")
    //     for (retries in 0 until MAX_COMMAND_TRIES) {
    //         try {
    //             val response = sendAndGetResponse(commandType, bodyData, DEFAULT_TIMEOUT + DEFAULT_TIMEOUT * retries)
    //             val check = checkResponseContent(response, commandType.commandDescription, commandType.expectedLength)
    //             if (check == null) {
    //                 val dataResponse = medtronicConverter!!.convertResponse(medtronicPumpPlugin!!.pumpDescription.pumpType, commandType, response.rawContent) as T?
    //                 if (dataResponse != null) {
    //                     errorResponse = null
    //                     aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Converted response for %s is %s.", commandType.name, dataResponse))
    //                     return dataResponse
    //                 } else {
    //                     errorResponse = "Error decoding response."
    //                 }
    //             } else {
    //                 errorResponse = check
    //                 // return null;
    //             }
    //         } catch (e: RileyLinkCommunicationException) {
    //             aapsLogger.warn(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Error getting response from RileyLink (error=%s, retry=%d)", e.message, retries + 1))
    //         }
    //     }
    //     return null
    // }

    private fun checkResponseContent(response: PumpMessage, method: String, expectedLength: Int): String? {
        if (!response.isValid) {
            val responseData = String.format("%s: Invalid response.", method)
            aapsLogger.warn(LTag.PUMPCOMM, responseData)
            return responseData
        }
        val contents = response.rawContent
        return if (contents != null) {
            if (contents.size >= expectedLength) {
                aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "%s: Content: %s", method, ByteUtil.shortHexString(contents)))
                null
            } else {
                val responseData = String.format(
                    "%s: Cannot return data. Data is too short [expected=%s, received=%s].", method, ""
                    + expectedLength, "" + contents.size)
                aapsLogger.warn(LTag.PUMPCOMM, responseData)
                responseData
            }
        } else {
            val responseData = String.format("%s: Cannot return data. Null response.", method)
            aapsLogger.warn(LTag.PUMPCOMM, responseData)
            responseData
        }
    }

    // PUMP SPECIFIC COMMANDS
    fun getRemainingInsulin(): Double? {
        val responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.GetRemainingInsulin)
        return if (responseObject == null) null else responseObject as Double?
    }

    fun getPumpModel(): MedtronicDeviceType? {
        val responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.PumpModel)
        return if (responseObject == null) null else responseObject as MedtronicDeviceType?
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
                var msg: PumpMessage
                msg = makePumpMessage(commandType)

                // send and wait for response
                var response = sendAndListen(msg, DEFAULT_TIMEOUT + DEFAULT_TIMEOUT * retries)

//                aapsLogger.debug(LTag.PUMPCOMM,"1st Response: " + HexDump.toHexStringDisplayable(response.getRawContent()));
//                aapsLogger.debug(LTag.PUMPCOMM,"1st Response: " + HexDump.toHexStringDisplayable(response.getMessageBody().getTxData()));
                val check = checkResponseContent(response, commandType.commandDescription, 1)
                var data: ByteArray? = null
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
                val basalProfile = medtronicConverter.convertResponse(medtronicPumpPlugin.pumpType, commandType, data) as BasalProfile?
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

    private fun checkIfWeHaveMoreData(commandType: MedtronicCommandType, response: PumpMessage, data: ByteArray?): Boolean {
        if (commandType === MedtronicCommandType.GetBasalProfileSTD || //
            commandType === MedtronicCommandType.GetBasalProfileA || //
            commandType === MedtronicCommandType.GetBasalProfileB) {
            val responseRaw = response.rawContentOfFrame
            val last = responseRaw.size - 1
            aapsLogger.debug(LTag.PUMPCOMM, "Length: " + data!!.size)
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
        val clockDTO = ClockDTO()
        clockDTO.localDeviceTime = LocalDateTime()
        val responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.GetRealTimeClock)
        if (responseObject != null) {
            clockDTO.pumpTime = responseObject as LocalDateTime?
            return clockDTO
        }
        return null
    }

    fun getTemporaryBasal(): TempBasalPair? {
        val responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.ReadTemporaryBasal)
        return if (responseObject == null) null else responseObject as TempBasalPair?
    }

    fun getPumpSettings(): Map<String, PumpSettingDTO>? {
        val responseObject = sendAndGetResponseWithCheck(getSettings(medtronicUtil.medtronicPumpModel))
        return if (responseObject == null) null else responseObject as Map<String, PumpSettingDTO>?
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
        val i = 1
        val data = ByteArray(8)
        data[0] = 7
        data[i] = gc[Calendar.HOUR_OF_DAY].toByte()
        data[i + 1] = gc[Calendar.MINUTE].toByte()
        data[i + 2] = gc[Calendar.SECOND].toByte()
        val yearByte = getByteArrayFromUnsignedShort(gc[Calendar.YEAR], true)
        data[i + 3] = yearByte[0]
        data[i + 4] = yearByte[1]
        data[i + 5] = (gc[Calendar.MONTH] + 1).toByte()
        data[i + 6] = gc[Calendar.DAY_OF_MONTH].toByte()

        //aapsLogger.info(LTag.PUMPCOMM,"setPumpTime: Body:  " + ByteUtil.getHex(data));
        return setCommand(MedtronicCommandType.SetRealTimeClock, data)
    }

    private fun setCommand(commandType: MedtronicCommandType, body: ByteArray): Boolean {
        for (retries in 0..MAX_COMMAND_TRIES) {
            try {
                if (doWakeUpBeforeCommand) wakeUp(false)
                if (debugSetCommands) aapsLogger.debug(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "%s: Body - %s", commandType.commandDescription,
                    ByteUtil.getHex(body)))
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
        val responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.GetBatteryStatus)
        return if (responseObject == null) null else responseObject as BatteryStatusDTO?
    }

    fun setBasalProfile(basalProfile: BasalProfile): Boolean {
        val basalProfileFrames = medtronicUtil.getBasalProfileFrames(basalProfile.rawData)
        for (retries in 0..MAX_COMMAND_TRIES) {
            var responseMessage: PumpMessage? = null
            try {
                responseMessage = runCommandWithFrames(MedtronicCommandType.SetBasalProfileSTD,
                    basalProfileFrames)
                if (responseMessage!!.commandType === MedtronicCommandType.CommandACK) return true
            } catch (e: RileyLinkCommunicationException) {
                aapsLogger.warn(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Error getting response from RileyLink (error=%s, retry=%d)", e.message, retries + 1))
            }
            if (responseMessage != null) aapsLogger.warn(LTag.PUMPCOMM, String.format(Locale.ENGLISH, "Set Basal Profile: Invalid response: commandType=%s,rawData=%s", responseMessage.commandType, ByteUtil.shortHexString(responseMessage.rawContent))) else aapsLogger.warn(LTag.PUMPCOMM, "Set Basal Profile: Null response.")
        }
        return false
    }
}
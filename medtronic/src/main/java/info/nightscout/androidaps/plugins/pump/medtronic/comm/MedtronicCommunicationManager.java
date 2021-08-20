package info.nightscout.androidaps.plugins.pump.medtronic.comm;

import android.os.SystemClock;

import org.joda.time.LocalDateTime;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RFSpyResponse;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioResponse;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.RawHistoryPage;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.MedtronicPumpHistoryDecoder;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryResult;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.CarelinkLongMessageBody;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.CarelinkShortMessageBody;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.GetHistoryPageCarelinkMessageBody;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.MessageBody;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.PacketType;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.PumpAckMessageBody;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.message.PumpMessage;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BatteryStatusDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.PumpSettingDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicDeviceType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Original file created by geoff on 5/30/16.
 * <p>
 * Split into 2 implementations, so that we can split it by target device. - Andy
 * This was mostly rewritten from Original version, and lots of commands and
 * functionality added.
 */
@Singleton
public class MedtronicCommunicationManager extends RileyLinkCommunicationManager<PumpMessage> {

    @Inject MedtronicPumpStatus medtronicPumpStatus;
    @Inject MedtronicPumpPlugin medtronicPumpPlugin;
    @Inject MedtronicConverter medtronicConverter;
    @Inject MedtronicUtil medtronicUtil;
    @Inject MedtronicPumpHistoryDecoder medtronicPumpHistoryDecoder;

    private final int MAX_COMMAND_TRIES = 3;
    private final int DEFAULT_TIMEOUT = 2000;
    private final long RILEYLINK_TIMEOUT = 15 * 60 * 1000; // 15 min

    private String errorMessage;
    private final boolean debugSetCommands = false;

    private boolean doWakeUpBeforeCommand = true;

    // This empty constructor must be kept, otherwise dagger injection might break!
    @Inject
    public MedtronicCommunicationManager() {
    }

    @Inject
    public void onInit() {
        // we can't do this in the constructor, as sp only gets injected after the constructor has returned
        medtronicPumpStatus.previousConnection = sp.getLong(
                RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
    }

    @Override
    public PumpMessage createResponseMessage(byte[] payload) {
        return new PumpMessage(aapsLogger, payload);
    }

    @Override
    public void setPumpDeviceState(PumpDeviceState pumpDeviceState) {
        this.medtronicPumpStatus.setPumpDeviceState(pumpDeviceState);
    }

    public void setDoWakeUpBeforeCommand(boolean doWakeUp) {
        this.doWakeUpBeforeCommand = doWakeUp;
    }


    @Override
    public boolean isDeviceReachable() {
        return isDeviceReachable(false);
    }


    /**
     * We do actual wakeUp and compare PumpModel with currently selected one. If returned model is
     * not Unknown, pump is reachable.
     *
     * @return
     */
    public boolean isDeviceReachable(boolean canPreventTuneUp) {

        PumpDeviceState state = medtronicPumpStatus.getPumpDeviceState();

        if (state != PumpDeviceState.PumpUnreachable)
            medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.WakingUp);

        for (int retry = 0; retry < 5; retry++) {

            aapsLogger.debug(LTag.PUMPCOMM, "isDeviceReachable. Waking pump... " + (retry != 0 ? " (retry " + retry + ")" : ""));

            boolean connected = connectToDevice();

            if (connected)
                return true;

            SystemClock.sleep(1000);

        }

        if (state != PumpDeviceState.PumpUnreachable)
            medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.PumpUnreachable);

        if (!canPreventTuneUp) {

            long diff = System.currentTimeMillis() - medtronicPumpStatus.lastConnection;

            if (diff > RILEYLINK_TIMEOUT) {
                serviceTaskExecutor.startTask(new WakeAndTuneTask(injector));
            }
        }

        return false;
    }


    private boolean connectToDevice() {

        PumpDeviceState state = medtronicPumpStatus.getPumpDeviceState();

        // check connection

        byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData); // simple
        RFSpyResponse rfSpyResponse = rfspy.transmitThenReceive(new RadioPacket(injector, pumpMsgContent), (byte) 0, (byte) 200,
                (byte) 0, (byte) 0, 25000, (byte) 0);
        aapsLogger.info(LTag.PUMPCOMM, "wakeup: raw response is " + ByteUtil.shortHexString(rfSpyResponse.getRaw()));

        if (rfSpyResponse.wasTimeout()) {
            aapsLogger.error(LTag.PUMPCOMM, "isDeviceReachable. Failed to find pump (timeout).");
        } else if (rfSpyResponse.looksLikeRadioPacket()) {
            RadioResponse radioResponse = new RadioResponse(injector);

            try {

                radioResponse.init(rfSpyResponse.getRaw());

                if (radioResponse.isValid()) {

                    PumpMessage pumpResponse = createResponseMessage(radioResponse.getPayload());

                    if (!pumpResponse.isValid()) {
                        aapsLogger.warn(LTag.PUMPCOMM, "Response is invalid ! [interrupted={}, timeout={}]", rfSpyResponse.wasInterrupted(),
                                rfSpyResponse.wasTimeout());
                    } else {

                        // radioResponse.rssi;
                        Object dataResponse = medtronicConverter.convertResponse(medtronicPumpPlugin.getPumpDescription().pumpType, MedtronicCommandType.PumpModel,
                                pumpResponse.getRawContent());

                        MedtronicDeviceType pumpModel = (MedtronicDeviceType) dataResponse;
                        boolean valid = (pumpModel != MedtronicDeviceType.Unknown_Device);

                        if (medtronicUtil.getMedtronicPumpModel() == null && valid) {
                            medtronicUtil.setMedtronicPumpModel(pumpModel);
                        }

                        aapsLogger.debug(LTag.PUMPCOMM, "isDeviceReachable. PumpModel is {} - Valid: {} (rssi={})", pumpModel.name(), valid,
                                radioResponse.rssi);

                        if (valid) {
                            if (state == PumpDeviceState.PumpUnreachable)
                                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.WakingUp);
                            else
                                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);

                            rememberLastGoodDeviceCommunicationTime();

                            return true;

                        } else {
                            if (state != PumpDeviceState.PumpUnreachable)
                                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.PumpUnreachable);
                        }

                    }

                } else {
                    aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Failed to parse radio response: "
                            + ByteUtil.shortHexString(rfSpyResponse.getRaw()));
                }

            } catch (RileyLinkCommunicationException e) {
                aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Failed to decode radio response: "
                        + ByteUtil.shortHexString(rfSpyResponse.getRaw()));
            }

        } else {
            aapsLogger.warn(LTag.PUMPCOMM, "isDeviceReachable. Unknown response: " + ByteUtil.shortHexString(rfSpyResponse.getRaw()));
        }

        return false;
    }


    @Override
    public boolean tryToConnectToDevice() {
        return isDeviceReachable(true);
    }


    private PumpMessage runCommandWithArgs(PumpMessage msg) throws RileyLinkCommunicationException {

        if (debugSetCommands)
            aapsLogger.debug(LTag.PUMPCOMM, "Run command with Args: ");

        PumpMessage rval;
        PumpMessage shortMessage = makePumpMessage(msg.commandType, new CarelinkShortMessageBody(new byte[]{0}));
        // look for ack from short message
        PumpMessage shortResponse = sendAndListen(shortMessage);
        if (shortResponse.commandType == MedtronicCommandType.CommandACK) {
            if (debugSetCommands)
                aapsLogger.debug(LTag.PUMPCOMM, "Run command with Args: Got ACK response");

            rval = sendAndListen(msg);
            if (debugSetCommands)
                aapsLogger.debug(LTag.PUMPCOMM, "2nd Response: {}", rval);

            return rval;
        } else {
            aapsLogger.error(LTag.PUMPCOMM, "runCommandWithArgs: Pump did not ack Attention packet");
            return new PumpMessage(aapsLogger, "No ACK after Attention packet.");
        }
    }


    private PumpMessage runCommandWithFrames(MedtronicCommandType commandType, List<List<Byte>> frames)
            throws RileyLinkCommunicationException {

        aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: {}", commandType.name());

        PumpMessage rval = null;
        PumpMessage shortMessage = makePumpMessage(commandType, new CarelinkShortMessageBody(new byte[]{0}));
        // look for ack from short message
        PumpMessage shortResponse = sendAndListen(shortMessage);

        if (shortResponse.commandType != MedtronicCommandType.CommandACK) {
            aapsLogger.error(LTag.PUMPCOMM, "runCommandWithFrames: Pump did not ack Attention packet");

            return new PumpMessage(aapsLogger, "No ACK after start message.");
        } else {
            aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: Got ACK response for Attention packet");
        }

        int frameNr = 1;

        for (List<Byte> frame : frames) {

            byte[] frameData = MedtronicUtil.createByteArray(frame);

            // aapsLogger.debug(LTag.PUMPCOMM,"Frame {} data:\n{}", frameNr, ByteUtil.getCompactString(frameData));

            PumpMessage msg = makePumpMessage(commandType, new CarelinkLongMessageBody(frameData));

            rval = sendAndListen(msg);

            // aapsLogger.debug(LTag.PUMPCOMM,"PumpResponse: " + rval);

            if (rval.commandType != MedtronicCommandType.CommandACK) {
                aapsLogger.error(LTag.PUMPCOMM, "runCommandWithFrames: Pump did not ACK frame #{}", frameNr);

                aapsLogger.error(LTag.PUMPCOMM, "Run command with Frames FAILED (command={}, response={})", commandType.name(),
                        rval.toString());

                return new PumpMessage(aapsLogger, "No ACK after frame #" + frameNr);
            } else {
                aapsLogger.debug(LTag.PUMPCOMM, "Run command with Frames: Got ACK response for frame #{}", (frameNr));
            }

            frameNr++;
        }

        return rval;

    }


    public PumpHistoryResult getPumpHistory(PumpHistoryEntry lastEntry, LocalDateTime targetDate) {

        PumpHistoryResult pumpTotalResult = new PumpHistoryResult(aapsLogger, lastEntry, targetDate == null ? null
                : DateTimeUtil.toATechDate(targetDate));

        if (doWakeUpBeforeCommand)
            wakeUp(receiverDeviceAwakeForMinutes, false);

        aapsLogger.debug(LTag.PUMPCOMM, "Current command: " + medtronicUtil.getCurrentCommand());

        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Active);
        boolean doneWithError = false;

        for (int pageNumber = 0; pageNumber < 5; pageNumber++) {

            RawHistoryPage rawHistoryPage = new RawHistoryPage(aapsLogger);
            // wakeUp(receiverDeviceAwakeForMinutes, false);
            PumpMessage getHistoryMsg = makePumpMessage(MedtronicCommandType.GetHistoryData,
                    new GetHistoryPageCarelinkMessageBody(pageNumber));

            aapsLogger.info(LTag.PUMPCOMM, "getPumpHistory: Page {}", pageNumber);
            // aapsLogger.info(LTag.PUMPCOMM,"getPumpHistoryPage("+pageNumber+"): "+ByteUtil.shortHexString(getHistoryMsg.getTxData()));
            // Ask the pump to transfer history (we get first frame?)

            PumpMessage firstResponse = null;
            boolean failed = false;

            medtronicUtil.setCurrentCommand(MedtronicCommandType.GetHistoryData, pageNumber, null);

            for (int retries = 0; retries < MAX_COMMAND_TRIES; retries++) {

                try {
                    firstResponse = runCommandWithArgs(getHistoryMsg);
                    failed = false;
                    break;
                } catch (RileyLinkCommunicationException e) {
                    aapsLogger.error(LTag.PUMPCOMM, "First call for PumpHistory failed (retry={})", retries);
                    failed = true;
                }
            }

            if (failed) {
                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
                return pumpTotalResult;
            }

            // aapsLogger.info(LTag.PUMPCOMM,"getPumpHistoryPage("+pageNumber+"): " + ByteUtil.shortHexString(firstResponse.getContents()));

            PumpMessage ackMsg = makePumpMessage(MedtronicCommandType.CommandACK, new PumpAckMessageBody());
            GetHistoryPageCarelinkMessageBody currentResponse = new GetHistoryPageCarelinkMessageBody(firstResponse
                    .getMessageBody().getTxData());
            int expectedFrameNum = 1;
            boolean done = false;
            // while (expectedFrameNum == currentResponse.getFrameNumber()) {

            int failures = 0;
            while (!done) {
                // examine current response for problems.
                byte[] frameData = currentResponse.getFrameData();
                if ((frameData != null) && (frameData.length > 0)
                        && currentResponse.getFrameNumber() == expectedFrameNum) {
                    // success! got a frame.
                    if (frameData.length != 64) {
                        aapsLogger.warn(LTag.PUMPCOMM, "Expected frame of length 64, got frame of length " + frameData.length);
                        // but append it anyway?
                    }
                    // handle successful frame data
                    rawHistoryPage.appendData(currentResponse.getFrameData());
                    // RileyLinkMedtronicService.getInstance().announceProgress(((100 / 16) *
                    // currentResponse.getFrameNumber() + 1));
                    medtronicUtil.setCurrentCommand(MedtronicCommandType.GetHistoryData, pageNumber,
                            currentResponse.getFrameNumber());

                    aapsLogger.info(LTag.PUMPCOMM, "getPumpHistory: Got frame {} of Page {}", currentResponse.getFrameNumber(), pageNumber);
                    // Do we need to ask for the next frame?
                    if (expectedFrameNum < 16) { // This number may not be correct for pumps other than 522/722
                        expectedFrameNum++;
                    } else {
                        done = true; // successful completion
                    }
                } else {
                    if (frameData == null) {
                        aapsLogger.error(LTag.PUMPCOMM, "null frame data, retrying");
                    } else if (currentResponse.getFrameNumber() != expectedFrameNum) {
                        aapsLogger.warn(LTag.PUMPCOMM, "Expected frame number {}, received {} (retrying)", expectedFrameNum,
                                currentResponse.getFrameNumber());
                    } else if (frameData.length == 0) {
                        aapsLogger.warn(LTag.PUMPCOMM, "Frame has zero length, retrying");
                    }
                    failures++;
                    if (failures == 6) {
                        aapsLogger.error(LTag.PUMPCOMM,
                                "getPumpHistory: 6 failures in attempting to download frame {} of page {}, giving up.",
                                expectedFrameNum, pageNumber);
                        done = true; // failure completion.
                        doneWithError = true;
                    }
                }

                if (!done) {
                    // ask for next frame
                    PumpMessage nextMsg = null;

                    for (int retries = 0; retries < MAX_COMMAND_TRIES; retries++) {

                        try {
                            nextMsg = sendAndListen(ackMsg);
                            break;
                        } catch (RileyLinkCommunicationException e) {
                            aapsLogger.error(LTag.PUMPCOMM, "Problem acknowledging frame response. (retry={})", retries);
                        }
                    }

                    if (nextMsg != null)
                        currentResponse = new GetHistoryPageCarelinkMessageBody(nextMsg.getMessageBody().getTxData());
                    else {
                        aapsLogger.error(LTag.PUMPCOMM, "We couldn't acknowledge frame from pump, aborting operation.");
                    }
                }
            }

            if (rawHistoryPage.getLength() != 1024) {
                aapsLogger.warn(LTag.PUMPCOMM, "getPumpHistory: short page.  Expected length of 1024, found length of "
                        + rawHistoryPage.getLength());
                doneWithError = true;
            }

            if (!rawHistoryPage.isChecksumOK()) {
                aapsLogger.error(LTag.PUMPCOMM, "getPumpHistory: checksum is wrong");
                doneWithError = true;
            }

            if (doneWithError) {
                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);
                return pumpTotalResult;
            }

            rawHistoryPage.dumpToDebug();

            List<PumpHistoryEntry> medtronicHistoryEntries = medtronicPumpHistoryDecoder.processPageAndCreateRecords(rawHistoryPage);

            aapsLogger.debug(LTag.PUMPCOMM, "getPumpHistory: Found {} history entries.", medtronicHistoryEntries.size());

            pumpTotalResult.addHistoryEntries(medtronicHistoryEntries, pageNumber);

            aapsLogger.debug(LTag.PUMPCOMM, "getPumpHistory: Search status: Search finished: {}", pumpTotalResult.isSearchFinished());

            if (pumpTotalResult.isSearchFinished()) {
                medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);

                return pumpTotalResult;
            }
        }

        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);

        return pumpTotalResult;

    }


    public String getErrorResponse() {
        return this.errorMessage;
    }


    @Override
    public byte[] createPumpMessageContent(RLMessageType type) {
        switch (type) {
            case PowerOn:
                return medtronicUtil.buildCommandPayload(rileyLinkServiceData, MedtronicCommandType.RFPowerOn, //
                        new byte[]{2, 1, (byte) receiverDeviceAwakeForMinutes}); // maybe this is better FIXME

            case ReadSimpleData:
                return medtronicUtil.buildCommandPayload(rileyLinkServiceData, MedtronicCommandType.PumpModel, null);
        }
        return new byte[0];
    }


    private PumpMessage makePumpMessage(MedtronicCommandType messageType, byte[] body) {
        return makePumpMessage(messageType, body == null ? new CarelinkShortMessageBody()
                : new CarelinkShortMessageBody(body));
    }


    private PumpMessage makePumpMessage(MedtronicCommandType messageType) {
        return makePumpMessage(messageType, (byte[]) null);
    }


    private PumpMessage makePumpMessage(MedtronicCommandType messageType, MessageBody messageBody) {
        PumpMessage msg = new PumpMessage(aapsLogger);
        msg.init(PacketType.Carelink, rileyLinkServiceData.pumpIDBytes, messageType, messageBody);
        return msg;
    }


    private PumpMessage sendAndGetResponse(MedtronicCommandType commandType) throws RileyLinkCommunicationException {

        return sendAndGetResponse(commandType, null, DEFAULT_TIMEOUT);
    }


    /**
     * Main wrapper method for sending data - (for getting responses)
     *
     * @param commandType
     * @param bodyData
     * @param timeoutMs
     * @return
     */
    private PumpMessage sendAndGetResponse(MedtronicCommandType commandType, byte[] bodyData, int timeoutMs)
            throws RileyLinkCommunicationException {
        // wakeUp
        if (doWakeUpBeforeCommand)
            wakeUp(receiverDeviceAwakeForMinutes, false);

        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Active);

        // create message
        PumpMessage msg;

        if (bodyData == null)
            msg = makePumpMessage(commandType);
        else
            msg = makePumpMessage(commandType, bodyData);

        // send and wait for response
        PumpMessage response = sendAndListen(msg, timeoutMs);

        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);

        return response;
    }


    private PumpMessage sendAndListen(PumpMessage msg) throws RileyLinkCommunicationException {
        return sendAndListen(msg, 4000); // 2000
    }


    // All pump communications go through this function.
    @Override
    protected PumpMessage sendAndListen(PumpMessage msg, int timeout_ms) throws RileyLinkCommunicationException {
        return super.sendAndListen(msg, timeout_ms);
    }


    private Object sendAndGetResponseWithCheck(MedtronicCommandType commandType) {

        return sendAndGetResponseWithCheck(commandType, null);
    }


    private Object sendAndGetResponseWithCheck(MedtronicCommandType commandType, byte[] bodyData) {

        aapsLogger.debug(LTag.PUMPCOMM, "getDataFromPump: {}", commandType);

        for (int retries = 0; retries < MAX_COMMAND_TRIES; retries++) {

            try {
                PumpMessage response = sendAndGetResponse(commandType, bodyData, DEFAULT_TIMEOUT + (DEFAULT_TIMEOUT * retries));

                String check = checkResponseContent(response, commandType.commandDescription, commandType.expectedLength);

                if (check == null) {

                    Object dataResponse = medtronicConverter.convertResponse(medtronicPumpPlugin.getPumpDescription().pumpType, commandType, response.getRawContent());

                    if (dataResponse != null) {
                        this.errorMessage = null;
                        aapsLogger.debug(LTag.PUMPCOMM, "Converted response for {} is {}.", commandType.name(), dataResponse);

                        return dataResponse;
                    } else {
                        this.errorMessage = "Error decoding response.";
                    }
                } else {
                    this.errorMessage = check;
                    // return null;
                }

            } catch (RileyLinkCommunicationException e) {
                aapsLogger.warn(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
            }

        }

        return null;
    }


    private String checkResponseContent(PumpMessage response, String method, int expectedLength) {

        if (!response.isValid()) {
            String responseData = String.format("%s: Invalid response.", method);
            aapsLogger.warn(LTag.PUMPCOMM, responseData);
            return responseData;
        }

        byte[] contents = response.getRawContent();

        if (contents != null) {
            if (contents.length >= expectedLength) {
                aapsLogger.debug(LTag.PUMPCOMM, "{}: Content: {}", method, ByteUtil.shortHexString(contents));
                return null;

            } else {
                String responseData = String.format(
                        "%s: Cannot return data. Data is too short [expected=%s, received=%s].", method, ""
                                + expectedLength, "" + contents.length);

                aapsLogger.warn(LTag.PUMPCOMM, responseData);
                return responseData;
            }
        } else {
            String responseData = String.format("%s: Cannot return data. Null response.", method);
            aapsLogger.warn(LTag.PUMPCOMM, responseData);
            return responseData;
        }
    }


    // PUMP SPECIFIC COMMANDS

    public Float getRemainingInsulin() {

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.GetRemainingInsulin);

        return responseObject == null ? null : (Float) responseObject;
    }


    public MedtronicDeviceType getPumpModel() {

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.PumpModel);

        return responseObject == null ? null : (MedtronicDeviceType) responseObject;
    }


    public BasalProfile getBasalProfile() {

        // wakeUp
        if (doWakeUpBeforeCommand)
            wakeUp(receiverDeviceAwakeForMinutes, false);

        MedtronicCommandType commandType = MedtronicCommandType.GetBasalProfileSTD;

        aapsLogger.debug(LTag.PUMPCOMM, "getDataFromPump: {}", commandType);

        medtronicUtil.setCurrentCommand(commandType);

        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Active);

        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {

            try {
                // create message
                PumpMessage msg;

                msg = makePumpMessage(commandType);

                // send and wait for response

                PumpMessage response = sendAndListen(msg, DEFAULT_TIMEOUT + (DEFAULT_TIMEOUT * retries));

//                aapsLogger.debug(LTag.PUMPCOMM,"1st Response: " + HexDump.toHexStringDisplayable(response.getRawContent()));
//                aapsLogger.debug(LTag.PUMPCOMM,"1st Response: " + HexDump.toHexStringDisplayable(response.getMessageBody().getTxData()));

                String check = checkResponseContent(response, commandType.commandDescription, 1);

                byte[] data = null;

                if (check == null) {

                    data = response.getRawContentOfFrame();

                    PumpMessage ackMsg = makePumpMessage(MedtronicCommandType.CommandACK, new PumpAckMessageBody());

                    while (checkIfWeHaveMoreData(commandType, response, data)) {

                        response = sendAndListen(ackMsg, DEFAULT_TIMEOUT + (DEFAULT_TIMEOUT * retries));

//                        aapsLogger.debug(LTag.PUMPCOMM,"{} Response: {}", runs, HexDump.toHexStringDisplayable(response2.getRawContent()));
//                        aapsLogger.debug(LTag.PUMPCOMM,"{} Response: {}", runs,
//                            HexDump.toHexStringDisplayable(response2.getMessageBody().getTxData()));

                        String check2 = checkResponseContent(response, commandType.commandDescription, 1);

                        if (check2 == null) {

                            data = ByteUtil.concat(data, response.getRawContentOfFrame());

                        } else {
                            this.errorMessage = check2;
                            aapsLogger.error(LTag.PUMPCOMM, "Error with response got GetProfile: " + check2);
                        }
                    }

                } else {
                    errorMessage = check;
                }

                BasalProfile basalProfile = (BasalProfile) medtronicConverter.convertResponse(medtronicPumpPlugin.getPumpDescription().pumpType, commandType, data);

                if (basalProfile != null) {
                    aapsLogger.debug(LTag.PUMPCOMM, "Converted response for {} is {}.", commandType.name(), basalProfile);

                    medtronicUtil.setCurrentCommand(null);
                    medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);

                    return basalProfile;
                }

            } catch (RileyLinkCommunicationException e) {
                aapsLogger.error(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
            }
        }

        aapsLogger.warn(LTag.PUMPCOMM, "Error reading profile in max retries.");
        medtronicUtil.setCurrentCommand(null);
        medtronicPumpStatus.setPumpDeviceState(PumpDeviceState.Sleeping);

        return null;

    }


    private boolean checkIfWeHaveMoreData(MedtronicCommandType commandType, PumpMessage response, byte[] data) {

        if (commandType == MedtronicCommandType.GetBasalProfileSTD || //
                commandType == MedtronicCommandType.GetBasalProfileA || //
                commandType == MedtronicCommandType.GetBasalProfileB) {
            byte[] responseRaw = response.getRawContentOfFrame();

            int last = responseRaw.length - 1;

            aapsLogger.debug(LTag.PUMPCOMM, "Length: " + data.length);

            if (data.length >= BasalProfile.MAX_RAW_DATA_SIZE) {
                return false;
            }

            if (responseRaw.length < 2) {
                return false;
            }

            return !(responseRaw[last] == 0x00 && responseRaw[last - 1] == 0x00 && responseRaw[last - 2] == 0x00);
        }

        return false;
    }


    public ClockDTO getPumpTime() {

        ClockDTO clockDTO = new ClockDTO();
        clockDTO.localDeviceTime = new LocalDateTime();

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.GetRealTimeClock);

        if (responseObject != null) {
            clockDTO.pumpTime = (LocalDateTime) responseObject;
            return clockDTO;
        }

        return null;
    }


    public TempBasalPair getTemporaryBasal() {

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.ReadTemporaryBasal);

        return responseObject == null ? null : (TempBasalPair) responseObject;
    }


    public Map<String, PumpSettingDTO> getPumpSettings() {

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.getSettings(medtronicUtil
                .getMedtronicPumpModel()));

        return responseObject == null ? null : (Map<String, PumpSettingDTO>) responseObject;
    }


    public Boolean setBolus(double units) {

        aapsLogger.info(LTag.PUMPCOMM, "setBolus: " + units);

        return setCommand(MedtronicCommandType.SetBolus, medtronicUtil.getBolusStrokes(units));

    }


    public boolean setTBR(TempBasalPair tbr) {

        aapsLogger.info(LTag.PUMPCOMM, "setTBR: " + tbr.getDescription());

        return setCommand(MedtronicCommandType.SetTemporaryBasal, tbr.getAsRawData());
    }


    public Boolean setPumpTime() {

        GregorianCalendar gc = new GregorianCalendar();
        gc.add(Calendar.SECOND, 5);

        aapsLogger.info(LTag.PUMPCOMM, "setPumpTime: " + DateTimeUtil.toString(gc));

        int i = 1;
        byte[] data = new byte[8];
        data[0] = 7;
        data[i] = (byte) gc.get(Calendar.HOUR_OF_DAY);
        data[i + 1] = (byte) gc.get(Calendar.MINUTE);
        data[i + 2] = (byte) gc.get(Calendar.SECOND);

        byte[] yearByte = MedtronicUtil.getByteArrayFromUnsignedShort(gc.get(Calendar.YEAR), true);

        data[i + 3] = yearByte[0];
        data[i + 4] = yearByte[1];

        data[i + 5] = (byte) (gc.get(Calendar.MONTH) + 1);
        data[i + 6] = (byte) gc.get(Calendar.DAY_OF_MONTH);

        //aapsLogger.info(LTag.PUMPCOMM,"setPumpTime: Body:  " + ByteUtil.getHex(data));

        return setCommand(MedtronicCommandType.SetRealTimeClock, data);

    }


    private boolean setCommand(MedtronicCommandType commandType, byte[] body) {

        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {

            try {
                if (this.doWakeUpBeforeCommand)
                    wakeUp(false);

                if (debugSetCommands)
                    aapsLogger.debug(LTag.PUMPCOMM, "{}: Body - {}", commandType.getCommandDescription(),
                            ByteUtil.getHex(body));

                PumpMessage msg = makePumpMessage(commandType, new CarelinkLongMessageBody(body));

                PumpMessage pumpMessage = runCommandWithArgs(msg);

                if (debugSetCommands)
                    aapsLogger.debug(LTag.PUMPCOMM, "{}: {}", commandType.getCommandDescription(), pumpMessage.getResponseContent());

                if (pumpMessage.commandType == MedtronicCommandType.CommandACK) {
                    return true;
                } else {
                    aapsLogger.warn(LTag.PUMPCOMM, "We received non-ACK response from pump: {}", pumpMessage.getResponseContent());
                }

            } catch (RileyLinkCommunicationException e) {
                aapsLogger.warn(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
            }
        }

        return false;
    }


    public boolean cancelTBR() {
        return setTBR(new TempBasalPair(0.0d, false, 0));
    }


    public BatteryStatusDTO getRemainingBattery() {

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.GetBatteryStatus);

        return responseObject == null ? null : (BatteryStatusDTO) responseObject;
    }


    public Boolean setBasalProfile(BasalProfile basalProfile) {

        List<List<Byte>> basalProfileFrames = medtronicUtil.getBasalProfileFrames(basalProfile.getRawData());

        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {

            PumpMessage responseMessage = null;
            try {
                responseMessage = runCommandWithFrames(MedtronicCommandType.SetBasalProfileSTD,
                        basalProfileFrames);

                if (responseMessage.commandType == MedtronicCommandType.CommandACK)
                    return true;

            } catch (RileyLinkCommunicationException e) {
                aapsLogger.warn(LTag.PUMPCOMM, "Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
            }

            if (responseMessage != null)
                aapsLogger.warn(LTag.PUMPCOMM, "Set Basal Profile: Invalid response: commandType={},rawData={}", responseMessage.commandType, ByteUtil.shortHexString(responseMessage.getRawContent()));
            else
                aapsLogger.warn(LTag.PUMPCOMM, "Set Basal Profile: Null response.");
        }

        return false;

    }
}

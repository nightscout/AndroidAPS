package info.nightscout.androidaps.plugins.pump.medtronic.comm;

import java.util.List;
import java.util.Map;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.os.SystemClock;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RFSpyResponse;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RLMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioResponse;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.common.utils.HexDump;
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
import info.nightscout.androidaps.plugins.pump.medtronic.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.utils.SP;

/**
 * Original file created by geoff on 5/30/16.
 * <p>
 * Split into 2 implementations, so that we can split it by target device. - Andy
 */
public class MedtronicCommunicationManager extends RileyLinkCommunicationManager {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicCommunicationManager.class);
    private static final int MAX_COMMAND_TRIES = 3;
    private static final int DEFAULT_TIMEOUT = 2000;
    private static final long RILEYLINK_TIMEOUT = 15 * 60 * 1000; // 15 min

    static MedtronicCommunicationManager medtronicCommunicationManager;
    String errorMessage;
    private MedtronicConverter medtronicConverter;
    private boolean debugSetCommands = true;
    private MedtronicPumpHistoryDecoder pumpHistoryDecoder;
    private boolean doWakeUpBeforeCommand = true;
    private boolean firstConnection = true;
    private boolean medtronicHistoryTesting = false; // TODO remove when not needed


    public MedtronicCommunicationManager(Context context, RFSpy rfspy) {
        super(context, rfspy);
        medtronicCommunicationManager = this;
        this.medtronicConverter = new MedtronicConverter();
        this.pumpHistoryDecoder = new MedtronicPumpHistoryDecoder();
        MedtronicUtil.getPumpStatus().previousConnection = SP.getLong(
            RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);

    }


    public static MedtronicCommunicationManager getInstance() {
        return medtronicCommunicationManager;
    }


    @Override
    protected void configurePumpSpecificSettings() {
        pumpStatus = MedtronicUtil.getPumpStatus();
    }


    @Override
    public <E extends RLMessage> E createResponseMessage(byte[] payload, Class<E> clazz) {
        PumpMessage pumpMessage = new PumpMessage(payload);
        return (E)pumpMessage;
    }


    public void setDoWakeUpBeforeCommand(boolean doWakeUp) {
        this.doWakeUpBeforeCommand = doWakeUp;
    }


    public boolean isDeviceReachable() {
        return isDeviceReachable(false);
    }


    /**
     * We do actual wakeUp and compare PumpModel with currently selected one. If returned model is not Unknown,
     * pump is reachable.
     *
     * @return
     */
    public boolean isDeviceReachable(boolean canPreventTuneUp) {

        PumpDeviceState state = MedtronicUtil.getPumpDeviceState();

        if (state != PumpDeviceState.PumpUnreachable)
            MedtronicUtil.setPumpDeviceState(PumpDeviceState.WakingUp);

        for (int retry = 0; retry < 5; retry++) {

            LOG.debug("isDeviceReachable. Waking pump... " + (retry != 0 ? " (retry " + retry + ")" : ""));

            boolean connected = connectToDevice();

            if (connected)
                return true;

            SystemClock.sleep(1000);

        }

        if (state != PumpDeviceState.PumpUnreachable)
            MedtronicUtil.setPumpDeviceState(PumpDeviceState.PumpUnreachable);

        if (!canPreventTuneUp) {

            long diff = System.currentTimeMillis() - MedtronicUtil.getPumpStatus().lastConnection;

            if (diff > RILEYLINK_TIMEOUT) {
                ServiceTaskExecutor.startTask(new WakeAndTuneTask());
            }
        }

        return false;
    }


    private boolean connectToDevice() {

        PumpDeviceState state = MedtronicUtil.getPumpDeviceState();

        byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData); // simple
        RFSpyResponse rfSpyResponse = rfspy.transmitThenReceive(new RadioPacket(pumpMsgContent), (byte)0, (byte)200,
            (byte)0, (byte)0, 25000, (byte)0);
        LOG.info("wakeup: raw response is " + ByteUtil.shortHexString(rfSpyResponse.getRaw()));

        if (rfSpyResponse.wasTimeout()) {
            LOG.error("isDeviceReachable. Failed to find pump (timeout).");
        } else if (rfSpyResponse.looksLikeRadioPacket()) {
            RadioResponse radioResponse = new RadioResponse();

            try {

                radioResponse.init(rfSpyResponse.getRaw());

                if (radioResponse.isValid()) {

                    PumpMessage pumpResponse = createResponseMessage(radioResponse.getPayload(), PumpMessage.class);

                    if (!pumpResponse.isValid()) {
                        LOG.warn("Response is invalid ! [interrupted={}, timeout={}]", rfSpyResponse.wasInterrupted(),
                            rfSpyResponse.wasTimeout());
                    } else {

                        // radioResponse.rssi;
                        Object dataResponse = medtronicConverter.convertResponse(MedtronicCommandType.PumpModel,
                            pumpResponse.getRawContent());

                        MedtronicDeviceType pumpModel = (MedtronicDeviceType)dataResponse;
                        boolean valid = (pumpModel != MedtronicDeviceType.Unknown_Device);

                        if (MedtronicUtil.getMedtronicPumpModel() == null && valid) {
                            MedtronicUtil.setMedtronicPumpModel(pumpModel);
                        }

                        LOG.debug("isDeviceReachable. PumpModel is {} - Valid: {} (rssi={})", pumpModel.name(), valid,
                            radioResponse.rssi);

                        if (valid) {
                            if (state == PumpDeviceState.PumpUnreachable)
                                MedtronicUtil.setPumpDeviceState(PumpDeviceState.WakingUp);
                            else
                                MedtronicUtil.setPumpDeviceState(PumpDeviceState.Sleeping);

                            if (firstConnection)
                                checkFirstConnectionTime();

                            rememberLastGoodDeviceCommunicationTime();

                            return true;

                        } else {
                            if (state != PumpDeviceState.PumpUnreachable)
                                MedtronicUtil.setPumpDeviceState(PumpDeviceState.PumpUnreachable);
                        }

                    }

                } else {
                    LOG.warn("isDeviceReachable. Failed to parse radio response: "
                        + ByteUtil.shortHexString(rfSpyResponse.getRaw()));
                }

            } catch (RileyLinkCommunicationException e) {
                LOG.warn("isDeviceReachable. Failed to decode radio response: "
                    + ByteUtil.shortHexString(rfSpyResponse.getRaw()));
            }

        } else {
            LOG.warn("isDeviceReachable. Unknown response: " + ByteUtil.shortHexString(rfSpyResponse.getRaw()));
        }

        return false;
    }


    // FIXME must not call getPumpModel !!!!!!!!!!!!!
    @Override
    public boolean tryToConnectToDevice() {

        return isDeviceReachable(true);

        // wakeUp(true);
        //
        // MedtronicDeviceType pumpModel = getPumpModel();
        //
        // // Andy (4.6.2018): we do retry if no data returned. We might need to do that everywhere, but that might
        // require
        // // little bit of rewrite of RF Code.
        // if (pumpModel == MedtronicDeviceType.Unknown_Device) {
        //
        // SystemClock.sleep(1000);
        //
        // pumpModel = getPumpModel();
        // }
        //
        // boolean connected = (pumpModel != MedtronicDeviceType.Unknown_Device);
        //
        // if (connected) {
        // checkFirstConnectionTime();
        // rememberLastGoodDeviceCommunicationTime();
        // }
        //
        // return (pumpModel != MedtronicDeviceType.Unknown_Device);
    }


    private void setLastConnectionTime() {

        // FIXME rename
        this.pumpStatus.setLastCommunicationToNow();

        // FIXME set to SP

    }


    private void checkFirstConnectionTime() {
        // FIXME set to SP

        firstConnection = false;
    }


    // FIXME remove debugs - Andy
    private PumpMessage runCommandWithArgs(PumpMessage msg) throws RileyLinkCommunicationException {

        if (debugSetCommands)
            LOG.debug("Run command with Args: ");

        PumpMessage rval;
        PumpMessage shortMessage = makePumpMessage(msg.commandType, new CarelinkShortMessageBody(new byte[] { 0 }));
        // look for ack from short message
        PumpMessage shortResponse = sendAndListen(shortMessage);
        if (shortResponse.commandType == MedtronicCommandType.CommandACK) {
            if (debugSetCommands)
                LOG.debug("Run command with Args: Got ACK response");

            rval = sendAndListen(msg);
            if (debugSetCommands)
                LOG.debug("2nd Response: {}", rval);

            return rval;
        } else {
            LOG.error("runCommandWithArgs: Pump did not ack Attention packet");
            return new PumpMessage("No ACK after Attention packet.");
        }
    }


    // @Deprecated
    // private PumpMessage runCommandWithArgsLong(MedtronicCommandType commandType, byte[] content) {
    //
    // LOG.debug("Run command with Args (Long): {}", commandType.name());
    //
    // PumpMessage rval = null;
    // PumpMessage shortMessage = makePumpMessage(commandType, new CarelinkShortMessageBody(new byte[] { 0 }));
    // // look for ack from short message
    // PumpMessage shortResponse = sendAndListen(shortMessage);
    //
    // if (shortResponse.commandType != MedtronicCommandType.CommandACK) {
    // LOG.error("runCommandWithArgs: Pump did not ack Attention packet");
    //
    // return new PumpMessage("No ACK after start message.");
    // }
    //
    // int start = 0;
    // int frameNr = 1;
    // int len = 0;
    //
    // do {
    //
    // if (start == 0)
    // LOG.debug("Run command with Args(Long): Got ACK response for Attention packet");
    // else
    // LOG.debug("Run command with Args(Long): Got ACK response for frame #{}", (frameNr - 1));
    //
    // if (start + 64 > content.length) {
    // len = content.length - start;
    //
    // if (len == 0)
    // break;
    // } else {
    // len = 64;
    // }
    //
    // byte frame[] = new byte[65];
    //
    // frame[0] = (byte)frameNr;
    //
    // System.arraycopy(content, start, frame, 1, len);
    //
    // PumpMessage msg = makePumpMessage(commandType, new CarelinkLongMessageBody(frame));
    //
    // rval = sendAndListen(msg);
    //
    // if (rval.commandType != MedtronicCommandType.CommandACK) {
    // LOG.error("runCommandWithArgs(Long): Pump did not ACK frame #{}", frameNr);
    //
    // return new PumpMessage("No ACK after frame #" + frameNr);
    // }
    //
    // if (len != 64) {
    // LOG.debug("Run command with Args(Long): Got ACK response for frame #{}", (frameNr));
    // break;
    // }
    //
    // start += 64;
    // frameNr++;
    //
    // } while (true);
    //
    // return rval;
    //
    // // return new PumpMessage("No ACK");
    // }

    private PumpMessage runCommandWithFrames(MedtronicCommandType commandType, List<List<Byte>> frames)
            throws RileyLinkCommunicationException {

        LOG.debug("Run command with Frames: {}", commandType.name());

        PumpMessage rval = null;
        PumpMessage shortMessage = makePumpMessage(commandType, new CarelinkShortMessageBody(new byte[] { 0 }));
        // look for ack from short message
        PumpMessage shortResponse = sendAndListen(shortMessage);

        if (shortResponse.commandType != MedtronicCommandType.CommandACK) {
            LOG.error("runCommandWithFrames: Pump did not ack Attention packet");

            return new PumpMessage("No ACK after start message.");
        } else {
            LOG.debug("Run command with Frames: Got ACK response for Attention packet");
        }

        int frameNr = 1;

        for (List<Byte> frame : frames) {

            byte[] frameData = MedtronicUtil.createByteArray(frame);

            // LOG.debug("Frame {} data:\n{}", frameNr, ByteUtil.getCompactString(frameData));

            PumpMessage msg = makePumpMessage(commandType, new CarelinkLongMessageBody(frameData));

            rval = sendAndListen(msg);

            // LOG.debug("PumpResponse: " + rval);

            if (rval.commandType != MedtronicCommandType.CommandACK) {
                LOG.error("runCommandWithFrames: Pump did not ACK frame #{}", frameNr);

                LOG.error("Run command with Frames FAILED (command={}, response={})", commandType.name(),
                    rval.toString());

                return new PumpMessage("No ACK after frame #" + frameNr);
            } else {
                LOG.debug("Run command with Frames: Got ACK response for frame #{}", (frameNr));
            }

            frameNr++;
        }

        return rval;

    }


    public PumpHistoryResult getPumpHistory(PumpHistoryEntry lastEntry, LocalDateTime targetDate) {

        PumpHistoryResult pumpTotalResult = new PumpHistoryResult(lastEntry, targetDate == null ? null
            : DateTimeUtil.toATechDate(targetDate));

        if (doWakeUpBeforeCommand)
            wakeUp(receiverDeviceAwakeForMinutes, false);

        LOG.debug("Current command: " + MedtronicUtil.getCurrentCommand());

        MedtronicUtil.setPumpDeviceState(PumpDeviceState.Active);
        boolean doneWithError = false;

        for (int pageNumber = 0; pageNumber < 16; pageNumber++) {

            RawHistoryPage rawHistoryPage = new RawHistoryPage();
            // wakeUp(receiverDeviceAwakeForMinutes, false);
            PumpMessage getHistoryMsg = makePumpMessage(MedtronicCommandType.GetHistoryData,
                new GetHistoryPageCarelinkMessageBody(pageNumber));

            LOG.info("getPumpHistory: Page {}", pageNumber);
            // LOG.info("getPumpHistoryPage("+pageNumber+"): "+ByteUtil.shortHexString(getHistoryMsg.getTxData()));
            // Ask the pump to transfer history (we get first frame?)

            PumpMessage firstResponse = null;
            boolean failed = false;

            MedtronicUtil.setCurrentCommand(MedtronicCommandType.GetHistoryData, pageNumber, null);

            for (int retries = 0; retries < MAX_COMMAND_TRIES; retries++) {

                try {
                    firstResponse = runCommandWithArgs(getHistoryMsg);
                    failed = false;
                    break;
                } catch (RileyLinkCommunicationException e) {
                    LOG.error("First call for PumpHistory failed (retry={})", retries);
                    failed = true;
                }
            }

            if (failed) {
                MedtronicUtil.setPumpDeviceState(PumpDeviceState.Sleeping);
                return pumpTotalResult;
            }

            // LOG.info("getPumpHistoryPage("+pageNumber+"): " + ByteUtil.shortHexString(firstResponse.getContents()));

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
                        LOG.warn("Expected frame of length 64, got frame of length " + frameData.length);
                        // but append it anyway?
                    }
                    // handle successful frame data
                    rawHistoryPage.appendData(currentResponse.getFrameData());
                    // RileyLinkMedtronicService.getInstance().announceProgress(((100 / 16) *
                    // currentResponse.getFrameNumber() + 1));
                    MedtronicUtil.setCurrentCommand(MedtronicCommandType.GetHistoryData, pageNumber,
                        currentResponse.getFrameNumber());

                    LOG.info("getPumpHistory: Got frame {} of Page {}", currentResponse.getFrameNumber(), pageNumber);
                    // Do we need to ask for the next frame?
                    if (expectedFrameNum < 16) { // This number may not be correct for pumps other than 522/722
                        expectedFrameNum++;
                    } else {
                        done = true; // successful completion
                    }
                } else {
                    if (frameData == null) {
                        LOG.error("null frame data, retrying");
                    } else if (currentResponse.getFrameNumber() != expectedFrameNum) {
                        LOG.warn("Expected frame number {}, received {} (retrying)", expectedFrameNum,
                            currentResponse.getFrameNumber());
                    } else if (frameData.length == 0) {
                        LOG.warn("Frame has zero length, retrying");
                    }
                    failures++;
                    if (failures == 6) {
                        LOG.error(
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
                            LOG.error("Problem acknowledging frame response. (retry={})", retries);
                        }
                    }

                    if (nextMsg != null)
                        currentResponse = new GetHistoryPageCarelinkMessageBody(nextMsg.getMessageBody().getTxData());
                    else
                        LOG.error("We couldn't acknowledge frame from pump, aborting operation.");
                }
            }

            if (rawHistoryPage.getLength() != 1024) {
                LOG.warn("getPumpHistory: short page.  Expected length of 1024, found length of "
                    + rawHistoryPage.getLength());
                doneWithError = true;
            }

            if (!rawHistoryPage.isChecksumOK()) {
                LOG.error("getPumpHistory: checksum is wrong");
                doneWithError = true;
            }

            // TODO handle error states

            if (doneWithError) {
                MedtronicUtil.setPumpDeviceState(PumpDeviceState.Sleeping);
                return pumpTotalResult;
            }

            rawHistoryPage.dumpToDebug();

            List<PumpHistoryEntry> medtronicHistoryEntries = pumpHistoryDecoder
                .processPageAndCreateRecords(rawHistoryPage);

            LOG.debug("getPumpHistory: Found {} history entries.", medtronicHistoryEntries.size());

            // PumpHistoryResult pumpHistoryResult = new PumpHistoryResult(lastEntry, targetDate);
            pumpTotalResult.addHistoryEntries(medtronicHistoryEntries);

            LOG.debug("getPumpHistory: Search status: Search finished: {}", pumpTotalResult.isSearchFinished());

            if (pumpTotalResult.isSearchFinished()) {
                MedtronicUtil.setPumpDeviceState(PumpDeviceState.Sleeping);

                return pumpTotalResult;
            }

        }

        MedtronicUtil.setPumpDeviceState(PumpDeviceState.Sleeping);

        return pumpTotalResult;

    }


    // @Deprecated
    // public Page getPumpHistoryPage(int pageNumber) {
    // RawHistoryPage rval = new RawHistoryPage();
    //
    // if (doWakeUpBeforeCommand)
    // wakeUp(receiverDeviceAwakeForMinutes, false);
    //
    // PumpMessage getHistoryMsg = makePumpMessage(MedtronicCommandType.GetHistoryData,
    // new GetHistoryPageCarelinkMessageBody(pageNumber));
    // // LOG.info("getPumpHistoryPage("+pageNumber+"): "+ByteUtil.shortHexString(getHistoryMsg.getTxData()));
    // // Ask the pump to transfer history (we get first frame?)
    // PumpMessage firstResponse = runCommandWithArgs(getHistoryMsg);
    // // LOG.info("getPumpHistoryPage("+pageNumber+"): " + ByteUtil.shortHexString(firstResponse.getContents()));
    //
    // PumpMessage ackMsg = makePumpMessage(MedtronicCommandType.CommandACK, new PumpAckMessageBody());
    // GetHistoryPageCarelinkMessageBody currentResponse = new GetHistoryPageCarelinkMessageBody(firstResponse
    // .getMessageBody().getTxData());
    // int expectedFrameNum = 1;
    // boolean done = false;
    // // while (expectedFrameNum == currentResponse.getFrameNumber()) {
    // int failures = 0;
    // while (!done) {
    // // examine current response for problems.
    // byte[] frameData = currentResponse.getFrameData();
    // if ((frameData != null) && (frameData.length > 0) && currentResponse.getFrameNumber() == expectedFrameNum) {
    // // success! got a frame.
    // if (frameData.length != 64) {
    // LOG.warn("Expected frame of length 64, got frame of length " + frameData.length);
    // // but append it anyway?
    // }
    // // handle successful frame data
    // rval.appendData(currentResponse.getFrameData());
    // RileyLinkMedtronicService.getInstance().announceProgress(
    // ((100 / 16) * currentResponse.getFrameNumber() + 1));
    // LOG.info("getPumpHistoryPage: Got frame " + currentResponse.getFrameNumber());
    // // Do we need to ask for the next frame?
    // if (expectedFrameNum < 16) { // This number may not be correct for pumps other than 522/722
    // expectedFrameNum++;
    // } else {
    // done = true; // successful completion
    // }
    // } else {
    // if (frameData == null) {
    // LOG.error("null frame data, retrying");
    // } else if (currentResponse.getFrameNumber() != expectedFrameNum) {
    // LOG.warn("Expected frame number {}, received {} (retrying)", expectedFrameNum,
    // currentResponse.getFrameNumber());
    // } else if (frameData.length == 0) {
    // LOG.warn("Frame has zero length, retrying");
    // }
    // failures++;
    // if (failures == 6) {
    // LOG.error("6 failures in attempting to download frame {} of page {}, giving up.", expectedFrameNum,
    // pageNumber);
    // done = true; // failure completion.
    // }
    // }
    // if (!done) {
    // // ask for next frame
    // PumpMessage nextMsg = sendAndListen(ackMsg);
    // currentResponse = new GetHistoryPageCarelinkMessageBody(nextMsg.getMessageBody().getTxData());
    // }
    // }
    // if (rval.getLength() != 1024) {
    // LOG.warn("getPumpHistoryPage: short page.  Expected length of 1024, found length of " + rval.getLength());
    // }
    // if (!rval.isChecksumOK()) {
    // LOG.error("getPumpHistoryPage: checksum is wrong");
    // }
    //
    // rval.dumpToDebug();
    //
    // Page page = new Page();
    // // page.parseFrom(rval.getData(),PumpModel.MM522);
    // // FIXME
    // page.parseFrom(rval.getData(), MedtronicDeviceType.Medtronic_522);
    //
    // return page;
    // }

    // public ArrayList<Page> getAllHistoryPages() {
    // ArrayList<Page> pages = new ArrayList<>();
    //
    // for (int pageNum = 0; pageNum < 16; pageNum++) {
    // pages.add(getPumpHistoryPage(pageNum));
    // }
    //
    // return pages;
    // }

    // public ArrayList<Page> getHistoryEventsSinceDate(Instant when) {
    // ArrayList<Page> pages = new ArrayList<>();
    // for (int pageNum = 0; pageNum < 16; pageNum++) {
    // pages.add(getPumpHistoryPage(pageNum));
    // for (Page page : pages) {
    // for (Record r : page.mRecordList) {
    // LocalDateTime timestamp = r.getTimestamp().getLocalDateTime();
    // LOG.info("Found record: (" + r.getClass().getSimpleName() + ") " + timestamp.toString());
    // }
    // }
    // }
    // return pages;
    // }

    public String getErrorResponse() {
        return this.errorMessage;
    }


    @Override
    public byte[] createPumpMessageContent(RLMessageType type) {
        switch (type) {
            case PowerOn:
                return MedtronicUtil.buildCommandPayload(MedtronicCommandType.RFPowerOn, //
                    new byte[] { 2, 1, (byte)receiverDeviceAwakeForMinutes }); // maybe this is better FIXME

            case ReadSimpleData:
                return MedtronicUtil.buildCommandPayload(MedtronicCommandType.PumpModel, null);
        }
        return new byte[0];
    }


    private PumpMessage makePumpMessage(MedtronicCommandType messageType, byte[] body) {
        return makePumpMessage(messageType, body == null ? new CarelinkShortMessageBody()
            : new CarelinkShortMessageBody(body));
    }


    private PumpMessage makePumpMessage(MedtronicCommandType messageType) {
        return makePumpMessage(messageType, (byte[])null);
    }


    private PumpMessage makePumpMessage(MedtronicCommandType messageType, MessageBody messageBody) {
        PumpMessage msg = new PumpMessage();
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

        MedtronicUtil.setPumpDeviceState(PumpDeviceState.Active);

        // create message
        PumpMessage msg;

        if (bodyData == null)
            msg = makePumpMessage(commandType);
        else
            msg = makePumpMessage(commandType, bodyData);

        // send and wait for response
        PumpMessage response = sendAndListen(msg, timeoutMs);

        MedtronicUtil.setPumpDeviceState(PumpDeviceState.Sleeping);

        return response;
    }


    private PumpMessage sendAndListen(RLMessage msg) throws RileyLinkCommunicationException {
        return sendAndListen(msg, 4000); // 2000
    }


    // All pump communications go through this function.
    private PumpMessage sendAndListen(RLMessage msg, int timeout_ms) throws RileyLinkCommunicationException {
        return sendAndListen(msg, timeout_ms, PumpMessage.class);
    }


    private Object sendAndGetResponseWithCheck(MedtronicCommandType commandType) {

        return sendAndGetResponseWithCheck(commandType, null);
    }


    private Object sendAndGetResponseWithCheck(MedtronicCommandType commandType, byte[] bodyData) {

        LOG.debug("getDataFromPump: {}", commandType);

        for (int retries = 0; retries < MAX_COMMAND_TRIES; retries++) {

            try {
                PumpMessage response = null;

                response = sendAndGetResponse(commandType, bodyData, DEFAULT_TIMEOUT + (DEFAULT_TIMEOUT * retries));

                String check = checkResponseContent(response, commandType.commandDescription,
                    commandType.expectedLength);

                if (check == null) {

                    Object dataResponse = medtronicConverter.convertResponse(commandType, response.getRawContent());

                    LOG.debug("Converted response for {} is {}.", commandType.name(), dataResponse);

                    return dataResponse;
                } else {
                    this.errorMessage = check;
                    // return null;
                }

            } catch (RileyLinkCommunicationException e) {
                LOG.warn("Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
            }

        }

        return null;
    }


    private String checkResponseContent(PumpMessage response, String method, int expectedLength) {

        if (!response.isValid()) {
            String responseData = String.format("%s: Invalid response.", method);
            LOG.warn(responseData);
            return responseData;
        }

        byte[] contents = response.getRawContent();

        if (contents != null) {
            if (contents.length >= expectedLength) {
                LOG.trace("{}: Content: {}", method, HexDump.toHexStringDisplayable(contents));
                return null;

            } else {
                String responseData = String.format(
                    "%s: Cannot return data. Data is too short [expected=%s, received=%s].", method, ""
                        + expectedLength, "" + contents.length);

                LOG.warn(responseData);
                return responseData;
            }
        } else {
            String responseData = String.format("%s: Cannot return data. Null response.", method);
            LOG.warn(responseData);
            return responseData;
        }
    }


    // PUMP SPECIFIC COMMANDS

    public Float getRemainingInsulin() {

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.GetRemainingInsulin);

        return responseObject == null ? null : (Float)responseObject;
    }


    public MedtronicDeviceType getPumpModel() {

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.PumpModel);

        // if (!MedtronicUtil.isModelSet()) {
        // MedtronicUtil.setMedtronicPumpModel((MedtronicDeviceType)responseObject);
        // }

        return responseObject == null ? null : (MedtronicDeviceType)responseObject;
    }


    public BasalProfile getBasalProfile() {

        // wakeUp
        if (doWakeUpBeforeCommand)
            wakeUp(receiverDeviceAwakeForMinutes, false);

        MedtronicCommandType commandType = MedtronicCommandType.GetBasalProfileSTD;

        LOG.debug("getDataFromPump: {}", commandType);

        MedtronicUtil.setCurrentCommand(commandType);

        MedtronicUtil.setPumpDeviceState(PumpDeviceState.Active);

        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {

            try {
                // create message
                PumpMessage msg;

                // if (bodyData == null)
                msg = makePumpMessage(commandType);

                // send and wait for response
                PumpMessage response = null;

                response = sendAndListen(msg, DEFAULT_TIMEOUT + (DEFAULT_TIMEOUT * retries));

                LOG.debug("1st Response: " + HexDump.toHexStringDisplayable(response.getRawContent()));
                LOG.debug("1st Response: " + HexDump.toHexStringDisplayable(response.getMessageBody().getTxData()));

                String check = checkResponseContent(response, commandType.commandDescription, 1);

                byte[] data = null;

                int runs = 1;

                if (check == null) {

                    data = response.getRawContent();

                    PumpMessage ackMsg = makePumpMessage(MedtronicCommandType.CommandACK, new PumpAckMessageBody());

                    while (checkIfWeHaveMoreData(commandType, response, data)) {

                        runs++;

                        PumpMessage response2 = sendAndListen(ackMsg, DEFAULT_TIMEOUT + (DEFAULT_TIMEOUT * retries));

                        LOG.debug("{} Response: {}", runs, HexDump.toHexStringDisplayable(response2.getRawContent()));
                        LOG.debug("{} Response: {}", runs,
                            HexDump.toHexStringDisplayable(response2.getMessageBody().getTxData()));

                        String check2 = checkResponseContent(response2, commandType.commandDescription, 1);

                        if (check2 == null) {

                            data = ByteUtil.concat(data, ByteUtil.substring(response2.getMessageBody().getTxData(), 1));

                        } else {
                            this.errorMessage = check2;
                            LOG.debug("Error message: " + check2);
                        }
                    }

                } else {
                    errorMessage = check;
                }

                BasalProfile basalProfile = (BasalProfile)medtronicConverter.convertResponse(commandType, data);

                LOG.debug("Converted response for {} is {}.", commandType.name(), basalProfile);

                MedtronicUtil.setCurrentCommand(null);
                MedtronicUtil.setPumpDeviceState(PumpDeviceState.Sleeping);

                return basalProfile;

            } catch (RileyLinkCommunicationException e) {
                LOG.warn("Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
            }
        }

        LOG.warn("Error reading profile in max retries.");
        MedtronicUtil.setCurrentCommand(null);
        MedtronicUtil.setPumpDeviceState(PumpDeviceState.Sleeping);

        return null;

    }


    private boolean checkIfWeHaveMoreData(MedtronicCommandType commandType, PumpMessage response, byte[] data) {

        if (commandType == MedtronicCommandType.GetBasalProfileSTD || //
            commandType == MedtronicCommandType.GetBasalProfileA || //
            commandType == MedtronicCommandType.GetBasalProfileB) {
            byte[] responseRaw = response.getRawContent();

            int last = responseRaw.length - 1;

            LOG.debug("Length: " + data.length);

            if (data.length >= BasalProfile.MAX_RAW_DATA_SIZE) {
                return false;
            }

            if (responseRaw.length == 1) {
                return false;
            }

            return !(responseRaw[last] == 0x00 && responseRaw[last - 1] == 0x00 && responseRaw[last - 2] == 0x00);
        }

        return false;
    }


    public ClockDTO getPumpTime() {

        ClockDTO clockDTO = new ClockDTO();
        clockDTO.localDeviceTime = new LocalDateTime();

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.RealTimeClock);

        if (responseObject != null) {
            clockDTO.pumpTime = (LocalDateTime)responseObject;
            return clockDTO;
        }

        return null;
    }


    public TempBasalPair getTemporaryBasal() {

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.ReadTemporaryBasal);

        return responseObject == null ? null : (TempBasalPair)responseObject;
    }


    public Map<String, PumpSettingDTO> getPumpSettings() {

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.getSettings(MedtronicUtil
            .getMedtronicPumpModel()));

        return responseObject == null ? null : (Map<String, PumpSettingDTO>)responseObject;
    }


    public Boolean setBolus(double units) {

        LOG.info("setBolus: " + units);

        return setCommand(MedtronicCommandType.SetBolus, MedtronicUtil.getBolusStrokes(units));

    }


    public boolean setTBR(TempBasalPair tbr) {

        LOG.info("setTBR: " + tbr.getDescription());

        return setCommand(MedtronicCommandType.SetTemporaryBasal, tbr.getAsRawData());
    }


    private boolean setCommand(MedtronicCommandType commandType, byte[] body) {

        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {

            try {
                if (this.doWakeUpBeforeCommand)
                    wakeUp(false);

                if (debugSetCommands)
                    LOG.debug("{}: Body - {}", commandType.getCommandDescription(),
                        HexDump.toHexStringDisplayable(body));

                PumpMessage msg = makePumpMessage(commandType, new CarelinkLongMessageBody(body));

                PumpMessage pumpMessage = runCommandWithArgs(msg);

                if (debugSetCommands)
                    LOG.debug("{}: {}", commandType.getCommandDescription(), pumpMessage.getResponseContent());

                return pumpMessage.commandType == MedtronicCommandType.CommandACK;

            } catch (RileyLinkCommunicationException e) {
                LOG.warn("Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
            }
        }

        return false;
    }


    public boolean cancelTBR() {
        return setTBR(new TempBasalPair(0.0d, false, 0));
    }


    public BatteryStatusDTO getRemainingBattery() {

        Object responseObject = sendAndGetResponseWithCheck(MedtronicCommandType.GetBatteryStatus);

        return responseObject == null ? null : (BatteryStatusDTO)responseObject;
    }


    public Boolean setBasalProfile(BasalProfile basalProfile) {

        List<List<Byte>> basalProfileFrames = MedtronicUtil.getBasalProfileFrames(basalProfile.getRawData());

        for (int retries = 0; retries <= MAX_COMMAND_TRIES; retries++) {

            // PumpMessage responseMessage = null;
            try {
                PumpMessage responseMessage = runCommandWithFrames(MedtronicCommandType.SetBasalProfileSTD,
                    basalProfileFrames);

                return responseMessage.commandType == MedtronicCommandType.CommandACK;
            } catch (RileyLinkCommunicationException e) {
                LOG.warn("Error getting response from RileyLink (error={}, retry={})", e.getMessage(), retries + 1);
            }

            // LOG.debug("Set Basal Profile: {}", HexDump.toHexStringDisplayable(responseMessage.getRawContent()));

        }

        return false;

    }


    // FIXME --- After this line commands in development --- REMOVE THIS COMMANDS

    // TODO test

    // TODO remove, we will see state from History
    // public PumpMessage getPumpState() {
    // PumpMessage response = sendAndGetResponse(MedtronicCommandType.PumpState);
    //
    // byte[] data = response.getRawContent();
    //
    // LOG.debug("Pump State: {}", HexDump.toHexStringDisplayable(data));
    //
    // // 3 TBR running ?
    //
    // return null;
    // }

    // TODO remove, we will see bolus status from History
    // public PumpMessage getBolusStatus() {
    // PumpMessage response = sendAndGetResponse(MedtronicCommandType.SetBolus, new byte[] { 0x03, 0x00, 0x00, 0x00 },
    // 4000);
    //
    // byte[] data = response.getRawContent();
    //
    // LOG.debug("Detect bolus: {}", HexDump.toHexStringDisplayable(data));
    //
    // // 3 TBR running ?
    //
    // return null;
    // }

    public PumpMessage cancelBolus() {
        // ? maybe suspend and resume
        return null;
    }

    // Set TBR 100%
    // Cancel TBR (set TBR 100%) 100%
    // Get Status (40%)

    // Set Bolus 100%
    // Set Extended Bolus 20%
    // Cancel Bolus 0% -- NOT SUPPORTED
    // Cancel Extended Bolus 0% -- NOT SUPPORTED

    // Get Basal Profile (0x92) Read STD 100%
    // Set Basal Profile 0% -- NOT SUPPORTED
    // Read History 60%
    // Load TDD ?

}

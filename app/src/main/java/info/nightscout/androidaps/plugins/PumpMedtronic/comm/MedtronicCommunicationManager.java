package info.nightscout.androidaps.plugins.PumpMedtronic.comm;

import android.content.Context;

import org.joda.time.IllegalFieldValueException;
import org.joda.time.Instant;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.RLMessage;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data.RLMessageType;
import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpCommon.utils.StringUtil;
import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.BasalProfile;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.Page;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.PumpModel;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.RawHistoryPage;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.TempBasalPair;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.history.Record;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.message.ButtonPressCarelinkMessageBody;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.message.CarelinkShortMessageBody;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.message.GetHistoryPageCarelinkMessageBody;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.message.GetPumpModelCarelinkMessageBody;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.message.MessageBody;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.message.MessageType;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.message.PacketType;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.message.PumpAckMessageBody;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.message.PumpMessage;
import info.nightscout.androidaps.plugins.PumpMedtronic.service.RileyLinkMedtronicService;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;

//import com.gxwtech.roundtrip2.ServiceData.ServiceResult;


/**
 * Created by geoff on 5/30/16.
 * <p>
 * Split into 2 implementations, so that we can split it by target device. - Andy
 */
public class MedtronicCommunicationManager extends RileyLinkCommunicationManager {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicCommunicationManager.class);

    private static double[] scanFrequenciesUS = {916.45, 916.50, 916.55, 916.60, 916.65, 916.70, 916.75, 916.80};
    private static double[] scanFrequenciesWorldwide = {868.25, 868.30, 868.35, 868.40, 868.45, 868.50, 868.55, 868.60, 868.65};

    byte[] pumpID;

    public MedtronicCommunicationManager(Context context, RFSpy rfspy, boolean hasUSfrequency, byte[] pumpID) {
        super(context, rfspy, hasUSfrequency ? scanFrequenciesUS : scanFrequenciesWorldwide);
        this.pumpID = pumpID;
        //prefs = context.getSharedPreferences(RT2Const.serviceLocal.sharedPreferencesKey, Context.MODE_PRIVATE);
        this.pumpStatus = MedtronicPumpPlugin.getPlugin().getPumpStatusData();
    }

    private PumpMessage runCommandWithArgs(PumpMessage msg) {
        PumpMessage rval;
        PumpMessage shortMessage = makePumpMessage(msg.messageType, new CarelinkShortMessageBody(new byte[]{0}));
        // look for ack from short message
        PumpMessage shortResponse = sendAndListen(shortMessage);
        if (shortResponse.messageType == MessageType.PumpAck) {
            rval = sendAndListen(msg);
            return rval;
        } else {
            LOG.error("runCommandWithArgs: Pump did not ack Attention packet");
        }
        return new PumpMessage();
    }


    public Page getPumpHistoryPage(int pageNumber) {
        RawHistoryPage rval = new RawHistoryPage();
        wakeup(receiverDeviceAwakeForMinutes);
        PumpMessage getHistoryMsg = makePumpMessage(MessageType.GetHistoryPage, new GetHistoryPageCarelinkMessageBody(pageNumber));
        //LOG.info("getPumpHistoryPage("+pageNumber+"): "+ByteUtil.shortHexString(getHistoryMsg.getTxData()));
        // Ask the pump to transfer history (we get first frame?)
        PumpMessage firstResponse = runCommandWithArgs(getHistoryMsg);
        //LOG.info("getPumpHistoryPage("+pageNumber+"): " + ByteUtil.shortHexString(firstResponse.getContents()));

        PumpMessage ackMsg = makePumpMessage(MessageType.PumpAck, new PumpAckMessageBody());
        GetHistoryPageCarelinkMessageBody currentResponse = new GetHistoryPageCarelinkMessageBody(firstResponse.getMessageBody().getTxData());
        int expectedFrameNum = 1;
        boolean done = false;
        //while (expectedFrameNum == currentResponse.getFrameNumber()) {
        int failures = 0;
        while (!done) {
            // examine current response for problems.
            byte[] frameData = currentResponse.getFrameData();
            if ((frameData != null) && (frameData.length > 0) && currentResponse.getFrameNumber() == expectedFrameNum) {
                // success! got a frame.
                if (frameData.length != 64) {
                    LOG.warn("Expected frame of length 64, got frame of length " + frameData.length);
                    // but append it anyway?
                }
                // handle successful frame data
                rval.appendData(currentResponse.getFrameData());
                RileyLinkMedtronicService.getInstance().announceProgress(((100 / 16) * currentResponse.getFrameNumber() + 1));
                LOG.info("getPumpHistoryPage: Got frame " + currentResponse.getFrameNumber());
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
                    LOG.warn("Expected frame number %d, received %d (retrying)", expectedFrameNum, currentResponse.getFrameNumber());
                } else if (frameData.length == 0) {
                    LOG.warn("Frame has zero length, retrying");
                }
                failures++;
                if (failures == 6) {
                    LOG.error("6 failures in attempting to download frame %d of page %d, giving up.", expectedFrameNum, pageNumber);
                    done = true; // failure completion.
                }
            }
            if (!done) {
                // ask for next frame
                PumpMessage nextMsg = sendAndListen(ackMsg);
                currentResponse = new GetHistoryPageCarelinkMessageBody(nextMsg.getMessageBody().getTxData());
            }
        }
        if (rval.getLength() != 1024) {
            LOG.warn("getPumpHistoryPage: short page.  Expected length of 1024, found length of " + rval.getLength());
        }
        if (!rval.isChecksumOK()) {
            LOG.error("getPumpHistoryPage: checksum is wrong");
        }

        rval.dumpToDebug();

        Page page = new Page();
        //page.parseFrom(rval.getData(),PumpModel.MM522);
        // FIXME
        page.parseFrom(rval.getData(), PumpModel.MM522);

        return page;
    }

    public ArrayList<Page> getAllHistoryPages() {
        ArrayList<Page> pages = new ArrayList<>();

        for (int pageNum = 0; pageNum < 16; pageNum++) {
            pages.add(getPumpHistoryPage(pageNum));
        }

        return pages;
    }

    public ArrayList<Page> getHistoryEventsSinceDate(Instant when) {
        ArrayList<Page> pages = new ArrayList<>();
        for (int pageNum = 0; pageNum < 16; pageNum++) {
            pages.add(getPumpHistoryPage(pageNum));
            for (Page page : pages) {
                for (Record r : page.mRecordList) {
                    LocalDateTime timestamp = r.getTimestamp().getLocalDateTime();
                    LOG.info("Found record: (" + r.getClass().getSimpleName() + ") " + timestamp.toString());
                }
            }
        }
        return pages;
    }


    public void hunt() {
        //tryoutPacket(new byte[] {MessageType.CMD_M_READ_PUMP_STATUS,0});
        //tryoutPacket(new byte[] {MessageType.CMD_M_READ_FIRMWARE_VER,0});
        //tryoutPacket(new byte[] {MessageType.CMD_M_READ_INSULIN_REMAINING,0});

    }

    // See ButtonPressCarelinkMessageBody
    public void pressButton(int which) {
        wakeup(receiverDeviceAwakeForMinutes);
        PumpMessage pressButtonMessage = makePumpMessage(MessageType.ButtonPress, new ButtonPressCarelinkMessageBody(which));
        PumpMessage resp = sendAndListen(pressButtonMessage);
        if (resp.messageType != MessageType.PumpAck) {
            LOG.error("Pump did not ack button press.");
        }
    }


    @Override
    public RLMessage makeRLMessage(RLMessageType type, byte[] data) {
        switch (type) {
            case PowerOn:
                return makePumpMessage(MessageType.PowerOn, new CarelinkShortMessageBody(data));

            case ReadSimpleData:
                return makePumpMessage(MessageType.GetPumpModel, new GetPumpModelCarelinkMessageBody());

        }
        return null;
    }


    @Override
    public RLMessage makeRLMessage(byte[] data) {
        return makePumpMessage(data);
    }


    protected PumpMessage makePumpMessage(MessageType messageType, MessageBody messageBody) {
        PumpMessage msg = new PumpMessage();
        msg.init(PacketType.Carelink, pumpID, messageType, messageBody);
        return msg;
    }


    protected PumpMessage makePumpMessage(byte msgType, MessageBody body) {
        return makePumpMessage(MessageType.getByValue(msgType), body);
    }


    protected PumpMessage makePumpMessage(MessageType messageType, byte[] body) {
        return makePumpMessage(messageType, body == null ? new CarelinkShortMessageBody() : new CarelinkShortMessageBody(body));
    }


    protected PumpMessage makePumpMessage(MessageType messageType) {
        return makePumpMessage(messageType, (byte[]) null);
    }


    protected PumpMessage makePumpMessage(byte[] typeAndBody) {
        PumpMessage msg = new PumpMessage();
        msg.init(ByteUtil.concat(ByteUtil.concat(new byte[]{(byte) 0xa7}, pumpID), typeAndBody));
        return msg;
    }


    private PumpMessage sendAndGetResponse(MessageType messageType) {
        return sendAndGetResponse(messageType, null);
    }


    private PumpMessage sendAndGetResponse(MessageType messageType, byte[] bodyData) {
        // wakeUp
        wakeup(receiverDeviceAwakeForMinutes);

        // create message
        PumpMessage msg;

        if (bodyData == null)
            msg = makePumpMessage(messageType);
        else
            msg = makePumpMessage(messageType, bodyData);

        // send and wait for response
        PumpMessage response = sendAndListen(msg);
        return response;
    }


    // Get Medtronic specific data
    private LocalDateTime parsePumpRTCBytes(byte[] bytes) {
        if (bytes == null) return null;
        if (bytes.length < 7) return null;
        int hours = ByteUtil.asUINT8(bytes[0]);
        int minutes = ByteUtil.asUINT8(bytes[1]);
        int seconds = ByteUtil.asUINT8(bytes[2]);
        int year = (ByteUtil.asUINT8(bytes[4]) & 0x3f) + 1984;
        int month = ByteUtil.asUINT8(bytes[5]);
        int day = ByteUtil.asUINT8(bytes[6]);
        try {
            LocalDateTime pumpTime = new LocalDateTime(year, month, day, hours, minutes, seconds);
            return pumpTime;
        } catch (IllegalFieldValueException e) {
            LOG.error("parsePumpRTCBytes: Failed to parse pump time value: year=%d, month=%d, hours=%d, minutes=%d, seconds=%d", year, month, day, hours, minutes, seconds);
            return null;
        }
    }


    public LocalDateTime getPumpRTC() {
        //ReadPumpClockResult rval = new ReadPumpClockResult();
        wakeup(receiverDeviceAwakeForMinutes);
        PumpMessage getRTCMsg = makePumpMessage(MessageType.ReadTime, new byte[]{0});
        LOG.info("getPumpRTC: " + ByteUtil.shortHexString(getRTCMsg.getTxData()));
        PumpMessage response = sendAndListen(getRTCMsg);
        if (response.isValid()) {
            byte[] receivedData = response.getContents();
            if (receivedData != null) {
                if (receivedData.length >= 9) {
                    LocalDateTime pumpTime = parsePumpRTCBytes(ByteUtil.substring(receivedData, 2, 7));
                    return pumpTime;
                }
            }
        } else {
            LOG.error("Invalid response: {}", ByteUtil.showPrintable(response.getContents()));
        }

        return null;
    }


    public PumpModel getPumpModel() {
        wakeup(receiverDeviceAwakeForMinutes);
        PumpMessage msg = makePumpMessage(MessageType.GetPumpModel, new GetPumpModelCarelinkMessageBody());
        LOG.info("getPumpModel: " + ByteUtil.shortHexString(msg.getTxData()));
        PumpMessage response = sendAndListen(msg);
        LOG.info("getPumpModel response: " + ByteUtil.shortHexString(response.getContents()));
        byte[] contents = response.getContents();
        PumpModel rval = PumpModel.UNSET;
        if (contents != null) {
            if (contents.length >= 7) {
                rval = PumpModel.fromString(StringUtil.fromBytes(ByteUtil.substring(contents, 3, 3)));
            } else {
                LOG.warn("getPumpModel: Cannot return pump model number: data is too short.");
            }
        } else {
            LOG.warn("getPumpModel: Cannot return pump model number: null response");
        }

        return rval;
    }


    // TODO remove for AAPS
//    public ISFTable getPumpISFProfile() {
//
//        PumpMessage response = sendAndGetResponse(MessageType.GetISFProfile);
//
//        ISFTable table = new ISFTable();
//        table.parseFrom(response.getContents());
//        return table;
//    }

    // TODO remove for AAPS
    public PumpMessage getBolusWizardCarbProfile() {
        PumpMessage response = sendAndGetResponse(MessageType.CMD_M_READ_CARB_RATIOS);

        return response;
    }

    // TODO check
    public Integer getRemainingBattery() {
        PumpMessage response = sendAndGetResponse(MessageType.GetBattery);

        // TODO decode here

        if (response.isValid()) {
            byte[] remainingBatteryBytes = response.getContents();
            if (remainingBatteryBytes != null) {
                if (remainingBatteryBytes.length == 5) {
                    /**
                     * 0x72 0x03, 0x00, 0x00, 0x82
                     * meaning what ????
                     */

                    // FIXME use RawData and decoding is not correct
                    // TODO review this !!! Andy

                    return ByteUtil.asUINT8(remainingBatteryBytes[5]);
                }
            }
        }


        return null;
    }

    // TODO check
    public Float getRemainingInsulin() {
        PumpMessage response = sendAndGetResponse(MessageType.CMD_M_READ_INSULIN_REMAINING);

        // TODO decode here
        byte[] data = response.getRawContent();

        float value = MedtronicUtil.makeUnsignedShort(data[1], data[0]) / 10.0f;

        return value;
    }

    // FIXME check
    public TempBasalPair getCurrentBasalRate() {
        PumpMessage response = sendAndGetResponse(MessageType.ReadTempBasal);

        TempBasalPair tbr = new TempBasalPair(response.getRawContent());

        return tbr;
    }


    // TODO test
    public BasalProfile getProfile() {

        PumpMessage response = sendAndGetResponse(MessageType.ReadBasalProfileSTD);

        return new BasalProfile(response.getRawContent());
    }

    // TODO generateRawData (check if it works correctly) and test
    public PumpMessage setProfile(BasalProfile basalProfile) {

        basalProfile.generateRawData();

        PumpMessage response = sendAndGetResponse(MessageType.SetBasalProfileSTD, basalProfile.getRawData());

        // what kind of response are we expecting when set it sent
        return response;
    }

    // TODO test
    public PumpMessage setTBR(TempBasalPair tbr) {

        // TODO check getAs Raw Data is correct data

        PumpMessage response = sendAndGetResponse(MessageType.ChangeTempBasal, tbr.getAsRawData());

        return response;

    }

    // TODO test
    public PumpMessage cancelTBR() {
        return setTBR(new TempBasalPair(0.0d, false, 0));
    }

    // TODO test ??? this might work correctly, check low value and some high value 25 (25 is max bolus)
    public PumpMessage setBolus(double units) {
        PumpMessage response = sendAndGetResponse(MessageType.Bolus, MedtronicUtil.getBolusStrokes(units));

        return response;
    }

    public PumpMessage cancelBolus() {
        //? maybe suspend and resume
        return null;
    }


    public PumpMessage setExtendedBolus(double units, int duration) {
        // FIXME see decocare
        PumpMessage response = sendAndGetResponse(MessageType.Bolus, MedtronicUtil.getBolusStrokes(units));

        return response;
    }

    public PumpMessage cancelExtendedBolus() {
        // set cancelBolus
        return null;
    }

    // Set TBR
    // Cancel TBR (set TBR 100%)
    // Get Status                  (40%)

    // Set Bolus                            20%
    // Set Extended Bolus                   20%
    // Cancel Bolus                         0% ?
    // Cancel Extended Bolus                0% ?

    // Get Basal Profile (0x92) Read STD    20%
    // Set Basal Profile                    20%
    // Read History                         60%
    // Load TDD                             ?

    public void updatePumpManagerStatus() {

        Integer resp = getRemainingBattery();
        pumpStatus.batteryRemaining = resp == null ? -1 : resp;

        //pumpStatus.remainUnits = getRemainingInsulin();

        /* current basal */
        TempBasalPair basalRate = getCurrentBasalRate();

        // FIXME
//        byte[] basalRateBytes = resp.getContents();
//        if (basalRateBytes != null) {
//            if (basalRateBytes.length == 2) {
//                /**
//                 * 0x98 0x06
//                 * 0x98 is "basal rate"
//                 * 0x06 is what? Not currently running a temp basal, current basal is "standard" at 0
//                 */
//                double basalRate = ByteUtil.asUINT8(basalRateBytes[1]);
//                pumpStatus.currentBasal = basalRate;
//            }
//        }
        // get last bolus amount
        // get last bolus time
        // get tempBasalInProgress
        // get tempBasalRatio
        // get tempBasalRemainMin
        // get tempBasalStart

        // get pump time
        LocalDateTime clockResult = getPumpRTC();
        if (clockResult != null) {
            //pumpStatus.time = clockResult.toDate();
        }
        // get last sync time

    }

    public void testPageDecode() {
        byte[] raw = new byte[]{(byte) 0x6D, (byte) 0x62, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x63, (byte) 0x10, (byte) 0x6D, (byte) 0x63, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x5A, (byte) 0xA5, (byte) 0x49, (byte) 0x04, (byte) 0x10, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x6D, (byte) 0xA5, (byte) 0x49, (byte) 0x04, (byte) 0x10, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x08, (byte) 0x64, (byte) 0x10, (byte) 0x6D, (byte) 0x64, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x08, (byte) 0x64, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x08, (byte) 0x64, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x08, (byte) 0x64, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x0C, (byte) 0x00,
                (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x64, (byte) 0x01, (byte) 0x75, (byte) 0x94, (byte) 0x0D, (byte) 0x05, (byte) 0x10, (byte) 0x64, (byte) 0x01, (byte) 0x44, (byte) 0x95, (byte) 0x0D, (byte) 0x05, (byte) 0x10, (byte) 0x17, (byte) 0x00, (byte) 0x4E, (byte) 0x95, (byte) 0x0D, (byte) 0x05, (byte) 0x10, (byte) 0x18, (byte) 0x00, (byte) 0x40, (byte) 0x95, (byte) 0x0D, (byte) 0x05, (byte) 0x10,
                (byte) 0x19, (byte) 0x00, (byte) 0x40, (byte) 0x81, (byte) 0x15, (byte) 0x05, (byte) 0x10, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x65, (byte) 0x10, (byte) 0x6D, (byte) 0x65, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x1A, (byte) 0x00, (byte) 0x47, (byte) 0x82, (byte) 0x09, (byte) 0x06,
                (byte) 0x10, (byte) 0x1A, (byte) 0x01, (byte) 0x5C, (byte) 0x82, (byte) 0x09, (byte) 0x06, (byte) 0x10, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x66, (byte) 0x10, (byte) 0x6D, (byte) 0x66, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x67, (byte) 0x10, (byte) 0x6D, (byte) 0x67, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x68, (byte) 0x10, (byte) 0x6D, (byte) 0x68, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x69, (byte) 0x10, (byte) 0x6D, (byte) 0x69, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x6A, (byte) 0x10, (byte) 0x6D, (byte) 0x6A, (byte) 0x10, (byte) 0x05, (byte) 0x0C,
                (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x6B, (byte) 0x10, (byte) 0x6D, (byte) 0x6B, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x6C,
                (byte) 0x10, (byte) 0x6D, (byte) 0x6C, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x6D, (byte) 0x10, (byte) 0x6D, (byte) 0x6D, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x6E, (byte) 0x10, (byte) 0x6D, (byte) 0x6E, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x6F, (byte) 0x10, (byte) 0x6D, (byte) 0x6F, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00,
                (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x19, (byte) 0x00, (byte) 0x40, (byte) 0x81, (byte) 0x03, (byte) 0x10, (byte) 0x10, (byte) 0x1A, (byte) 0x00, (byte) 0x68, (byte) 0x96, (byte) 0x0A, (byte) 0x10, (byte) 0x10, (byte) 0x1A, (byte) 0x01, (byte) 0x40, (byte) 0x97, (byte) 0x0A, (byte) 0x10, (byte) 0x10, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x70, (byte) 0x10, (byte) 0x6D, (byte) 0x70, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x71, (byte) 0x10, (byte) 0x6D, (byte) 0x71, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x72, (byte) 0x10, (byte) 0x6D, (byte) 0x72, (byte) 0x10, (byte) 0x05, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x73, (byte) 0x10, (byte) 0x6D, (byte) 0x73, (byte) 0x10, (byte) 0x05, (byte) 0x0C,
                (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x0C, (byte) 0x00, (byte) 0xE8, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x07, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x74, (byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x2C, (byte) 0x79,
        };
        Page page = new Page();
        page.parseFrom(raw, PumpModel.MM522);
        page.parseByDates(raw, PumpModel.MM522);
        page.parsePicky(raw, PumpModel.MM522);
        LOG.info("testPageDecode: done");
    }

}

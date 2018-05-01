package com.gxwtech.roundtrip2.RoundtripService.RileyLink;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import com.gxwtech.roundtrip2.RT2Const;
import com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.RFSpy;
import com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.RFSpyResponse;
import com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.RadioPacket;
import com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.RadioResponse;
import com.gxwtech.roundtrip2.RoundtripService.RoundtripService;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages.ButtonPressCarelinkMessageBody;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages.CarelinkShortMessageBody;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages.GetHistoryPageCarelinkMessageBody;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages.GetPumpModelCarelinkMessageBody;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages.MessageBody;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages.MessageType;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.Messages.PumpAckMessageBody;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PacketType;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.ISFTable;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.Page;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.records.Record;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpMessage;
import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpModel;
import com.gxwtech.roundtrip2.ServiceData.PumpStatusResult;
import com.gxwtech.roundtrip2.ServiceData.ReadPumpClockResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceResult;
import com.gxwtech.roundtrip2.util.ByteUtil;
import com.gxwtech.roundtrip2.util.StringUtil;

import org.joda.time.Duration;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.Instant;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;


/**
 * Created by geoff on 5/30/16.
 */
public class PumpManager {
    private static final String TAG = "PumpManager";
    public double[] scanFrequencies = {916.45, 916.50, 916.55, 916.60, 916.65, 916.70, 916.75, 916.80};
    public static final int startSession_signal = 6656; // arbitrary.
    //private long pumpAwakeUntil = 0;
    private int pumpAwakeForMinutes = 6;
    private final RFSpy rfspy;
    private byte[] pumpID;
    public boolean DEBUG_PUMPMANAGER = true;
    private final Context context;
    private SharedPreferences prefs;
    private Instant lastGoodPumpCommunicationTime = new Instant(0);
    public PumpManager(Context context, RFSpy rfspy, byte[] pumpID) {
        this.context = context;
        this.rfspy = rfspy;
        this.pumpID = pumpID;
        prefs = context.getSharedPreferences(RT2Const.serviceLocal.sharedPreferencesKey, Context.MODE_PRIVATE);
    }

    private PumpMessage runCommandWithArgs(PumpMessage msg) {
        PumpMessage rval;
        PumpMessage shortMessage = makePumpMessage(msg.messageType,new CarelinkShortMessageBody(new byte[]{0}));
        // look for ack from short message
        PumpMessage shortResponse = sendAndListen(shortMessage);
        if (shortResponse.messageType.mtype == MessageType.PumpAck) {
            rval = sendAndListen(msg);
            return rval;
        } else {
            Log.e(TAG,"runCommandWithArgs: Pump did not ack Attention packet");
        }
        return new PumpMessage();
    }

    protected PumpMessage sendAndListen(PumpMessage msg) {
        return sendAndListen(msg,2000);
    }

    // All pump communications go through this function.
    protected PumpMessage sendAndListen(PumpMessage msg, int timeout_ms) {
        boolean showPumpMessages = true;
        if (showPumpMessages) {
            Log.i(TAG,"Sent:"+ByteUtil.shortHexString(msg.getTxData()));
        }
        RFSpyResponse resp = rfspy.transmitThenReceive(new RadioPacket(msg.getTxData()),timeout_ms);
        PumpMessage rval = new PumpMessage(resp.getRadioResponse().getPayload());
        if (rval.isValid()) {
            // Mark this as the last time we heard from the pump.
            rememberLastGoodPumpCommunicationTime();
        }
        if (showPumpMessages) {
            Log.i(TAG,"Received:"+ByteUtil.shortHexString(resp.getRadioResponse().getPayload()));
        }
        return rval;
    }

    public Page getPumpHistoryPage(int pageNumber) {
        RawHistoryPage rval = new RawHistoryPage();
        wakeup(pumpAwakeForMinutes);
        PumpMessage getHistoryMsg = makePumpMessage(new MessageType(MessageType.CMD_M_READ_HISTORY), new GetHistoryPageCarelinkMessageBody(pageNumber));
        //Log.i(TAG,"getPumpHistoryPage("+pageNumber+"): "+ByteUtil.shortHexString(getHistoryMsg.getTxData()));
        // Ask the pump to transfer history (we get first frame?)
        PumpMessage firstResponse = runCommandWithArgs(getHistoryMsg);
        //Log.i(TAG,"getPumpHistoryPage("+pageNumber+"): " + ByteUtil.shortHexString(firstResponse.getContents()));

        PumpMessage ackMsg = makePumpMessage(MessageType.PumpAck,new PumpAckMessageBody());
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
                    Log.w(TAG,"Expected frame of length 64, got frame of length " + frameData.length);
                    // but append it anyway?
                }
                // handle successful frame data
                rval.appendData(currentResponse.getFrameData());
                RoundtripService.getInstance().announceProgress(((100/16) * currentResponse.getFrameNumber()+1));
                Log.i(TAG,"getPumpHistoryPage: Got frame "+currentResponse.getFrameNumber());
                // Do we need to ask for the next frame?
                if (expectedFrameNum < 16) { // This number may not be correct for pumps other than 522/722
                    expectedFrameNum++;
                } else {
                    done = true; // successful completion
                }
            } else {
                if (frameData == null) {
                    Log.e(TAG,"null frame data, retrying");
                } else if (currentResponse.getFrameNumber() != expectedFrameNum) {
                    Log.w(TAG, String.format("Expected frame number %d, received %d (retrying)", expectedFrameNum, currentResponse.getFrameNumber()));
                } else if (frameData.length == 0) {
                    Log.w(TAG, "Frame has zero length, retrying");
                }
                failures++;
                if (failures == 6) {
                    Log.e(TAG,String.format("6 failures in attempting to download frame %d of page %d, giving up.",expectedFrameNum,pageNumber));
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
            Log.w(TAG,"getPumpHistoryPage: short page.  Expected length of 1024, found length of "+rval.getLength());
        }
        if (rval.isChecksumOK() == false) {
            Log.e(TAG,"getPumpHistoryPage: checksum is wrong");
        }

        rval.dumpToDebug();

        Page page = new Page();
        //page.parseFrom(rval.getData(),PumpModel.MM522);
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
                    Log.i(TAG, "Found record: (" + r.getClass().getSimpleName() + ") " + timestamp.toString());
                }
            }
        }
        return pages;
    }

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
            Log.e(TAG,String.format("parsePumpRTCBytes: Failed to parse pump time value: year=%d, month=%d, hours=%d, minutes=%d, seconds=%d",year,month,day,hours,minutes,seconds));
            return null;
        }
    }

    public ReadPumpClockResult getPumpRTC() {
        ReadPumpClockResult rval = new ReadPumpClockResult();
        wakeup(pumpAwakeForMinutes);
        PumpMessage getRTCMsg = makePumpMessage(new MessageType(MessageType.CMD_M_READ_RTC), new CarelinkShortMessageBody(new byte[]{0}));
        Log.i(TAG,"getPumpRTC: " + ByteUtil.shortHexString(getRTCMsg.getTxData()));
        PumpMessage response = sendAndListen(getRTCMsg);
        if (response.isValid()) {
            byte[] receivedData = response.getContents();
            if (receivedData != null) {
                if (receivedData.length >= 9) {
                    LocalDateTime pumpTime = parsePumpRTCBytes(ByteUtil.substring(receivedData, 2, 7));
                    if (pumpTime != null) {
                        rval.setTime(pumpTime);
                        rval.setResultOK();
                    } else {
                        rval.setResultError(ServiceResult.ERROR_MALFORMED_PUMP_RESPONSE);
                    }
                } else {
                    rval.setResultError(ServiceResult.ERROR_MALFORMED_PUMP_RESPONSE);
                }
            } else {
                rval.setResultError(ServiceResult.ERROR_MALFORMED_PUMP_RESPONSE);
            }
        } else {
            rval.setResultError(ServiceResult.ERROR_INVALID_PUMP_RESPONSE);
        }
        return rval;
    }

    public PumpModel getPumpModel() {
        wakeup(pumpAwakeForMinutes);
        PumpMessage msg = makePumpMessage(new MessageType(MessageType.GetPumpModel), new GetPumpModelCarelinkMessageBody());
        Log.i(TAG,"getPumpModel: " + ByteUtil.shortHexString(msg.getTxData()));
        PumpMessage response = sendAndListen(msg);
        Log.i(TAG,"getPumpModel response: " + ByteUtil.shortHexString(response.getContents()));
        byte[] contents = response.getContents();
        PumpModel rval = PumpModel.UNSET;
        if (contents != null) {
            if (contents.length >= 7) {
                rval = PumpModel.fromString(StringUtil.fromBytes(ByteUtil.substring(contents,3,3)));
            } else {
                Log.w(TAG,"getPumpModel: Cannot return pump model number: data is too short.");
            }
        } else {
            Log.w(TAG,"getPumpModel: Cannot return pump model number: null response");
        }

        return rval;
    }


    public ISFTable getPumpISFProfile() {
        wakeup(pumpAwakeForMinutes);
        PumpMessage getISFProfileMessage = makePumpMessage(new MessageType(MessageType.GetISFProfile),new CarelinkShortMessageBody());
        PumpMessage resp = sendAndListen(getISFProfileMessage);
        ISFTable table = new ISFTable();
        table.parseFrom(resp.getContents());
        return table;
    }

    public PumpMessage getBolusWizardCarbProfile() {
        wakeup(pumpAwakeForMinutes);
        PumpMessage getCarbProfileMessage = makePumpMessage(new MessageType(MessageType.CMD_M_READ_CARB_RATIOS),new CarelinkShortMessageBody());
        PumpMessage resp = sendAndListen(getCarbProfileMessage);
        return resp;
    }

    public void tryoutPacket(byte[] pkt) {
        sendAndListen(makePumpMessage(pkt));
    }

    public void hunt() {
        tryoutPacket(new byte[] {MessageType.CMD_M_READ_PUMP_STATUS,0});
        tryoutPacket(new byte[] {MessageType.CMD_M_READ_FIRMWARE_VER,0});
        tryoutPacket(new byte[] {MessageType.CMD_M_READ_INSULIN_REMAINING,0});

    }

    // See ButtonPressCarelinkMessageBody
    public void pressButton(int which) {
        wakeup(pumpAwakeForMinutes);
        PumpMessage pressButtonMessage = makePumpMessage(new MessageType(MessageType.CMD_M_KEYPAD_PUSH),new ButtonPressCarelinkMessageBody(which));
        PumpMessage resp = sendAndListen(pressButtonMessage);
        if (resp.messageType.mtype != MessageType.PumpAck) {
            Log.e(TAG,"Pump did not ack button press.");
        }
    }

    public void wakeup(int duration_minutes) {
        // If it has been longer than n minutes, do wakeup.  Otherwise assume pump is still awake.
        // **** FIXME: this wakeup doesn't seem to work well... must revisit
        pumpAwakeForMinutes = duration_minutes;
        Instant lastGood = getLastGoodPumpCommunicationTime();
//        Instant lastGoodPlus = lastGood.plus(new Duration(pumpAwakeForMinutes * 60 * 1000));
        Instant lastGoodPlus = lastGood.plus(new Duration(1 * 60 * 1000));
        Instant now = Instant.now();
        if (now.compareTo(lastGoodPlus) > 0) {
            Log.i(TAG,"Waking pump...");
            PumpMessage msg = makePumpMessage(new MessageType(MessageType.PowerOn), new CarelinkShortMessageBody(new byte[]{(byte) duration_minutes}));
            RFSpyResponse resp = rfspy.transmitThenReceive(new RadioPacket(msg.getTxData()), (byte) 0, (byte) 200, (byte) 0, (byte) 0, 15000, (byte) 0);
            Log.i(TAG, "wakeup: raw response is " + ByteUtil.shortHexString(resp.getRaw()));
        } else {
            Log.v(TAG,"Last pump communication was recent, not waking pump.");
        }
    }

    public void setRadioFrequencyForPump(double freqMHz) {
        rfspy.setBaseFrequency(freqMHz);
    }

    public double tuneForPump() {
        return scanForPump(scanFrequencies);
    }

    private int tune_tryFrequency(double freqMHz) {
        rfspy.setBaseFrequency(freqMHz);
        PumpMessage msg = makePumpMessage(new MessageType(MessageType.GetPumpModel),new GetPumpModelCarelinkMessageBody());
        RadioPacket pkt = new RadioPacket(msg.getTxData());
        RFSpyResponse resp = rfspy.transmitThenReceive(pkt,(byte)0,(byte)0,(byte)0,(byte)0,rfspy.EXPECTED_MAX_BLUETOOTH_LATENCY_MS,(byte)0);
        if (resp.wasTimeout()) {
            Log.w(TAG,String.format("tune_tryFrequency: no pump response at frequency %.2f",freqMHz));
        } else if (resp.looksLikeRadioPacket()) {
            RadioResponse radioResponse = new RadioResponse(resp.getRaw());
            if (radioResponse.isValid()) {
                Log.w(TAG,String.format("tune_tryFrequency: saw response level %d at frequency %.2f",radioResponse.rssi,freqMHz));
                return radioResponse.rssi;
            } else {
                Log.w(TAG,"tune_tryFrequency: invalid radio response:"+ByteUtil.shortHexString(radioResponse.getPayload()));
            }
        }
        return 0;
    }

    public double quickTuneForPump(double startFrequencyMHz) {
        double betterFrequency = startFrequencyMHz;
        double stepsize = 0.05;
        for (int tries = 0; tries < 4; tries++) {
            double evenBetterFrequency = quickTunePumpStep(betterFrequency, stepsize);
            if (evenBetterFrequency == 0.0) {
                // could not see the pump at all.
                // Try again at larger step size
                stepsize += 0.05;
            } else {
                if ((int)(evenBetterFrequency * 100) == (int)(betterFrequency * 100)) {
                    // value did not change, so we're done.
                    break;
                }
                betterFrequency = evenBetterFrequency; // and go again.
            }
        }
        if (betterFrequency == 0.0) {
            // we've failed... caller should try a full scan for pump
            Log.e(TAG,"quickTuneForPump: failed to find pump");
        } else {
            rfspy.setBaseFrequency(betterFrequency);
            if (betterFrequency != startFrequencyMHz) {
                Log.i(TAG, String.format("quickTuneForPump: new frequency is %.2fMHz", betterFrequency));
            } else {
                Log.i(TAG, String.format("quickTuneForPump: pump frequency is the same: %.2fMHz", startFrequencyMHz));
            }
        }
        return betterFrequency;
    }

    private double quickTunePumpStep(double startFrequencyMHz, double stepSizeMHz) {
        Log.i(TAG,"Doing quick radio tune for pump ID " + pumpID);
        wakeup(pumpAwakeForMinutes);
        int startRssi = tune_tryFrequency(startFrequencyMHz);
        double lowerFrequency = startFrequencyMHz - stepSizeMHz;
        int lowerRssi = tune_tryFrequency(lowerFrequency);
        double higherFrequency = startFrequencyMHz + stepSizeMHz;
        int higherRssi = tune_tryFrequency(higherFrequency);
        if ((higherRssi == 0.0) && (lowerRssi == 0.0) && (startRssi == 0.0)) {
            // we can't see the pump at all...
            return 0.0;
        }
        if (higherRssi > startRssi) {
            // need to move higher
            return higherFrequency;
        } else if (lowerRssi > startRssi) {
            // need to move lower.
            return lowerFrequency;
        }
        return startFrequencyMHz;
    }

    private double scanForPump(double[] frequencies) {
        Log.i(TAG,"Scanning for pump ID " + pumpID);
        wakeup(pumpAwakeForMinutes);
        FrequencyScanResults results = new FrequencyScanResults();

        for (int i=0; i<frequencies.length; i++) {
            int tries = 3;
            FrequencyTrial trial = new FrequencyTrial();
            trial.frequencyMHz = frequencies[i];
            rfspy.setBaseFrequency(frequencies[i]);
            int sumRSSI = 0;
            for (int j = 0; j<tries; j++) {
                PumpMessage msg = makePumpMessage(new MessageType(MessageType.GetPumpModel), new GetPumpModelCarelinkMessageBody());
                RFSpyResponse resp = rfspy.transmitThenReceive(new RadioPacket(msg.getTxData()),(byte) 0, (byte) 0, (byte) 0, (byte) 0, rfspy.EXPECTED_MAX_BLUETOOTH_LATENCY_MS, (byte) 0);
                if (resp.wasTimeout()) {
                    Log.e(TAG, String.format("scanForPump: Failed to find pump at frequency %.2f", frequencies[i]));
                } else if (resp.looksLikeRadioPacket()) {
                    RadioResponse radioResponse = new RadioResponse(resp.getRaw());
                    if (radioResponse.isValid()) {
                        sumRSSI += radioResponse.rssi;
                        trial.successes++;
                    } else {
                        Log.w(TAG,"Failed to parse radio response: " + ByteUtil.shortHexString(resp.getRaw()));
                    }
                } else {
                    Log.e(TAG, "scanForPump: raw response is " + ByteUtil.shortHexString(resp.getRaw()));
                }
                trial.tries++;
            }
            sumRSSI += -99.0 * (trial.tries - trial.successes);
            trial.averageRSSI = (double)(sumRSSI) / (double)(trial.tries);
            results.trials.add(trial);
        }
        results.sort(); // sorts in ascending order
        Log.d(TAG,"Sorted scan results:");
        for (int k=0; k<results.trials.size(); k++) {
            FrequencyTrial one = results.trials.get(k);
            Log.d(TAG,String.format("Scan Result[%d]: Freq=%.2f, avg RSSI = %f",k,one.frequencyMHz, one.averageRSSI));
        }
        FrequencyTrial bestTrial = results.trials.get(results.trials.size()-1);
        results.bestFrequencyMHz = bestTrial.frequencyMHz;
        if (bestTrial.successes > 0) {
            rfspy.setBaseFrequency(results.bestFrequencyMHz);
            return results.bestFrequencyMHz;
        } else {
            Log.e(TAG,"No pump response during scan.");
            return 0.0;
        }
    }

    private PumpMessage makePumpMessage(MessageType messageType, MessageBody messageBody) {
        PumpMessage msg = new PumpMessage();
        msg.init(new PacketType(PacketType.Carelink),pumpID,messageType,messageBody);
        return msg;
    }

    private PumpMessage makePumpMessage(byte msgType, MessageBody body) {
        return makePumpMessage(new MessageType(msgType),body);
    }

    private PumpMessage makePumpMessage(byte[] typeAndBody) {
        PumpMessage msg = new PumpMessage();
        msg.init(ByteUtil.concat(ByteUtil.concat(new byte[]{(byte)0xa7},pumpID),typeAndBody));
        return msg;
    }

    private void rememberLastGoodPumpCommunicationTime() {
        lastGoodPumpCommunicationTime = Instant.now();
        SharedPreferences.Editor ed = prefs.edit();
        ed.putLong("lastGoodPumpCommunicationTime",lastGoodPumpCommunicationTime.getMillis());
        ed.commit();
    }

    private Instant getLastGoodPumpCommunicationTime() {
        // If we have a value of zero, we need to load from prefs.
        if (lastGoodPumpCommunicationTime.getMillis() == new Instant(0).getMillis()) {
            lastGoodPumpCommunicationTime = new Instant(prefs.getLong("lastGoodPumpCommunicationTime",0));
            // Might still be zero, but that's fine.
        }
        double minutesAgo = (Instant.now().getMillis() - lastGoodPumpCommunicationTime.getMillis()) / (1000.0 * 60.0);
        Log.v(TAG,"Last good pump communication was " + minutesAgo + " minutes ago.");
        return lastGoodPumpCommunicationTime;
    }

    public PumpMessage getRemainingBattery() {
        PumpMessage getBatteryMsg = makePumpMessage(new MessageType(MessageType.GetBattery),new CarelinkShortMessageBody());
        PumpMessage resp = sendAndListen(getBatteryMsg);
        return resp;
    }

    public PumpMessage getRemainingInsulin() {
        PumpMessage msg = makePumpMessage(new MessageType(MessageType.CMD_M_READ_INSULIN_REMAINING),new CarelinkShortMessageBody());
        PumpMessage resp = sendAndListen(msg);
        return resp;
    }

    public PumpMessage getCurrentBasalRate() {
        PumpMessage msg = makePumpMessage(new MessageType(MessageType.ReadTempBasal), new CarelinkShortMessageBody());
        PumpMessage resp = sendAndListen(msg);
        return resp;
    }

    PumpManagerStatus pumpManagerStatus = new PumpManagerStatus();

    public PumpManagerStatus getPumpManagerStatus() { return pumpManagerStatus; }

    public void updatePumpManagerStatus() {
        PumpMessage resp = getRemainingBattery();
        if (resp.isValid()) {
            byte[] remainingBatteryBytes = resp.getContents();
            if (remainingBatteryBytes != null) {
                if (remainingBatteryBytes.length == 5) {
                    /**
                     * 0x72 0x03, 0x00, 0x00, 0x82
                     * meaning what ????
                     */
                    pumpManagerStatus.remainBattery = ByteUtil.asUINT8(remainingBatteryBytes[5]);
                }
            }
        }
        resp = getRemainingInsulin();
        byte[] insulinRemainingBytes = resp.getContents();
        if (insulinRemainingBytes != null) {
            if (insulinRemainingBytes.length == 4) {
                /* 0x73 0x02 0x05 0xd2
                * the 0xd2 (210) represents 21 units remaining.
                */
                double insulinUnitsRemaining = ByteUtil.asUINT8(insulinRemainingBytes[3]) / 10.0;
                pumpManagerStatus.remainUnits = insulinUnitsRemaining;
            }
        }
        /* current basal */
        resp = getCurrentBasalRate();
        byte[] basalRateBytes = resp.getContents();
        if (basalRateBytes != null) {
            if (basalRateBytes.length == 2) {
                /**
                 * 0x98 0x06
                 * 0x98 is "basal rate"
                 * 0x06 is what? Not currently running a temp basal, current basal is "standard" at 0
                 */
                double basalRate = ByteUtil.asUINT8(basalRateBytes[1]);
                pumpManagerStatus.currentBasal = basalRate;
            }
        }
        // get last bolus amount
        // get last bolus time
        // get tempBasalInProgress
        // get tempBasalRatio
        // get tempBasalRemainMin
        // get tempBasalStart
        // get pump time
        ReadPumpClockResult clockResult = getPumpRTC();
        if (clockResult.resultIsOK()) {
            pumpManagerStatus.time = clockResult.getTime().toDate();
        }
        // get last sync time

    }

    public void testPageDecode() {
        byte[] raw = new byte[] {(byte)0x6D, (byte)0x62, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x63, (byte)0x10, (byte)0x6D, (byte)0x63, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01,
                (byte)0x01, (byte)0x01, (byte)0x00, (byte)0x5A, (byte)0xA5, (byte)0x49, (byte)0x04, (byte)0x10, (byte)0x01, (byte)0x01, (byte)0x01, (byte)0x00, (byte)0x6D, (byte)0xA5, (byte)0x49, (byte)0x04, (byte)0x10, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x08, (byte)0x64, (byte)0x10, (byte)0x6D, (byte)0x64, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x08, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x08, (byte)0x64, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x08, (byte)0x64, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x08, (byte)0x64, (byte)0x02, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x02, (byte)0x0C, (byte)0x00,
                (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x64, (byte)0x01, (byte)0x75, (byte)0x94, (byte)0x0D, (byte)0x05, (byte)0x10, (byte)0x64, (byte)0x01, (byte)0x44, (byte)0x95, (byte)0x0D, (byte)0x05, (byte)0x10, (byte)0x17, (byte)0x00, (byte)0x4E, (byte)0x95, (byte)0x0D, (byte)0x05, (byte)0x10, (byte)0x18, (byte)0x00, (byte)0x40, (byte)0x95, (byte)0x0D, (byte)0x05, (byte)0x10,
                (byte)0x19, (byte)0x00, (byte)0x40, (byte)0x81, (byte)0x15, (byte)0x05, (byte)0x10, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x65, (byte)0x10, (byte)0x6D, (byte)0x65, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x1A, (byte)0x00, (byte)0x47, (byte)0x82, (byte)0x09, (byte)0x06,
                (byte)0x10, (byte)0x1A, (byte)0x01, (byte)0x5C, (byte)0x82, (byte)0x09, (byte)0x06, (byte)0x10, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x66, (byte)0x10, (byte)0x6D, (byte)0x66, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x67, (byte)0x10, (byte)0x6D, (byte)0x67, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x68, (byte)0x10, (byte)0x6D, (byte)0x68, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x69, (byte)0x10, (byte)0x6D, (byte)0x69, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x6A, (byte)0x10, (byte)0x6D, (byte)0x6A, (byte)0x10, (byte)0x05, (byte)0x0C,
                (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x6B, (byte)0x10, (byte)0x6D, (byte)0x6B, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x6C,
                (byte)0x10, (byte)0x6D, (byte)0x6C, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x6D, (byte)0x10, (byte)0x6D, (byte)0x6D, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x6E, (byte)0x10, (byte)0x6D, (byte)0x6E, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x6F, (byte)0x10, (byte)0x6D, (byte)0x6F, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00,
                (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x19, (byte)0x00, (byte)0x40, (byte)0x81, (byte)0x03, (byte)0x10, (byte)0x10, (byte)0x1A, (byte)0x00, (byte)0x68, (byte)0x96, (byte)0x0A, (byte)0x10, (byte)0x10, (byte)0x1A, (byte)0x01, (byte)0x40, (byte)0x97, (byte)0x0A, (byte)0x10, (byte)0x10, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x70, (byte)0x10, (byte)0x6D, (byte)0x70, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x71, (byte)0x10, (byte)0x6D, (byte)0x71, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x72, (byte)0x10, (byte)0x6D, (byte)0x72, (byte)0x10, (byte)0x05, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x73, (byte)0x10, (byte)0x6D, (byte)0x73, (byte)0x10, (byte)0x05, (byte)0x0C,
                (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x0C, (byte)0x00, (byte)0xE8, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x07, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x74, (byte)0x10, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x2C, (byte)0x79,
        };
        Page page = new Page();
        page.parseFrom(raw,PumpModel.MM522);
        page.parseByDates(raw,PumpModel.MM522);
        page.parsePicky(raw,PumpModel.MM522);
        Log.i(TAG,"testPageDecode: done");
    }

}

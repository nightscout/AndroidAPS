package info.nightscout.androidaps.plugins.pump.common.hw.rileylink;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDeviceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RFSpy;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.FrequencyScanResults;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.FrequencyTrial;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RFSpyResponse;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RLMessage;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioPacket;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data.RadioResponse;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RLMessageType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * This is abstract class for RileyLink Communication, this one needs to be extended by specific "Pump" class.
 * <p>
 * Created by andy on 5/10/18.
 */
public abstract class RileyLinkCommunicationManager<T extends RLMessage> {
    @Inject protected AAPSLogger aapsLogger;
    @Inject protected SP sp;
    @Inject protected RileyLinkServiceData rileyLinkServiceData;
    @Inject protected ServiceTaskExecutor serviceTaskExecutor;
    @Inject protected RFSpy rfspy;
    @Inject protected HasAndroidInjector injector;
    @Inject protected ActivePluginProvider activePluginProvider;

    private final int SCAN_TIMEOUT = 1500;
    private final int ALLOWED_PUMP_UNREACHABLE = 10 * 60 * 1000; // 10 minutes

    protected int receiverDeviceAwakeForMinutes = 1; // override this in constructor of specific implementation
    protected String receiverDeviceID; // String representation of receiver device (ex. Pump (xxxxxx) or Pod (yyyyyy))
    protected long lastGoodReceiverCommunicationTime = 0;
    private long nextWakeUpRequired = 0L;
    private int timeoutCount = 0;


    // All pump communications go through this function.
    protected T sendAndListen(T msg, int timeout_ms)
            throws RileyLinkCommunicationException {
        return sendAndListen(msg, timeout_ms, null);
    }

    private T sendAndListen(T msg, int timeout_ms, Integer extendPreamble_ms)
            throws RileyLinkCommunicationException {
        return sendAndListen(msg, timeout_ms, 0, extendPreamble_ms);
    }

    // For backward compatibility
    private T sendAndListen(T msg, int timeout_ms, int repeatCount, Integer extendPreamble_ms)
            throws RileyLinkCommunicationException {
        return sendAndListen(msg, timeout_ms, repeatCount, 0, extendPreamble_ms);
    }

    protected T sendAndListen(T msg, int timeout_ms, int repeatCount, int retryCount, Integer extendPreamble_ms)
            throws RileyLinkCommunicationException {

        // internal flag
        boolean showPumpMessages = true;
        if (showPumpMessages) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Sent:" + ByteUtil.shortHexString(msg.getTxData()));
        }

        RFSpyResponse rfSpyResponse = rfspy.transmitThenReceive(new RadioPacket(injector, msg.getTxData()),
                (byte) 0, (byte) repeatCount, (byte) 0, (byte) 0, timeout_ms, (byte) retryCount, extendPreamble_ms);

        RadioResponse radioResponse = rfSpyResponse.getRadioResponse(injector);
        T response = createResponseMessage(radioResponse.getPayload());

        if (response.isValid()) {
            // Mark this as the last time we heard from the pump.
            rememberLastGoodDeviceCommunicationTime();
        } else {
            aapsLogger.warn(LTag.PUMPBTCOMM, "isDeviceReachable. Response is invalid ! [noResponseFromRileyLink={}, interrupted={}, timeout={}, unknownCommand={}, invalidParam={}]",
                    rfSpyResponse.wasNoResponseFromRileyLink(), rfSpyResponse.wasInterrupted(), rfSpyResponse.wasTimeout(), rfSpyResponse.isUnknownCommand(), rfSpyResponse.isInvalidParam());

            if (rfSpyResponse.wasTimeout()) {
                if (rileyLinkServiceData.targetDevice.isTuneUpEnabled()) {
                    timeoutCount++;

                    long diff = System.currentTimeMillis() - getPumpDevice().getLastConnectionTimeMillis();

                    if (diff > ALLOWED_PUMP_UNREACHABLE) {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "We reached max time that Pump can be unreachable. Starting Tuning.");
                        serviceTaskExecutor.startTask(new WakeAndTuneTask(injector));
                        timeoutCount = 0;
                    }
                }

                throw new RileyLinkCommunicationException(RileyLinkBLEError.Timeout);
            } else if (rfSpyResponse.wasInterrupted()) {
                throw new RileyLinkCommunicationException(RileyLinkBLEError.Interrupted);
            } else if (rfSpyResponse.wasNoResponseFromRileyLink()) {
                throw new RileyLinkCommunicationException(RileyLinkBLEError.NoResponse);
            }
        }

        if (showPumpMessages) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Received:" + ByteUtil.shortHexString(rfSpyResponse.getRadioResponse(injector).getPayload()));
        }

        return response;
    }


    public abstract T createResponseMessage(byte[] payload);


    public abstract void setPumpDeviceState(PumpDeviceState pumpDeviceState);


    public void wakeUp(boolean force) {
        wakeUp(receiverDeviceAwakeForMinutes, force);
    }


    public int getNotConnectedCount() {
        return rfspy != null ? rfspy.notConnectedCount : 0;
    }


    // FIXME change wakeup
    // TODO we might need to fix this. Maybe make pump awake for shorter time (battery factor for pump) - Andy
    public void wakeUp(int duration_minutes, boolean force) {
        // If it has been longer than n minutes, do wakeup. Otherwise assume pump is still awake.
        // **** FIXME: this wakeup doesn't seem to work well... must revisit
        // receiverDeviceAwakeForMinutes = duration_minutes;

        setPumpDeviceState(PumpDeviceState.WakingUp);

        if (force)
            nextWakeUpRequired = 0L;

        if (System.currentTimeMillis() > nextWakeUpRequired) {
            aapsLogger.info(LTag.PUMPBTCOMM, "Waking pump...");

            byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData); // simple
            RFSpyResponse resp = rfspy.transmitThenReceive(new RadioPacket(injector, pumpMsgContent), (byte) 0, (byte) 200,
                    (byte) 0, (byte) 0, 25000, (byte) 0);
            aapsLogger.info(LTag.PUMPBTCOMM, "wakeup: raw response is " + ByteUtil.shortHexString(resp.getRaw()));

            // FIXME wakeUp successful !!!!!!!!!!!!!!!!!!

            nextWakeUpRequired = System.currentTimeMillis() + (receiverDeviceAwakeForMinutes * 60 * 1000);
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Last pump communication was recent, not waking pump.");
        }

        // long lastGoodPlus = getLastGoodReceiverCommunicationTime() + (receiverDeviceAwakeForMinutes * 60 * 1000);
        //
        // if (System.currentTimeMillis() > lastGoodPlus || force) {
        // LOG.info("Waking pump...");
        //
        // byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.PowerOn);
        // RFSpyResponse resp = rfspy.transmitThenReceive(new RadioPacket(pumpMsgContent), (byte) 0, (byte) 200, (byte)
        // 0, (byte) 0, 15000, (byte) 0);
        // LOG.info("wakeup: raw response is " + ByteUtil.shortHexString(resp.getRaw()));
        // } else {
        // LOG.trace("Last pump communication was recent, not waking pump.");
        // }
    }


    public void setRadioFrequencyForPump(double freqMHz) {
        rfspy.setBaseFrequency(freqMHz);
    }


    public double tuneForDevice() {
        return scanForDevice(rileyLinkServiceData.rileyLinkTargetFrequency.getScanFrequencies());
    }


    /**
     * If user changes pump and one pump is running in US freq, and other in WW, then previously set frequency would be
     * invalid,
     * so we would need to retune. This checks that saved frequency is correct range.
     *
     * @param frequency
     * @return
     */
    public boolean isValidFrequency(double frequency) {

        double[] scanFrequencies = rileyLinkServiceData.rileyLinkTargetFrequency.getScanFrequencies();

        if (scanFrequencies.length == 1) {
            return Round.isSame(scanFrequencies[0], frequency);
        } else {
            return (scanFrequencies[0] <= frequency && scanFrequencies[scanFrequencies.length - 1] >= frequency);
        }
    }


    /**
     * Do device connection, with wakeup
     *
     * @return
     */
    public abstract boolean tryToConnectToDevice();


    private double scanForDevice(double[] frequencies) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Scanning for receiver ({})", receiverDeviceID);
        wakeUp(receiverDeviceAwakeForMinutes, false);
        FrequencyScanResults results = new FrequencyScanResults();

        for (int i = 0; i < frequencies.length; i++) {
            int tries = 3;
            FrequencyTrial trial = new FrequencyTrial();
            trial.frequencyMHz = frequencies[i];
            rfspy.setBaseFrequency(frequencies[i]);

            int sumRSSI = 0;
            for (int j = 0; j < tries; j++) {

                byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData);
                RFSpyResponse resp = rfspy.transmitThenReceive(new RadioPacket(injector, pumpMsgContent), (byte) 0, (byte) 0,
                        (byte) 0, (byte) 0, 1250, (byte) 0);
                if (resp.wasTimeout()) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "scanForPump: Failed to find pump at frequency {}", frequencies[i]);
                } else if (resp.looksLikeRadioPacket()) {
                    RadioResponse radioResponse = new RadioResponse(injector);

                    try {

                        radioResponse.init(resp.getRaw());

                        if (radioResponse.isValid()) {
                            int rssi = calculateRssi(radioResponse.rssi);
                            sumRSSI += rssi;
                            trial.rssiList.add(rssi);
                            trial.successes++;
                        } else {
                            aapsLogger.warn(LTag.PUMPBTCOMM, "Failed to parse radio response: " + ByteUtil.shortHexString(resp.getRaw()));
                            trial.rssiList.add(-99);
                        }

                    } catch (RileyLinkCommunicationException rle) {
                        aapsLogger.warn(LTag.PUMPBTCOMM, "Failed to decode radio response: " + ByteUtil.shortHexString(resp.getRaw()));
                        trial.rssiList.add(-99);
                    }

                } else {
                    aapsLogger.error(LTag.PUMPBTCOMM, "scanForPump: raw response is " + ByteUtil.shortHexString(resp.getRaw()));
                    trial.rssiList.add(-99);
                }
                trial.tries++;
            }
            sumRSSI += -99.0 * (trial.tries - trial.successes);
            trial.averageRSSI2 = (double) (sumRSSI) / (double) (trial.tries);

            trial.calculateAverage();

            results.trials.add(trial);
        }

        results.dateTime = System.currentTimeMillis();

        StringBuilder stringBuilder = new StringBuilder("Scan results:\n");

        for (int k = 0; k < results.trials.size(); k++) {
            FrequencyTrial one = results.trials.get(k);

            stringBuilder.append(String.format("Scan Result[%s]: Freq=%s, avg RSSI = %s\n", "" + k, ""
                    + one.frequencyMHz, "" + one.averageRSSI + ", RSSIs =" + one.rssiList));
        }

        aapsLogger.info(LTag.PUMPBTCOMM, stringBuilder.toString());

        results.sort(); // sorts in ascending order

        FrequencyTrial bestTrial = results.trials.get(results.trials.size() - 1);
        results.bestFrequencyMHz = bestTrial.frequencyMHz;
        if (bestTrial.successes > 0) {
            rfspy.setBaseFrequency(results.bestFrequencyMHz);
            aapsLogger.debug(LTag.PUMPBTCOMM, "Best frequency found: " + results.bestFrequencyMHz);
            return results.bestFrequencyMHz;
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "No pump response during scan.");
            return 0.0;
        }
    }


    private int calculateRssi(int rssiIn) {
        int rssiOffset = 73;
        int outRssi = 0;
        if (rssiIn >= 128) {
            outRssi = ((rssiIn - 256) / 2) - rssiOffset;
        } else {
            outRssi = (rssiIn / 2) - rssiOffset;
        }

        return outRssi;
    }


    public abstract byte[] createPumpMessageContent(RLMessageType type);


    private int tune_tryFrequency(double freqMHz) {
        rfspy.setBaseFrequency(freqMHz);
        // T msg = makeRLMessage(RLMessageType.ReadSimpleData);
        byte[] pumpMsgContent = createPumpMessageContent(RLMessageType.ReadSimpleData);
        RadioPacket pkt = new RadioPacket(injector, pumpMsgContent);
        RFSpyResponse resp = rfspy.transmitThenReceive(pkt, (byte) 0, (byte) 0, (byte) 0, (byte) 0, SCAN_TIMEOUT, (byte) 0);
        if (resp.wasTimeout()) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "tune_tryFrequency: no pump response at frequency {}", freqMHz);
        } else if (resp.looksLikeRadioPacket()) {
            RadioResponse radioResponse = new RadioResponse(injector);
            try {
                radioResponse.init(resp.getRaw());

                if (radioResponse.isValid()) {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "tune_tryFrequency: saw response level {} at frequency {}", radioResponse.rssi, freqMHz);
                    return calculateRssi(radioResponse.rssi);
                } else {
                    aapsLogger.warn(LTag.PUMPBTCOMM, "tune_tryFrequency: invalid radio response:"
                            + ByteUtil.shortHexString(radioResponse.getPayload()));
                }

            } catch (RileyLinkCommunicationException e) {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Failed to decode radio response: " + ByteUtil.shortHexString(resp.getRaw()));
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
                if ((int) (evenBetterFrequency * 100) == (int) (betterFrequency * 100)) {
                    // value did not change, so we're done.
                    break;
                }
                betterFrequency = evenBetterFrequency; // and go again.
            }
        }
        if (betterFrequency == 0.0) {
            // we've failed... caller should try a full scan for pump
            aapsLogger.error(LTag.PUMPBTCOMM, "quickTuneForPump: failed to find pump");
        } else {
            rfspy.setBaseFrequency(betterFrequency);
            if (betterFrequency != startFrequencyMHz) {
                aapsLogger.info(LTag.PUMPBTCOMM, "quickTuneForPump: new frequency is {}MHz", betterFrequency);
            } else {
                aapsLogger.info(LTag.PUMPBTCOMM, "quickTuneForPump: pump frequency is the same: {}MHz", startFrequencyMHz);
            }
        }
        return betterFrequency;
    }


    private double quickTunePumpStep(double startFrequencyMHz, double stepSizeMHz) {
        aapsLogger.info(LTag.PUMPBTCOMM, "Doing quick radio tune for receiver ({})", receiverDeviceID);
        wakeUp(false);
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


    protected void rememberLastGoodDeviceCommunicationTime() {
        lastGoodReceiverCommunicationTime = System.currentTimeMillis();

        sp.putLong(RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, lastGoodReceiverCommunicationTime);

        getPumpDevice().setLastCommunicationToNow();
    }


    private long getLastGoodReceiverCommunicationTime() {
        // If we have a value of zero, we need to load from prefs.
        if (lastGoodReceiverCommunicationTime == 0L) {
            lastGoodReceiverCommunicationTime = sp.getLong(RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
            // Might still be zero, but that's fine.
        }
        double minutesAgo = (System.currentTimeMillis() - lastGoodReceiverCommunicationTime) / (1000.0 * 60.0);
        aapsLogger.debug(LTag.PUMPBTCOMM, "Last good pump communication was " + minutesAgo + " minutes ago.");
        return lastGoodReceiverCommunicationTime;
    }

    public void clearNotConnectedCount() {
        if (rfspy != null) {
            rfspy.notConnectedCount = 0;
        }
    }

    private RileyLinkPumpDevice getPumpDevice() {
        return (RileyLinkPumpDevice) activePluginProvider.getActivePump();
    }

    public abstract boolean isDeviceReachable();
}

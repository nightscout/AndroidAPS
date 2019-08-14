package info.nightscout.androidaps.plugins.pump.omnipod.defs.state;

import com.google.gson.Gson;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FirmwareVersion;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.NonceState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmniCRC;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.SP;

public class PodSessionState extends PodState {
    private final Map<AlertSlot, AlertType> configuredAlerts;
    private final DateTime activatedAt;
    private final FirmwareVersion piVersion;
    private final FirmwareVersion pmVersion;
    private final int lot;
    private final int tid;
    private boolean suspended;

    private DateTimeZone timeZone;
    private NonceState nonceState;
    private SetupProgress setupProgress;
    private AlertSet activeAlerts;
    private BasalSchedule basalSchedule;
    private DeliveryStatus lastDeliveryStatus;

    public PodSessionState(DateTimeZone timeZone, int address, DateTime activatedAt, FirmwareVersion piVersion,
                           FirmwareVersion pmVersion, int lot, int tid, int packetNumber, int messageNumber) {
        super(address, messageNumber, packetNumber);
        if (timeZone == null) {
            throw new IllegalArgumentException("Time zone can not be null");
        }

        suspended = false;
        configuredAlerts = new HashMap<>();
        configuredAlerts.put(AlertSlot.SLOT7, AlertType.FINISH_SETUP_REMINDER);

        this.timeZone = timeZone;
        this.setupProgress = SetupProgress.ADDRESS_ASSIGNED;
        this.activatedAt = activatedAt;
        this.piVersion = piVersion;
        this.pmVersion = pmVersion;
        this.lot = lot;
        this.tid = tid;
        this.nonceState = new NonceState(lot, tid);
        store();
    }

    public AlertType getConfiguredAlertType(AlertSlot alertSlot) {
        return configuredAlerts.get(alertSlot);
    }

    public void putConfiguredAlert(AlertSlot alertSlot, AlertType alertType) {
        configuredAlerts.put(alertSlot, alertType);
        store();
    }

    public void removeConfiguredAlert(AlertSlot alertSlot) {
        configuredAlerts.remove(alertSlot);
        store();
    }

    public DateTime getActivatedAt() {
        return activatedAt;
    }

    public FirmwareVersion getPiVersion() {
        return piVersion;
    }

    public FirmwareVersion getPmVersion() {
        return pmVersion;
    }

    public int getLot() {
        return lot;
    }

    public int getTid() {
        return tid;
    }

    public synchronized void resyncNonce(int syncWord, int sentNonce, int sequenceNumber) {
        int sum = (sentNonce & 0xFFFF)
                + OmniCRC.crc16lookup[sequenceNumber]
                + (this.lot & 0xFFFF)
                + (this.tid & 0xFFFF);
        int seed = ((sum & 0xFFFF) ^ syncWord);

        this.nonceState = new NonceState(lot, tid, (byte) (seed & 0xFF));
        store();
    }

    public int getCurrentNonce() {
        return nonceState.getCurrentNonce();
    }

    public synchronized void advanceToNextNonce() {
        nonceState.advanceToNextNonce();
        store();
    }

    public SetupProgress getSetupProgress() {
        return setupProgress;
    }

    public synchronized void setSetupProgress(SetupProgress setupProgress) {
        if (setupProgress == null) {
            throw new IllegalArgumentException("Setup state cannot be null");
        }
        this.setupProgress = setupProgress;
        store();
    }

    public boolean isSuspended() {
        return suspended;
    }

    public boolean hasActiveAlerts() {
        return activeAlerts != null;
    }

    public AlertSet getActiveAlerts() {
        return activeAlerts;
    }

    public DateTimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(DateTimeZone timeZone) {
        if (timeZone == null) {
            throw new IllegalArgumentException("Time zone can not be null");
        }
        this.timeZone = timeZone;
        store();
    }

    public DateTime getTime() {
        return DateTime.now().withZone(timeZone);
    }

    public Duration getScheduleOffset() {
        DateTime now = getTime();
        DateTime startOfDay = new DateTime(now.getYear(), now.getMonthOfYear(), now.getDayOfMonth(),
                0, 0, 0, timeZone);
        return new Duration(startOfDay, now);
    }

    public boolean hasNonceState() {
        return true;
    }

    @Override
    public void setPacketNumber(int packetNumber) {
        super.setPacketNumber(packetNumber);
        store();
    }

    @Override
    public void setMessageNumber(int messageNumber) {
        super.setMessageNumber(messageNumber);
        store();
    }

    public BasalSchedule getBasalSchedule() {
        return basalSchedule;
    }

    public void setBasalSchedule(BasalSchedule basalSchedule) {
        this.basalSchedule = basalSchedule;
        store();
    }

    public DeliveryStatus getLastDeliveryStatus() {
        return lastDeliveryStatus;
    }

    @Override
    public void updateFromStatusResponse(StatusResponse statusResponse) {
        suspended = (statusResponse.getDeliveryStatus() == DeliveryStatus.SUSPENDED);
        activeAlerts = statusResponse.getAlerts();
        lastDeliveryStatus = statusResponse.getDeliveryStatus();
        store();
    }

    private void store() {
        Gson gson = OmnipodUtil.getGsonInstance();
        SP.putString(OmnipodConst.Prefs.PodState, gson.toJson(this));
        OmnipodUtil.setPodSessionState(this);
    }

    public String getExpiryDateAsString() {
        // TODO
        return "???";
    }



    @Override
    public String toString() {
        return "PodSessionState{" +
                "activatedAt=" + activatedAt +
                ", piVersion=" + piVersion +
                ", pmVersion=" + pmVersion +
                ", lot=" + lot +
                ", tid=" + tid +
                ", suspended=" + suspended +
                ", timeZone=" + timeZone +
                ", nonceState=" + nonceState +
                ", setupProgress=" + setupProgress +
                ", configuredAlerts=" + configuredAlerts +
                ", activeAlerts=" + activeAlerts +
                ", basalSchedule=" + basalSchedule +
                ", lastDeliveryStatus=" + lastDeliveryStatus +
                ", address=" + address +
                ", packetNumber=" + packetNumber +
                ", messageNumber=" + messageNumber +
                ", faultEvent=" + faultEvent +
                '}';
    }
}

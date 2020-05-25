package info.nightscout.androidaps.plugins.pump.omnipod.defs.state;

import com.google.gson.Gson;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoFaultEvent;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FirmwareVersion;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.NonceState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmniCRC;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.sharedPreferences.SP;


public class PodSessionState extends PodState {

    @Inject transient AAPSLogger aapsLogger;
    @Inject transient SP sp;
    @Inject transient OmnipodUtil omnipodUtil;
    @Inject transient DateUtil dateUtil;

    private transient PodStateChangedHandler stateChangedHandler;

    // TODO
    // the problem you have with injection in this class is that you are mixing
    // data storing and handlind. Move these member variables to extra class you can
    // easy serialize and load it here by setData(dataInOtherClass)
    private final Map<AlertSlot, AlertType> configuredAlerts;
    private DateTimeZone timeZone;
    private DateTime activatedAt;
    private DateTime expiresAt;
    private final FirmwareVersion piVersion;
    private final FirmwareVersion pmVersion;
    private final int lot;
    private final int tid;
    private Double reservoirLevel;
    private boolean suspended;
    private NonceState nonceState;
    private SetupProgress setupProgress;
    private AlertSet activeAlerts;
    private BasalSchedule basalSchedule;
    private DeliveryStatus lastDeliveryStatus;

    public PodSessionState(DateTimeZone timeZone, int address, FirmwareVersion piVersion,
                           FirmwareVersion pmVersion, int lot, int tid, int packetNumber, int messageNumber, HasAndroidInjector injector) {
        super(address, messageNumber, packetNumber);
        injectDaggerClass(injector);
        if (timeZone == null) {
            throw new IllegalArgumentException("Time zone can not be null");
        }

        suspended = false;
        configuredAlerts = new HashMap<>();
        configuredAlerts.put(AlertSlot.SLOT7, AlertType.FINISH_SETUP_REMINDER);

        this.timeZone = timeZone;
        this.setupProgress = SetupProgress.ADDRESS_ASSIGNED;
        this.piVersion = piVersion;
        this.pmVersion = pmVersion;
        this.lot = lot;
        this.tid = tid;
        this.nonceState = new NonceState(lot, tid);
        handleUpdates();
    }

    @Deprecated
    public void injectDaggerClass(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);
    }

    @Deprecated
    public void setStateChangedHandler(PodStateChangedHandler handler) {
        // FIXME this is an ugly workaround for not being able to serialize the PodStateChangedHandler
        if (stateChangedHandler != null) {
            throw new IllegalStateException("A PodStateChangedHandler has already been already registered");
        }
        stateChangedHandler = handler;
    }

    public AlertType getConfiguredAlertType(AlertSlot alertSlot) {
        return configuredAlerts.get(alertSlot);
    }

    public void putConfiguredAlert(AlertSlot alertSlot, AlertType alertType) {
        configuredAlerts.put(alertSlot, alertType);
        handleUpdates();
    }

    public void removeConfiguredAlert(AlertSlot alertSlot) {
        configuredAlerts.remove(alertSlot);
        handleUpdates();
    }

    public DateTime getActivatedAt() {
        return activatedAt == null ? null : activatedAt.withZone(timeZone);
    }

    public DateTime getExpiresAt() {
        return expiresAt == null ? null : expiresAt.withZone(timeZone);
    }

    public String getExpiryDateAsString() {
        return expiresAt == null ? "???" : dateUtil.dateAndTimeString(expiresAt.toDate());
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

    public Double getReservoirLevel() {
        return reservoirLevel;
    }

    public synchronized void resyncNonce(int syncWord, int sentNonce, int sequenceNumber) {
        int sum = (sentNonce & 0xFFFF)
                + OmniCRC.crc16lookup[sequenceNumber]
                + (this.lot & 0xFFFF)
                + (this.tid & 0xFFFF);
        int seed = ((sum & 0xFFFF) ^ syncWord);

        this.nonceState = new NonceState(lot, tid, (byte) (seed & 0xFF));
        handleUpdates();
    }

    public int getCurrentNonce() {
        return nonceState.getCurrentNonce();
    }

    public synchronized void advanceToNextNonce() {
        nonceState.advanceToNextNonce();
        handleUpdates();
    }

    public SetupProgress getSetupProgress() {
        return setupProgress;
    }

    public synchronized void setSetupProgress(SetupProgress setupProgress) {
        if (setupProgress == null) {
            throw new IllegalArgumentException("Setup state cannot be null");
        }
        this.setupProgress = setupProgress;
        handleUpdates();
    }

    public boolean isSuspended() {
        return suspended;
    }

    public boolean hasActiveAlerts() {
        return activeAlerts != null && activeAlerts.size() > 0;
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
        handleUpdates();
    }

    public DateTime getTime() {
        DateTime now = DateTime.now();
        return now.withZone(timeZone);
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
        handleUpdates();
    }

    @Override
    public void setMessageNumber(int messageNumber) {
        super.setMessageNumber(messageNumber);
        handleUpdates();
    }

    public BasalSchedule getBasalSchedule() {
        return basalSchedule;
    }

    public void setBasalSchedule(BasalSchedule basalSchedule) {
        this.basalSchedule = basalSchedule;
        handleUpdates();
    }

    public DeliveryStatus getLastDeliveryStatus() {
        return lastDeliveryStatus;
    }

    @Override
    public void setFaultEvent(PodInfoFaultEvent faultEvent) {
        super.setFaultEvent(faultEvent);
        suspended = true;
        handleUpdates();
    }

    @Override
    public void updateFromStatusResponse(StatusResponse statusResponse) {
        DateTime activatedAtCalculated = getTime().minus(statusResponse.getTimeActive());
        if (activatedAt == null) {
            activatedAt = activatedAtCalculated;
        }
        DateTime expiresAtCalculated = activatedAtCalculated.plus(OmnipodConst.NOMINAL_POD_LIFE);
        if (expiresAt == null || expiresAtCalculated.isBefore(expiresAt) || expiresAtCalculated.isAfter(expiresAt.plusMinutes(1))) {
            expiresAt = expiresAtCalculated;
        }

        boolean newSuspendedState = statusResponse.getDeliveryStatus() == DeliveryStatus.SUSPENDED;
        if (suspended != newSuspendedState) {
            aapsLogger.info(LTag.PUMPCOMM, "Updating pod suspended state in updateFromStatusResponse. newSuspendedState={}, statusResponse={}", newSuspendedState, statusResponse.toString());
            suspended = newSuspendedState;
        }
        activeAlerts = statusResponse.getAlerts();
        lastDeliveryStatus = statusResponse.getDeliveryStatus();
        reservoirLevel = statusResponse.getReservoirLevel();
        handleUpdates();
    }

    private void handleUpdates() {
        Gson gson = omnipodUtil.getGsonInstance();
        String gsonValue = gson.toJson(this);
        aapsLogger.info(LTag.PUMPCOMM, "PodSessionState-SP: Saved Session State to SharedPreferences: " + gsonValue);
        sp.putString(OmnipodConst.Prefs.PodState, gsonValue);
        if (stateChangedHandler != null) {
            stateChangedHandler.handle(this);
        }
    }

    @Override
    public String toString() {
        return "PodSessionState{" +
                "configuredAlerts=" + configuredAlerts +
                ", stateChangedHandler=" + stateChangedHandler +
                ", activatedAt=" + activatedAt +
                ", expiresAt=" + expiresAt +
                ", piVersion=" + piVersion +
                ", pmVersion=" + pmVersion +
                ", lot=" + lot +
                ", tid=" + tid +
                ", reservoirLevel=" + reservoirLevel +
                ", suspended=" + suspended +
                ", timeZone=" + timeZone +
                ", nonceState=" + nonceState +
                ", setupProgress=" + setupProgress +
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

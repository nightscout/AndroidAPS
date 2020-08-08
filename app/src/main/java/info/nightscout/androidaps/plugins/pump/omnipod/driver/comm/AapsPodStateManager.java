package info.nightscout.androidaps.plugins.pump.omnipod.driver.comm;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import java.util.Arrays;
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
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateChangedHandler;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmniCRC;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

public class AapsPodStateManager implements PodStateManager {

    @Inject protected AAPSLogger aapsLogger;
    @Inject protected SP sp;
    @Inject protected OmnipodUtil omnipodUtil;

    private PodState podState;
    private PodStateChangedHandler stateChangedHandler;

    public AapsPodStateManager(HasAndroidInjector injector) {
        injector.androidInjector().inject(this);

        // TODO is there something like @PostConstruct in Dagger? if so, we should probably move loading the pod state there
        loadPodState();
    }

    @Override public boolean hasState() {
        return podState != null;
    }

    @Override public void removeState() {
        this.podState = null;
        persistPodState();
        notifyPodStateChanged();
    }

    @Override
    public void initState(int address) {
        if (hasState()) {
            throw new IllegalStateException("Can not init a new pod state: podState <> null");
        }
        podState = new PodState(address);
        persistPodState();
        notifyPodStateChanged();
    }

    @Override public boolean isPaired() {
        return hasState() //
                && podState.getLot() != null && podState.getTid() != null //
                && podState.getPiVersion() != null && podState.getPmVersion() != null //
                && podState.getTimeZone() != null //
                && podState.getSetupProgress() != null;
    }

    @Override
    public void setPairingParameters(int lot, int tid, FirmwareVersion piVersion, FirmwareVersion pmVersion, DateTimeZone timeZone) {
        if (!hasState()) {
            throw new IllegalStateException("Cannot set pairing parameters: podState is null");
        }
        if (isPaired()) {
            throw new IllegalStateException("Cannot set pairing parameters: pairing parameters have already been set");
        }
        if (piVersion == null) {
            throw new IllegalArgumentException("Cannot set pairing parameters: piVersion can not be null");
        }
        if (pmVersion == null) {
            throw new IllegalArgumentException("Cannot set pairing parameters: pmVersion can not be null");
        }
        if (timeZone == null) {
            throw new IllegalArgumentException("Cannot set pairing parameters: timeZone can not be null");
        }

        setAndStore(() -> {
            podState.setLot(lot);
            podState.setTid(tid);
            podState.setPiVersion(piVersion);
            podState.setPmVersion(pmVersion);
            podState.setTimeZone(timeZone);
            podState.setNonceState(new NonceState(lot, tid));
            podState.setSetupProgress(SetupProgress.ADDRESS_ASSIGNED);
            podState.getConfiguredAlerts().put(AlertSlot.SLOT7, AlertType.FINISH_SETUP_REMINDER);
        });
    }

    @Override public int getAddress() {
        return getSafe(() -> podState.getAddress());
    }

    @Override public int getMessageNumber() {
        return getSafe(() -> podState.getMessageNumber());
    }

    @Override public void setMessageNumber(int messageNumber) {
        setAndStore(() -> podState.setMessageNumber(messageNumber));
    }

    @Override public int getPacketNumber() {
        return getSafe(() -> podState.getPacketNumber());
    }

    @Override public void setPacketNumber(int packetNumber) {
        setAndStore(() -> podState.setPacketNumber(packetNumber));
    }

    @Override public void increaseMessageNumber() {
        setAndStore(() -> podState.setMessageNumber(podState.getMessageNumber() + 1));
    }

    @Override public void increasePacketNumber() {
        setAndStore(() -> podState.setPacketNumber(podState.getPacketNumber() + 1));
    }

    @Override public synchronized void resyncNonce(int syncWord, int sentNonce, int sequenceNumber) {
        if (!isPaired()) {
            throw new IllegalStateException("Cannot resync nonce: Pod is not paired yet");
        }

        int sum = (sentNonce & 0xFFFF)
                + OmniCRC.crc16lookup[sequenceNumber]
                + (podState.getLot() & 0xFFFF)
                + (podState.getTid() & 0xFFFF);
        int seed = ((sum & 0xFFFF) ^ syncWord);
        NonceState nonceState = new NonceState(podState.getLot(), podState.getTid(), (byte) (seed & 0xFF));

        setAndStore(() -> podState.setNonceState(nonceState));
    }

    @Override public synchronized int getCurrentNonce() {
        if (!isPaired()) {
            throw new IllegalStateException("Cannot get current nonce: Pod is not paired yet");
        }
        return podState.getNonceState().getCurrentNonce();
    }

    @Override public synchronized void advanceToNextNonce() {
        if (!isPaired()) {
            throw new IllegalStateException("Cannot advance to next nonce: Pod is not paired yet");
        }
        setAndStore(() -> podState.getNonceState().advanceToNextNonce());
    }

    @Override public boolean hasFaultEvent() {
        return getSafe(() -> podState.getFaultEvent()) != null;
    }

    @Override public PodInfoFaultEvent getFaultEvent() {
        return getSafe(() -> podState.getFaultEvent());
    }

    @Override public void setFaultEvent(PodInfoFaultEvent faultEvent) {
        setAndStore(() -> podState.setFaultEvent(faultEvent));
    }

    @Override public AlertType getConfiguredAlertType(AlertSlot alertSlot) {
        return getSafe(() -> podState.getConfiguredAlerts().get(alertSlot));
    }

    @Override public void putConfiguredAlert(AlertSlot alertSlot, AlertType alertType) {
        setAndStore(() -> podState.getConfiguredAlerts().put(alertSlot, alertType));
    }

    @Override public void removeConfiguredAlert(AlertSlot alertSlot) {
        setAndStore(() -> podState.getConfiguredAlerts().remove(alertSlot));
    }

    @Override public boolean hasActiveAlerts() {
        AlertSet activeAlerts = podState.getActiveAlerts();
        return activeAlerts != null && activeAlerts.size() > 0;
    }

    @Override public AlertSet getActiveAlerts() {
        return getSafe(() -> podState.getActiveAlerts());
    }

    @Override public Integer getLot() {
        return getSafe(() -> podState.getLot());
    }

    @Override public Integer getTid() {
        return getSafe(() -> podState.getTid());
    }

    @Override public FirmwareVersion getPiVersion() {
        return getSafe(() -> podState.getPiVersion());
    }

    @Override public FirmwareVersion getPmVersion() {
        return getSafe(() -> podState.getPmVersion());
    }

    @Override public DateTimeZone getTimeZone() {
        return getSafe(() -> podState.getTimeZone());
    }

    @Override public void setTimeZone(DateTimeZone timeZone) {
        if (timeZone == null) {
            throw new IllegalArgumentException("Time zone can not be null");
        }
        setAndStore(() -> podState.setTimeZone(timeZone));
    }

    @Override public DateTime getTime() {
        DateTime now = DateTime.now();
        return now.withZone(getSafe(() -> podState.getTimeZone()));
    }

    public DateTime getActivatedAt() {
        DateTime activatedAt = getSafe(() -> podState.getActivatedAt());
        return activatedAt == null ? null : activatedAt.withZone(getSafe(() -> podState.getTimeZone()));
    }

    public DateTime getExpiresAt() {
        DateTime expiresAt = getSafe(() -> podState.getExpiresAt());
        return expiresAt == null ? null : expiresAt.withZone(getSafe(() -> podState.getTimeZone()));
    }

    // TODO doesn't belong here
    public String getExpiryDateAsString() {
        DateTime expiresAt = getExpiresAt();
        return expiresAt == null ? "???" : DateUtil.dateAndTimeString(expiresAt.toDate());
    }

    public SetupProgress getSetupProgress() {
        return getSafe(() -> podState.getSetupProgress());
    }

    public void setSetupProgress(SetupProgress setupProgress) {
        if (setupProgress == null) {
            throw new IllegalArgumentException("Setup progress can not be null");
        }
        setAndStore(() -> podState.setSetupProgress(setupProgress));
    }

    @Override public boolean isSuspended() {
        return getSafe(() -> podState.isSuspended());
    }

    @Override public Double getReservoirLevel() {
        return getSafe(() -> podState.getReservoirLevel());
    }

    @Override public Duration getScheduleOffset() {
        DateTime now = getTime();
        DateTime startOfDay = new DateTime(now.getYear(), now.getMonthOfYear(), now.getDayOfMonth(),
                0, 0, 0, getSafe(() -> podState.getTimeZone()));
        return new Duration(startOfDay, now);
    }

    @Override public BasalSchedule getBasalSchedule() {
        return getSafe(() -> podState.getBasalSchedule());
    }

    @Override public void setBasalSchedule(BasalSchedule basalSchedule) {
        setAndStore(() -> podState.setBasalSchedule(basalSchedule));
    }

    @Override public DeliveryStatus getLastDeliveryStatus() {
        return getSafe(() -> podState.getLastDeliveryStatus());
    }

    @Override public void updateFromStatusResponse(StatusResponse statusResponse) {
        if (!hasState()) {
            throw new IllegalStateException("Cannot update from status response: podState is null");
        }
        setAndStore(() -> {
            if (podState.getActivatedAt() == null) {
                DateTime activatedAtCalculated = getTime().minus(statusResponse.getTimeActive());
                podState.setActivatedAt(activatedAtCalculated);
            }
            DateTime expiresAt = podState.getExpiresAt();
            DateTime expiresAtCalculated = podState.getActivatedAt().plus(OmnipodConst.NOMINAL_POD_LIFE);
            if (expiresAt == null || expiresAtCalculated.isBefore(expiresAt) || expiresAtCalculated.isAfter(expiresAt.plusMinutes(1))) {
                podState.setExpiresAt(expiresAtCalculated);
            }

            boolean newSuspendedState = statusResponse.getDeliveryStatus() == DeliveryStatus.SUSPENDED;
            if (podState.isSuspended() != newSuspendedState) {
                aapsLogger.info(LTag.PUMPCOMM, "Updating pod suspended state in updateFromStatusResponse. newSuspendedState={}, statusResponse={}", newSuspendedState, statusResponse.toString());
                podState.setSuspended(newSuspendedState);
            }
            podState.setActiveAlerts(statusResponse.getAlerts());
            podState.setLastDeliveryStatus(statusResponse.getDeliveryStatus());
            podState.setReservoirLevel(statusResponse.getReservoirLevel());
        });
    }

    @Override
    public void setStateChangedHandler(PodStateChangedHandler handler) {
        // FIXME this is an ugly workaround for not being able to serialize the PodStateChangedHandler
        if (stateChangedHandler != null) {
            throw new IllegalStateException("A PodStateChangedHandler has already been already registered");
        }
        stateChangedHandler = handler;
    }

    private void setAndStore(Runnable runnable) {
        if (!hasState()) {
            throw new IllegalStateException("Cannot mutate PodState: podState is null");
        }
        runnable.run();
        persistPodState();
        notifyPodStateChanged();
    }

    private void persistPodState() {
        Gson gson = omnipodUtil.getGsonInstance();
        String gsonValue = gson.toJson(podState);
        aapsLogger.info(LTag.PUMPCOMM, "PodState-SP: Saved PodState to SharedPreferences: " + gsonValue);
        sp.putString(OmnipodConst.Prefs.PodState, gsonValue);
    }

    private void notifyPodStateChanged() {
        if (stateChangedHandler != null) {
            stateChangedHandler.handle(this);
        }
    }

    // Not actually "safe" as it throws an Exception, but it prevents NPEs
    private <T> T getSafe(Supplier<T> supplier) {
        if (!hasState()) {
            throw new IllegalStateException("Cannot read from PodState: podState is null");
        }
        return supplier.get();
    }

    private void loadPodState() {
        podState = null;

        String storedPodState = sp.getString(OmnipodConst.Prefs.PodState, "");

        if (StringUtils.isEmpty(storedPodState)) {
            aapsLogger.info(LTag.PUMP, "PodState-SP: no PodState present in SharedPreferences");
        } else {
            aapsLogger.info(LTag.PUMP, "PodState-SP: loaded PodState from SharedPreferences: " + storedPodState);
            try {
                podState = omnipodUtil.getGsonInstance().fromJson(storedPodState, PodState.class);
            } catch (Exception ex) {
                aapsLogger.error(LTag.PUMPCOMM, "PodState-SP: could not deserialize PodState", ex);
            }
        }

        notifyPodStateChanged();
    }

    @Override public String toString() {
        return "AapsPodStateManager{" +
                "podState=" + podState +
                '}';
    }

    private static class PodState {
        private final int address;
        private Integer lot;
        private Integer tid;
        private FirmwareVersion piVersion;
        private FirmwareVersion pmVersion;
        private int packetNumber;
        private int messageNumber;
        private DateTimeZone timeZone;
        private DateTime activatedAt;
        private DateTime expiresAt;
        private PodInfoFaultEvent faultEvent;
        private Double reservoirLevel;
        private boolean suspended;
        private NonceState nonceState;
        private SetupProgress setupProgress;
        private DeliveryStatus lastDeliveryStatus;
        private AlertSet activeAlerts;
        private BasalSchedule basalSchedule;
        private final Map<AlertSlot, AlertType> configuredAlerts = new HashMap<>();

        private PodState(int address) {
            this.address = address;
        }

        public int getAddress() {
            return address;
        }

        public Integer getLot() {
            return lot;
        }

        public void setLot(int lot) {
            this.lot = lot;
        }

        public Integer getTid() {
            return tid;
        }

        public void setTid(int tid) {
            this.tid = tid;
        }

        public FirmwareVersion getPiVersion() {
            return piVersion;
        }

        public void setPiVersion(FirmwareVersion piVersion) {
            if (this.piVersion != null) {
                throw new IllegalStateException("piVersion has already been set");
            }
            if (piVersion == null) {
                throw new IllegalArgumentException("piVersion can not be null");
            }
            this.piVersion = piVersion;
        }

        public FirmwareVersion getPmVersion() {
            return pmVersion;
        }

        public void setPmVersion(FirmwareVersion pmVersion) {
            this.pmVersion = pmVersion;
        }

        public int getPacketNumber() {
            return packetNumber;
        }

        public void setPacketNumber(int packetNumber) {
            this.packetNumber = packetNumber;
        }

        public int getMessageNumber() {
            return messageNumber;
        }

        public void setMessageNumber(int messageNumber) {
            this.messageNumber = messageNumber;
        }

        public DateTimeZone getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(DateTimeZone timeZone) {
            this.timeZone = timeZone;
        }

        public DateTime getActivatedAt() {
            return activatedAt;
        }

        public void setActivatedAt(DateTime activatedAt) {
            this.activatedAt = activatedAt;
        }

        public DateTime getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(DateTime expiresAt) {
            this.expiresAt = expiresAt;
        }

        public PodInfoFaultEvent getFaultEvent() {
            return faultEvent;
        }

        public void setFaultEvent(PodInfoFaultEvent faultEvent) {
            this.faultEvent = faultEvent;
        }

        public Double getReservoirLevel() {
            return reservoirLevel;
        }

        public void setReservoirLevel(Double reservoirLevel) {
            this.reservoirLevel = reservoirLevel;
        }

        public boolean isSuspended() {
            return suspended;
        }

        public void setSuspended(boolean suspended) {
            this.suspended = suspended;
        }

        public NonceState getNonceState() {
            return nonceState;
        }

        public void setNonceState(NonceState nonceState) {
            this.nonceState = nonceState;
        }

        public SetupProgress getSetupProgress() {
            return setupProgress;
        }

        public void setSetupProgress(SetupProgress setupProgress) {
            this.setupProgress = setupProgress;
        }

        public DeliveryStatus getLastDeliveryStatus() {
            return lastDeliveryStatus;
        }

        public void setLastDeliveryStatus(DeliveryStatus lastDeliveryStatus) {
            this.lastDeliveryStatus = lastDeliveryStatus;
        }

        public AlertSet getActiveAlerts() {
            return activeAlerts;
        }

        public void setActiveAlerts(AlertSet activeAlerts) {
            this.activeAlerts = activeAlerts;
        }

        public BasalSchedule getBasalSchedule() {
            return basalSchedule;
        }

        public void setBasalSchedule(BasalSchedule basalSchedule) {
            this.basalSchedule = basalSchedule;
        }

        public Map<AlertSlot, AlertType> getConfiguredAlerts() {
            return configuredAlerts;
        }

        @Override public String toString() {
            return "PodState{" +
                    "address=" + address +
                    ", lot=" + lot +
                    ", tid=" + tid +
                    ", piVersion=" + piVersion +
                    ", pmVersion=" + pmVersion +
                    ", packetNumber=" + packetNumber +
                    ", messageNumber=" + messageNumber +
                    ", timeZone=" + timeZone +
                    ", activatedAt=" + activatedAt +
                    ", expiresAt=" + expiresAt +
                    ", faultEvent=" + faultEvent +
                    ", reservoirLevel=" + reservoirLevel +
                    ", suspended=" + suspended +
                    ", nonceState=" + nonceState +
                    ", setupProgress=" + setupProgress +
                    ", lastDeliveryStatus=" + lastDeliveryStatus +
                    ", activeAlerts=" + activeAlerts +
                    ", basalSchedule=" + basalSchedule +
                    ", configuredAlerts=" + configuredAlerts +
                    '}';
        }
    }

    private static class NonceState {
        private final long[] table = new long[21];
        private int index;

        private NonceState(int lot, int tid) {
            initializeTable(lot, tid, (byte) 0x00);
        }

        private NonceState(int lot, int tid, byte seed) {
            initializeTable(lot, tid, seed);
        }

        private void initializeTable(int lot, int tid, byte seed) {
            table[0] = (long) (lot & 0xFFFF) + 0x55543DC3L + (((long) (lot) & 0xFFFFFFFFL) >> 16);
            table[0] = table[0] & 0xFFFFFFFFL;
            table[1] = (tid & 0xFFFF) + 0xAAAAE44EL + (((long) (tid) & 0xFFFFFFFFL) >> 16);
            table[1] = table[1] & 0xFFFFFFFFL;
            index = 0;
            table[0] += seed;
            for (int i = 0; i < 16; i++) {
                table[2 + i] = generateEntry();
            }
            index = (int) ((table[0] + table[1]) & 0X0F);
        }

        private int generateEntry() {
            table[0] = (((table[0] >> 16) + (table[0] & 0xFFFF) * 0x5D7FL) & 0xFFFFFFFFL);
            table[1] = (((table[1] >> 16) + (table[1] & 0xFFFF) * 0x8CA0L) & 0xFFFFFFFFL);
            return (int) ((table[1] + (table[0] << 16)) & 0xFFFFFFFFL);
        }

        public int getCurrentNonce() {
            return (int) table[(2 + index)];
        }

        public void advanceToNextNonce() {
            int nonce = getCurrentNonce();
            table[(2 + index)] = generateEntry();
            index = (nonce & 0x0F);
        }

        @Override
        public String toString() {
            return "NonceState{" +
                    "table=" + Arrays.toString(table) +
                    ", index=" + index +
                    '}';
        }
    }

    // TODO replace with java.util.function.Supplier<T> when min API level >= 24
    @FunctionalInterface
    private interface Supplier<T> {
        T get();
    }
}

package info.nightscout.androidaps.plugins.pump.omnipod.driver.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusUpdatableResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoFaultEvent;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.FirmwareVersion;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodCrc;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.schedule.BasalSchedule;

// TODO add nullchecks on some setters
public abstract class PodStateManager {

    private final AAPSLogger aapsLogger;
    private final Gson gsonInstance;
    private PodState podState;

    protected PodStateManager(AAPSLogger aapsLogger) {
        this.aapsLogger = aapsLogger;
        this.gsonInstance = createGson();
    }

    public final void discardState() {
        this.podState = null;
        storePodState();
    }

    public final void initState(int address) {
        if (hasPodState()) {
            throw new IllegalStateException("Can not init a new pod state: podState <> null");
        }
        podState = new PodState(address);
        storePodState();
    }

    /**
     * @return true if we have a Pod state (which at least contains an address), indicating it is legal to call getters on PodStateManager
     */
    public final boolean hasPodState() {
        return podState != null;
    }

    /**
     * @return true if we have a Pod state and the Pod has been initialized, meaning it has an address assigned.
     */
    public final boolean isPodInitialized() {
        return hasPodState() //
                && podState.getLot() != null && podState.getTid() != null //
                && podState.getPiVersion() != null && podState.getPmVersion() != null //
                && podState.getTimeZone() != null //
                && podState.getPodProgressStatus() != null;
    }

    /**
     * @return true if we have a Pod state and the Pod activation has been completed. The pod could also be dead at this point
     */
    public final boolean isPodActivationCompleted() {
        return isPodInitialized() && podState.getPodProgressStatus().isAtLeast(PodProgressStatus.ABOVE_FIFTY_UNITS) && podState.getPodProgressStatus() != PodProgressStatus.ACTIVATION_TIME_EXCEEDED;
    }

    /**
     * @return true if we have a Pod state and the Pod is running, meaning the activation process has completed and the Pod is not deactivated or in a fault state
     */
    public final boolean isPodRunning() {
        return isPodInitialized() && getPodProgressStatus().isRunning();
    }

    /**
     * @return true if the Pod is initialized and a Fault Event has occurred
     */
    public final boolean isPodFaulted() {
        return isPodInitialized() && podState.getPodProgressStatus().equals(PodProgressStatus.FAULT_EVENT_OCCURRED);
    }

    /**
     * @return true if we have a Pod state and the Pod is dead, meaning it is either in a fault state or activation time has been exceeded or it is deactivated
     */
    public boolean isPodDead() {
        return isPodInitialized() && getPodProgressStatus().isDead();
    }

    public final void setInitializationParameters(int lot, int tid, FirmwareVersion piVersion, FirmwareVersion pmVersion, DateTimeZone timeZone, PodProgressStatus podProgressStatus) {
        if (isPodInitialized() && getPodProgressStatus().isAfter(PodProgressStatus.REMINDER_INITIALIZED)) {
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
        if (podProgressStatus == null) {
            throw new IllegalArgumentException("Cannot set pairing parameters: podProgressStatus can not be null");
        }

        setAndStore(() -> {
            podState.setLot(lot);
            podState.setTid(tid);
            podState.setPiVersion(piVersion);
            podState.setPmVersion(pmVersion);
            podState.setTimeZone(timeZone);
            podState.setNonceState(new NonceState(lot, tid));
            podState.setPodProgressStatus(podProgressStatus);
            podState.getConfiguredAlerts().put(AlertSlot.SLOT7, AlertType.FINISH_SETUP_REMINDER);
        });
    }

    public final int getAddress() {
        return getSafe(() -> podState.getAddress());
    }

    public final int getMessageNumber() {
        return getSafe(() -> podState.getMessageNumber());
    }

    /**
     * Does not automatically store pod state in order to decrease I/O load
     */
    public final void setMessageNumber(int messageNumber) {
        setSafe(() -> podState.setMessageNumber(messageNumber));
    }

    public final int getPacketNumber() {
        return getSafe(() -> podState.getPacketNumber());
    }

    /**
     * Does not automatically store pod state in order to decrease I/O load
     */
    public final void increaseMessageNumber() {
        setSafe(() -> podState.setMessageNumber((podState.getMessageNumber() + 1) & 0b1111));
    }

    /**
     * Does not automatically store pod state in order to decrease I/O load
     */
    public final void increasePacketNumber() {
        setSafe(() -> podState.setPacketNumber((podState.getPacketNumber() + 1) & 0b11111));
    }

    /**
     * Does not automatically store pod state in order to decrease I/O load
     */
    public final synchronized void resyncNonce(int syncWord, int sentNonce, int sequenceNumber) {
        if (!isPodInitialized()) {
            throw new IllegalStateException("Cannot resync nonce: Pod is not paired yet");
        }

        int sum = (sentNonce & 0xFFFF)
                + OmnipodCrc.crc16lookup[sequenceNumber]
                + (podState.getLot() & 0xFFFF)
                + (podState.getTid() & 0xFFFF);
        int seed = ((sum & 0xFFFF) ^ syncWord);
        NonceState nonceState = new NonceState(podState.getLot(), podState.getTid(), (byte) (seed & 0xFF));

        setSafe(() -> podState.setNonceState(nonceState));
    }

    public final synchronized int getCurrentNonce() {
        if (!isPodInitialized()) {
            throw new IllegalStateException("Cannot get current nonce: Pod is not paired yet");
        }
        return podState.getNonceState().getCurrentNonce();
    }

    /**
     * Does not automatically store pod state in order to decrease I/O load
     */
    public final synchronized void advanceToNextNonce() {
        if (!isPodInitialized()) {
            throw new IllegalStateException("Cannot advance to next nonce: Pod is not paired yet");
        }
        setSafe(() -> podState.getNonceState().advanceToNextNonce());
    }

    public final DateTime getLastSuccessfulCommunication() {
        return getSafe(() -> podState.getLastSuccessfulCommunication());
    }

    /**
     * Does not automatically store pod state in order to decrease I/O load
     */
    public final void setLastSuccessfulCommunication(DateTime dateTime) {
        setSafe(() -> podState.setLastSuccessfulCommunication(dateTime));
    }

    public final DateTime getLastFailedCommunication() {
        return getSafe(() -> podState.getLastFailedCommunication());
    }

    /**
     * Does not automatically store pod state in order to decrease I/O load
     */
    public final void setLastFailedCommunication(DateTime dateTime) {
        setSafe(() -> podState.setLastFailedCommunication(dateTime));
    }

    public final DateTime getLastUpdatedFromResponse() {
        return getSafe(() -> podState.getLastUpdatedFromResponse());
    }

    /**
     * @return true if the Pod State contains a fault event. Is the Pod state does not contain
     * a fault event, this does NOT necessarily mean that the Pod is not faulted. For a reliable
     * indication on whether or not the pod is faulted, see {@link #isPodFaulted() isPodFaulted()}
     */
    public final boolean hasFaultEvent() {
        return podState != null && podState.getFaultEvent() != null;
    }

    public final PodInfoFaultEvent getFaultEvent() {
        return getSafe(() -> podState.getFaultEvent());
    }

    public final void setFaultEvent(PodInfoFaultEvent faultEvent) {
        setAndStore(() -> podState.setFaultEvent(faultEvent));
    }

    public final AlertType getConfiguredAlertType(AlertSlot alertSlot) {
        return getSafe(() -> podState.getConfiguredAlerts().get(alertSlot));
    }

    public final void putConfiguredAlert(AlertSlot alertSlot, AlertType alertType) {
        setAndStore(() -> podState.getConfiguredAlerts().put(alertSlot, alertType));
    }

    public final void removeConfiguredAlert(AlertSlot alertSlot) {
        setAndStore(() -> podState.getConfiguredAlerts().remove(alertSlot));
    }

    public final boolean hasActiveAlerts() {
        if (podState == null) {
            return false;
        }
        AlertSet activeAlerts = podState.getActiveAlerts();
        return activeAlerts != null && activeAlerts.size() > 0;
    }

    public final AlertSet getActiveAlerts() {
        return new AlertSet(getSafe(() -> podState.getActiveAlerts()));
    }

    public final Integer getLot() {
        return getSafe(() -> podState.getLot());
    }

    public final Integer getTid() {
        return getSafe(() -> podState.getTid());
    }

    public final FirmwareVersion getPiVersion() {
        return getSafe(() -> podState.getPiVersion());
    }

    public final FirmwareVersion getPmVersion() {
        return getSafe(() -> podState.getPmVersion());
    }

    public final DateTimeZone getTimeZone() {
        return getSafe(() -> podState.getTimeZone());
    }

    public final void setTimeZone(DateTimeZone timeZone) {
        if (timeZone == null) {
            throw new IllegalArgumentException("Time zone can not be null");
        }
        setAndStore(() -> podState.setTimeZone(timeZone));
    }

    public final DateTime getTime() {
        DateTime now = DateTime.now();
        return now.withZone(getSafe(() -> podState.getTimeZone()));
    }

    public final DateTime getActivatedAt() {
        DateTime activatedAt = getSafe(() -> podState.getActivatedAt());
        return activatedAt == null ? null : activatedAt.withZone(getSafe(() -> podState.getTimeZone()));
    }

    public final DateTime getExpiresAt() {
        DateTime expiresAt = getSafe(() -> podState.getExpiresAt());
        return expiresAt == null ? null : expiresAt.withZone(getSafe(() -> podState.getTimeZone()));
    }

    public final PodProgressStatus getPodProgressStatus() {
        return getSafe(() -> podState.getPodProgressStatus());
    }

    public final void setPodProgressStatus(PodProgressStatus podProgressStatus) {
        if (podProgressStatus == null) {
            throw new IllegalArgumentException("Pod progress status can not be null");
        }
        setAndStore(() -> podState.setPodProgressStatus(podProgressStatus));
    }

    public final boolean isSuspended() {
        return getSafe(() -> podState.isSuspended());
    }

    public final Double getReservoirLevel() {
        return getSafe(() -> podState.getReservoirLevel());
    }

    public final Double getTotalInsulinDelivered() {
        return getSafe(() -> podState.getTotalInsulinDelivered());
    }

    public final Duration getScheduleOffset() {
        DateTime now = getTime();
        return new Duration(now.withTimeAtStartOfDay(), now);
    }

    public final BasalSchedule getBasalSchedule() {
        return getSafe(() -> podState.getBasalSchedule());
    }

    public final void setBasalSchedule(BasalSchedule basalSchedule) {
        setAndStore(() -> podState.setBasalSchedule(basalSchedule));
    }

    public final DateTime getLastBolusStartTime() {
        return getSafe(() -> podState.getLastBolusStartTime());
    }

    public final Double getLastBolusAmount() {
        return getSafe(() -> podState.getLastBolusAmount());
    }

    public final Duration getLastBolusDuration() {
        return getSafe(() -> podState.getLastBolusDuration());
    }

    public final boolean isLastBolusCertain() {
        Boolean certain = getSafe(() -> podState.isLastBolusCertain());
        return certain == null || certain;
    }

    public final void setLastBolus(DateTime startTime, double amount, Duration duration, boolean certain) {
        setAndStore(() -> {
            podState.setLastBolusStartTime(startTime);
            podState.setLastBolusAmount(amount);
            podState.setLastBolusDuration(duration);
            podState.setLastBolusCertain(certain);
        });
    }

    public final boolean hasLastBolus() {
        return getLastBolusAmount() != null && getLastBolusDuration() != null && getLastBolusStartTime() != null;
    }

    public final DateTime getTempBasalStartTime() {
        return getSafe(() -> podState.getTempBasalStartTime());
    }

    public final Double getTempBasalAmount() {
        return getSafe(() -> podState.getTempBasalAmount());
    }

    public final Duration getTempBasalDuration() {
        return getSafe(() -> podState.getTempBasalDuration());
    }

    public final boolean isTempBasalCertain() {
        Boolean certain = getSafe(() -> podState.isTempBasalCertain());
        return certain == null || certain;
    }

    public final void setTempBasal(DateTime startTime, Double amount, Duration duration, boolean certain) {
        setTempBasal(startTime, amount, duration, certain, true);
    }

    public final void setTempBasal(DateTime startTime, Double amount, Duration duration, Boolean certain, boolean store) {
        DateTime currentStartTime = getTempBasalStartTime();
        Double currentAmount = getTempBasalAmount();
        Duration currentDuration = getTempBasalDuration();
        if (!Objects.equals(currentStartTime, startTime) || !Objects.equals(currentAmount, amount) || !Objects.equals(currentDuration, duration)) {
            Runnable runnable = () -> {
                podState.setTempBasalStartTime(startTime);
                podState.setTempBasalAmount(amount);
                podState.setTempBasalDuration(duration);
                podState.setTempBasalCertain(certain);
            };

            if (store) {
                setAndStore(runnable);
            } else {
                setSafe(runnable);
            }
            onTbrChanged();
        }
    }

    /**
     * @return true when a Temp Basal is stored in the Pod Stated
     * Please note that this could also be an expired Temp Basal. For an indication on whether or not
     * a temp basal is actually running, use {@link #isTempBasalRunning() isTempBasalRunning()}
     */
    public final boolean hasTempBasal() {
        return getTempBasalAmount() != null && getTempBasalDuration() != null && getTempBasalStartTime() != null;
    }

    /**
     * @return true when a Temp Basal is stored in the Pod Stated and this temp basal is currently running (based on start time and duration)
     */
    public final boolean isTempBasalRunning() {
        if (hasTempBasal()) {
            DateTime tempBasalEndTime = getTempBasalStartTime().plus(getTempBasalDuration());
            return DateTime.now().isBefore(tempBasalEndTime);
        }
        return false;
    }

    public final DeliveryStatus getLastDeliveryStatus() {
        return getSafe(() -> podState.getLastDeliveryStatus());
    }

    public final Duration getExpirationAlertTimeBeforeShutdown() {
        return getSafe(() -> podState.getExpirationAlertTimeBeforeShutdown());
    }

    public final void setExpirationAlertTimeBeforeShutdown(Duration duration) {
        setAndStore(() -> podState.setExpirationAlertTimeBeforeShutdown(duration));
    }

    public final Integer getLowReservoirAlertUnits() {
        return getSafe(() -> podState.getLowReservoirAlertUnits());
    }

    public final void setLowReservoirAlertUnits(Integer units) {
        setAndStore(() -> podState.setLowReservoirAlertUnits(units));
    }

    /**
     * Does not automatically store pod state in order to decrease I/O load
     */
    public final void updateFromResponse(StatusUpdatableResponse statusResponse) {
        setSafe(() -> {
            if (podState.getActivatedAt() == null) {
                DateTime activatedAtCalculated = getTime().minus(statusResponse.getTimeActive());
                podState.setActivatedAt(activatedAtCalculated);
            }
            DateTime expiresAt = podState.getExpiresAt();
            DateTime expiresAtCalculated = podState.getActivatedAt().plus(OmnipodConstants.NOMINAL_POD_LIFE);
            if (expiresAt == null || expiresAtCalculated.isBefore(expiresAt) || expiresAtCalculated.isAfter(expiresAt.plusMinutes(1))) {
                podState.setExpiresAt(expiresAtCalculated);
            }

            podState.setSuspended(statusResponse.getDeliveryStatus() == DeliveryStatus.SUSPENDED);
            podState.setActiveAlerts(statusResponse.getUnacknowledgedAlerts());
            podState.setLastDeliveryStatus(statusResponse.getDeliveryStatus());
            podState.setReservoirLevel(statusResponse.getReservoirLevel());
            podState.setTotalTicksDelivered(statusResponse.getTicksDelivered());
            podState.setPodProgressStatus(statusResponse.getPodProgressStatus());
            if (statusResponse.getDeliveryStatus().isTbrRunning()) {
                if (!isTempBasalCertain() && isTempBasalRunning()) {
                    podState.setTempBasalCertain(true);
                }
            } else {
                // Triggers {@link #onTbrChanged() onTbrChanged()} when appropriate
                setTempBasal(null, null, null, true, false);
            }
            podState.setLastUpdatedFromResponse(DateTime.now());
        });
    }

    protected void onTbrChanged() {
        // Deliberately left empty
        // Can be overridden in subclasses
    }

    private void setAndStore(Runnable runnable) {
        setSafe(runnable);
        storePodState();
    }

    // Not actually "safe" as it throws an Exception, but it prevents NPEs
    private void setSafe(Runnable runnable) {
        if (!hasPodState()) {
            throw new IllegalStateException("Cannot mutate PodState: podState is null");
        }
        runnable.run();
    }

    public void storePodState() {
        String podState = gsonInstance.toJson(this.podState);
        aapsLogger.debug(LTag.PUMP, "storePodState: storing podState: {}", podState);
        storePodState(podState);
    }

    protected abstract void storePodState(String podState);

    protected abstract String readPodState();

    // Should be called after initializing the object
    public final void loadPodState() {
        podState = null;

        String storedPodState = readPodState();

        if (StringUtils.isEmpty(storedPodState)) {
            aapsLogger.info(LTag.PUMP, "loadPodState: no Pod state was provided");
        } else {
            aapsLogger.info(LTag.PUMP, "loadPodState: serialized Pod state was provided: " + storedPodState);
            try {
                podState = gsonInstance.fromJson(storedPodState, PodState.class);
            } catch (Exception ex) {
                aapsLogger.error(LTag.PUMP, "loadPodState: could not deserialize PodState: " + storedPodState, ex);
            }
        }
    }

    // Not actually "safe" as it throws an Exception, but it prevents NPEs
    private <T> T getSafe(Supplier<T> supplier) {
        if (!hasPodState()) {
            throw new IllegalStateException("Cannot read from PodState: podState is null");
        }
        return supplier.get();
    }

    private static Gson createGson() {
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(DateTime.class, (JsonSerializer<DateTime>) (dateTime, typeOfSrc, context) ->
                        new JsonPrimitive(ISODateTimeFormat.dateTime().print(dateTime)))
                .registerTypeAdapter(DateTime.class, (JsonDeserializer<DateTime>) (json, typeOfT, context) ->
                        ISODateTimeFormat.dateTime().parseDateTime(json.getAsString()))
                .registerTypeAdapter(DateTimeZone.class, (JsonSerializer<DateTimeZone>) (timeZone, typeOfSrc, context) ->
                        new JsonPrimitive(timeZone.getID()))
                .registerTypeAdapter(DateTimeZone.class, (JsonDeserializer<DateTimeZone>) (json, typeOfT, context) ->
                        DateTimeZone.forID(json.getAsString()));

        return gsonBuilder.create();
    }

    @Override public String toString() {
        return "AapsPodStateManager{" +
                "podState=" + podState +
                '}';
    }

    private static final class PodState {
        private final int address;
        private Integer lot;
        private Integer tid;
        private FirmwareVersion piVersion;
        private FirmwareVersion pmVersion;
        private int packetNumber;
        private int messageNumber;
        private DateTime lastSuccessfulCommunication;
        private DateTime lastFailedCommunication;
        private DateTime lastUpdatedFromResponse;
        private DateTimeZone timeZone;
        private DateTime activatedAt;
        private DateTime expiresAt;
        private PodInfoFaultEvent faultEvent;
        private Double reservoirLevel;
        private Integer totalTicksDelivered;
        private boolean suspended;
        private NonceState nonceState;
        private PodProgressStatus podProgressStatus;
        private DeliveryStatus lastDeliveryStatus;
        private AlertSet activeAlerts;
        private BasalSchedule basalSchedule;
        private DateTime lastBolusStartTime;
        private Double lastBolusAmount;
        private Duration lastBolusDuration;
        private Boolean lastBolusCertain;
        private Double tempBasalAmount;
        private DateTime tempBasalStartTime;
        private Duration tempBasalDuration;
        private Boolean tempBasalCertain;
        private Duration expirationAlertTimeBeforeShutdown;
        private Integer lowReservoirAlertUnits;
        private final Map<AlertSlot, AlertType> configuredAlerts = new HashMap<>();

        private PodState(int address) {
            this.address = address;
        }

        int getAddress() {
            return address;
        }

        Integer getLot() {
            return lot;
        }

        void setLot(int lot) {
            this.lot = lot;
        }

        Integer getTid() {
            return tid;
        }

        void setTid(int tid) {
            this.tid = tid;
        }

        FirmwareVersion getPiVersion() {
            return piVersion;
        }

        void setPiVersion(FirmwareVersion piVersion) {
            this.piVersion = piVersion;
        }

        FirmwareVersion getPmVersion() {
            return pmVersion;
        }

        void setPmVersion(FirmwareVersion pmVersion) {
            this.pmVersion = pmVersion;
        }

        int getPacketNumber() {
            return packetNumber;
        }

        void setPacketNumber(int packetNumber) {
            this.packetNumber = packetNumber;
        }

        int getMessageNumber() {
            return messageNumber;
        }

        void setMessageNumber(int messageNumber) {
            this.messageNumber = messageNumber;
        }

        DateTime getLastSuccessfulCommunication() {
            return lastSuccessfulCommunication;
        }

        void setLastSuccessfulCommunication(DateTime lastSuccessfulCommunication) {
            this.lastSuccessfulCommunication = lastSuccessfulCommunication;
        }

        DateTime getLastFailedCommunication() {
            return lastFailedCommunication;
        }

        void setLastFailedCommunication(DateTime lastFailedCommunication) {
            this.lastFailedCommunication = lastFailedCommunication;
        }

        DateTime getLastUpdatedFromResponse() {
            return lastUpdatedFromResponse;
        }

        void setLastUpdatedFromResponse(DateTime lastUpdatedFromResponse) {
            this.lastUpdatedFromResponse = lastUpdatedFromResponse;
        }

        DateTimeZone getTimeZone() {
            return timeZone;
        }

        void setTimeZone(DateTimeZone timeZone) {
            this.timeZone = timeZone;
        }

        DateTime getActivatedAt() {
            return activatedAt;
        }

        void setActivatedAt(DateTime activatedAt) {
            this.activatedAt = activatedAt;
        }

        DateTime getExpiresAt() {
            return expiresAt;
        }

        void setExpiresAt(DateTime expiresAt) {
            this.expiresAt = expiresAt;
        }

        PodInfoFaultEvent getFaultEvent() {
            return faultEvent;
        }

        void setFaultEvent(PodInfoFaultEvent faultEvent) {
            this.faultEvent = faultEvent;
        }

        Double getReservoirLevel() {
            return reservoirLevel;
        }

        void setReservoirLevel(Double reservoirLevel) {
            this.reservoirLevel = reservoirLevel;
        }

        public Integer getTotalTicksDelivered() {
            return totalTicksDelivered;
        }

        Double getTotalInsulinDelivered() {
            if (totalTicksDelivered != null) {
                return totalTicksDelivered * OmnipodConstants.POD_PULSE_SIZE;
            } else {
                return null;
            }
        }

        void setTotalTicksDelivered(Integer totalTicksDelivered) {
            this.totalTicksDelivered = totalTicksDelivered;
        }

        boolean isSuspended() {
            return suspended;
        }

        void setSuspended(boolean suspended) {
            this.suspended = suspended;
        }

        NonceState getNonceState() {
            return nonceState;
        }

        void setNonceState(NonceState nonceState) {
            this.nonceState = nonceState;
        }

        PodProgressStatus getPodProgressStatus() {
            return podProgressStatus;
        }

        void setPodProgressStatus(PodProgressStatus podProgressStatus) {
            this.podProgressStatus = podProgressStatus;
        }

        DeliveryStatus getLastDeliveryStatus() {
            return lastDeliveryStatus;
        }

        void setLastDeliveryStatus(DeliveryStatus lastDeliveryStatus) {
            this.lastDeliveryStatus = lastDeliveryStatus;
        }

        AlertSet getActiveAlerts() {
            return activeAlerts;
        }

        void setActiveAlerts(AlertSet activeAlerts) {
            this.activeAlerts = activeAlerts;
        }

        BasalSchedule getBasalSchedule() {
            return basalSchedule;
        }

        void setBasalSchedule(BasalSchedule basalSchedule) {
            this.basalSchedule = basalSchedule;
        }

        DateTime getLastBolusStartTime() {
            return lastBolusStartTime;
        }

        void setLastBolusStartTime(DateTime lastBolusStartTime) {
            this.lastBolusStartTime = lastBolusStartTime;
        }

        Double getLastBolusAmount() {
            return lastBolusAmount;
        }

        void setLastBolusAmount(Double lastBolusAmount) {
            this.lastBolusAmount = lastBolusAmount;
        }

        Duration getLastBolusDuration() {
            return lastBolusDuration;
        }

        void setLastBolusDuration(Duration lastBolusDuration) {
            this.lastBolusDuration = lastBolusDuration;
        }

        Boolean isLastBolusCertain() {
            return lastBolusCertain;
        }

        void setLastBolusCertain(Boolean certain) {
            this.lastBolusCertain = certain;
        }

        Double getTempBasalAmount() {
            return tempBasalAmount;
        }

        void setTempBasalAmount(Double tempBasalAmount) {
            this.tempBasalAmount = tempBasalAmount;
        }

        DateTime getTempBasalStartTime() {
            return tempBasalStartTime;
        }

        void setTempBasalStartTime(DateTime tempBasalStartTime) {
            this.tempBasalStartTime = tempBasalStartTime;
        }

        Duration getTempBasalDuration() {
            return tempBasalDuration;
        }

        void setTempBasalDuration(Duration tempBasalDuration) {
            this.tempBasalDuration = tempBasalDuration;
        }

        Boolean isTempBasalCertain() {
            return tempBasalCertain;
        }

        void setTempBasalCertain(Boolean certain) {
            this.tempBasalCertain = certain;
        }

        Map<AlertSlot, AlertType> getConfiguredAlerts() {
            return configuredAlerts;
        }

        Duration getExpirationAlertTimeBeforeShutdown() {
            return expirationAlertTimeBeforeShutdown;
        }

        void setExpirationAlertTimeBeforeShutdown(Duration duration) {
            expirationAlertTimeBeforeShutdown = duration;
        }

        Integer getLowReservoirAlertUnits() {
            return lowReservoirAlertUnits;
        }

        void setLowReservoirAlertUnits(Integer units) {
            lowReservoirAlertUnits = units;
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
                    ", lastSuccessfulCommunication=" + lastSuccessfulCommunication +
                    ", lastFailedCommunication=" + lastFailedCommunication +
                    ", lastUpdatedFromResponse=" + lastUpdatedFromResponse +
                    ", timeZone=" + timeZone +
                    ", activatedAt=" + activatedAt +
                    ", expiresAt=" + expiresAt +
                    ", faultEvent=" + faultEvent +
                    ", reservoirLevel=" + reservoirLevel +
                    ", totalTicksDelivered=" + totalTicksDelivered +
                    ", suspended=" + suspended +
                    ", nonceState=" + nonceState +
                    ", podProgressStatus=" + podProgressStatus +
                    ", lastDeliveryStatus=" + lastDeliveryStatus +
                    ", activeAlerts=" + activeAlerts +
                    ", basalSchedule=" + basalSchedule +
                    ", lastBolusStartTime=" + lastBolusStartTime +
                    ", lastBolusAmount=" + lastBolusAmount +
                    ", lastBolusDuration=" + lastBolusDuration +
                    ", lastBolusCertain=" + lastBolusCertain +
                    ", tempBasalAmount=" + tempBasalAmount +
                    ", tempBasalStartTime=" + tempBasalStartTime +
                    ", tempBasalDuration=" + tempBasalDuration +
                    ", tempBasalCertain=" + tempBasalCertain +
                    ", expirationAlertHoursBeforeShutdown=" + expirationAlertTimeBeforeShutdown +
                    ", lowReservoirAlertUnits=" + lowReservoirAlertUnits +
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

        int getCurrentNonce() {
            return (int) table[(2 + index)];
        }

        void advanceToNextNonce() {
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
}

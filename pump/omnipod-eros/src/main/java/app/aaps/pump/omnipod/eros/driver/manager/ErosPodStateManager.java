package app.aaps.pump.omnipod.eros.driver.manager;

import androidx.annotation.NonNull;

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
import java.util.Optional;
import java.util.function.Supplier;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.pump.omnipod.eros.driver.communication.message.OmnipodMessage;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusUpdatableResponse;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoDetailedStatus;
import app.aaps.pump.omnipod.eros.driver.definition.ActivationProgress;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSet;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSlot;
import app.aaps.pump.omnipod.eros.driver.definition.AlertType;
import app.aaps.pump.omnipod.eros.driver.definition.DeliveryStatus;
import app.aaps.pump.omnipod.eros.driver.definition.FaultEventCode;
import app.aaps.pump.omnipod.eros.driver.definition.FirmwareVersion;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodCrc;
import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus;
import app.aaps.pump.omnipod.eros.driver.definition.schedule.BasalSchedule;
import app.aaps.pump.omnipod.eros.driver.util.TimeUtil;

// TODO add nullchecks on some setters
public abstract class ErosPodStateManager {

    private final AAPSLogger aapsLogger;
    private final Gson gsonInstance;
    private PodState podState;

    protected ErosPodStateManager(AAPSLogger aapsLogger) {
        this.aapsLogger = aapsLogger;
        this.gsonInstance = createGson();
    }

    /**
     * Discard Pod state
     */
    public final void discardState() {
        // Change on commit 4cea57acf6d74baffef83e1f04376b10bb5c1978 Nov 2021
        // As by commit, keep podState object but wipe address to 0x0 to signal hasPodState()
        // there is no state ( = no Pod address).
        this.podState = new PodState(0x0);
        storePodState();
    }

    /**
     * Init Pod state but only if it has valid state.
     *
     * @param address New Pod address
     */
    public final void initState(int address) {
        if (hasPodState()) {
            throw new IllegalStateException("Can not init a new pod state: State is " +
                    "null or discarded?");
        }
        podState = new PodState(address);
        storePodState();
    }

    /**
     * @return true if we have a Pod state (which at least contains an valid address), indicating
     * it is legal to call getters on PodStateManager
     */
    public final boolean hasPodState() {

        return this.podState != null
                && this.podState.getAddress() != 0x0; // 0x0=discarded
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
        return getActivationProgress().isCompleted();
    }

    /**
     * @return true if we have a Pod state and the Pod is running, meaning the activation process has completed and the Pod is not deactivated or in a fault state
     * This does not mean the Pod is actually delivering insulin, combine with {@link #isSuspended() isSuspended()} for that
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
     * @return true if the Pod's activation time has been exceeded
     */
    public final boolean isPodActivationTimeExceeded() {
        return isPodInitialized() && getPodProgressStatus() == PodProgressStatus.ACTIVATION_TIME_EXCEEDED;
    }

    /**
     * @return true if we have a Pod state and the Pod is dead, meaning it is either in a fault state or activation time has been exceeded or it is deactivated
     */
    public final boolean isPodDead() {
        return isPodInitialized() && getPodProgressStatus().isDead();
    }

    public final void setInitializationParameters(int lot, int tid, FirmwareVersion piVersion, FirmwareVersion pmVersion, @NonNull DateTimeZone timeZone, PodProgressStatus podProgressStatus) {
        if (isPodInitialized() && getActivationProgress().isAtLeast(ActivationProgress.PAIRING_COMPLETED)) {
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

    public final FaultEventCode getFaultEventCode() {
        return getSafe(() -> podState.getFaultEventCode());
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

    @NonNull public final AlertSet getActiveAlerts() {
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

    public final void setTimeZone(@NonNull DateTimeZone timeZone) {
        if (timeZone == null) {
            throw new IllegalArgumentException("Time zone can not be null");
        }
        setAndStore(() -> podState.setTimeZone(timeZone));
    }

    public final DateTime getTime() {
        DateTimeZone timeZone = getSafe(() -> podState.getTimeZone());
        if (timeZone == null) {
            return DateTime.now();
        }
        Duration timeActive = getSafe(() -> podState.getTimeActive());
        DateTime activatedAt = getSafe(() -> podState.getActivatedAt());
        DateTime lastUpdatedFromResponse = getSafe(() -> podState.getLastUpdatedFromResponse());
        if (timeActive == null || activatedAt == null) {
            return DateTime.now().withZone(timeZone);
        }
        return activatedAt.plus(timeActive).plus(new Duration(lastUpdatedFromResponse, DateTime.now()));
    }

    public final boolean timeDeviatesMoreThan(Duration duration) {
        return new Duration(getTime(), DateTime.now().withZoneRetainFields(getSafe(() -> podState.getTimeZone()))).abs().isLongerThan(duration);
    }

    public final DateTime getActivatedAt() {
        DateTime activatedAt = getSafe(() -> podState.getActivatedAt());
        return activatedAt == null ? null : activatedAt.withZone(getSafe(() -> podState.getTimeZone()));
    }

    public final void updateActivatedAt() {
        setAndStore(() -> podState.setActivatedAt(DateTime.now().withZone(getSafe(() -> podState.getTimeZone())).minus(getSafe(this::getTimeActive))));
    }

    public final Duration getTimeActive() {
        return getSafe(() -> podState.getTimeActive());
    }

    public final DateTime getExpiresAt() {
        DateTime activatedAt = getSafe(() -> podState.getActivatedAt());
        return activatedAt == null ? null : activatedAt.withZone(getSafe(() -> podState.getTimeZone())).plus(OmnipodConstants.NOMINAL_POD_LIFE);
    }

    public final ActivationProgress getActivationProgress() {
        if (hasPodState()) {
            return Optional.ofNullable(podState.getActivationProgress()).orElse(ActivationProgress.NONE);
        }
        return ActivationProgress.NONE;
    }

    public final void setActivationProgress(ActivationProgress activationProgress) {
        setAndStore(() -> podState.setActivationProgress(activationProgress));
    }

    public final PodProgressStatus getPodProgressStatus() {
        return getSafe(() -> podState.getPodProgressStatus());
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
        return TimeUtil.toDuration(getTime());
    }

    public final BasalSchedule getBasalSchedule() {
        return getSafe(() -> podState.getBasalSchedule());
    }

    public final void setBasalSchedule(BasalSchedule basalSchedule) {
        setAndStore(() -> podState.setBasalSchedule(basalSchedule));
    }

    public final boolean isBasalCertain() {
        Boolean certain = getSafe(() -> podState.isBasalCertain());
        return certain == null || certain;
    }

    public final void setBasalCertain(boolean certain) {
        setAndStore(() -> podState.setBasalCertain(certain));
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

    public final void setTempBasalCertain(boolean certain) {
        setAndStore(() -> {
            if (!Objects.equals(podState.isTempBasalCertain(), certain)) {
                podState.setTempBasalCertain(certain);
            }
        });
    }

    public final void setTempBasal(DateTime startTime, Double amount, Duration duration) {
        setTempBasal(startTime, amount, duration, true);
    }

    private void setTempBasal(DateTime startTime, Double amount, Duration duration, boolean store) {
        DateTime currentStartTime = getTempBasalStartTime();
        Double currentAmount = getTempBasalAmount();
        Duration currentDuration = getTempBasalDuration();
        if (!Objects.equals(currentStartTime, startTime) || !Objects.equals(currentAmount, amount) || !Objects.equals(currentDuration, duration)) {
            Runnable runnable = () -> {
                podState.setTempBasalStartTime(startTime);
                podState.setTempBasalAmount(amount);
                podState.setTempBasalDuration(duration);
            };

            if (store) {
                setAndStore(runnable);
            } else {
                setSafe(runnable);
            }
            onTbrChanged();
        }
    }

    public final void clearTempBasal() {
        clearTempBasal(true);
    }

    private void clearTempBasal(boolean store) {
        setTempBasal(null, null, null, store);
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
     * @return true when a Temp Basal is stored in the Pod State and this temp basal is currently running (based on start time and duration)
     */
    public final boolean isTempBasalRunning() {
        return isTempBasalRunningAt(null);
    }

    /**
     * @param time the time for which to look up whether a temp basal is running, null meaning now
     * @return true when a Temp Basal is stored in the Pod State and this temp basal is running at the given time (based on start time and duration),
     * or when the time provided is null and the delivery status of the Pod inidicated that a TBR is running, but not TBR is stored
     * This can happen in some rare cases.
     */
    public final boolean isTempBasalRunningAt(DateTime time) {
        if (time == null) { // now
            if (!hasTempBasal() && getLastDeliveryStatus().isTbrRunning()) {
                return true;
            }
            time = DateTime.now();
        }
        if (hasTempBasal()) {
            DateTime tempBasalStartTime = getTempBasalStartTime();
            DateTime tempBasalEndTime = tempBasalStartTime.plus(getTempBasalDuration());
            return (time.isAfter(tempBasalStartTime) || time.isEqual(tempBasalStartTime)) && time.isBefore(tempBasalEndTime);
        }
        return false;
    }

    /**
     * @return the current effective basal rate (taking Pod suspension, TBR, and basal profile into account)
     */
    public final double getEffectiveBasalRate() {
        if (isSuspended()) {
            return 0d;
        }
        return getEffectiveBasalRateAt(DateTime.now());
    }

    /**
     * @return the effective basal rate at the given time (taking TBR, and basal profile into account)
     * Suspension is not taken into account as we don't keep historic data of that
     */
    public final double getEffectiveBasalRateAt(DateTime time) {
        BasalSchedule basalSchedule = getSafe(() -> podState.getBasalSchedule());
        if (basalSchedule == null) {
            return 0d;
        }
        if (isTempBasalRunningAt(time)) {
            return getTempBasalAmount();
        }
        Duration offset = TimeUtil.toDuration(time);
        return basalSchedule.rateAt(offset);
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
    public final void updateFromResponse(StatusUpdatableResponse status, OmnipodMessage requestMessage) {
        setSafe(() -> {
            if (podState.getActivatedAt() == null) {
                DateTime activatedAtCalculated = DateTime.now().withZone(podState.getTimeZone()).minus(status.getTimeActive());
                podState.setActivatedAt(activatedAtCalculated);
            }
            podState.setSuspended(status.getDeliveryStatus() == DeliveryStatus.SUSPENDED);
            if (!Objects.equals(status.getUnacknowledgedAlerts(), podState.getActiveAlerts())) {
                podState.setActiveAlerts(status.getUnacknowledgedAlerts());
                onActiveAlertsChanged();
            }
            podState.setLastDeliveryStatus(status.getDeliveryStatus());
            podState.setReservoirLevel(status.getReservoirLevel());
            podState.setTotalTicksDelivered(status.getTicksDelivered());
            podState.setPodProgressStatus(status.getPodProgressStatus());
            podState.setTimeActive(status.getTimeActive());

            boolean wasBasalCertain = podState.isBasalCertain() == null || podState.isBasalCertain();
            boolean wasTempBasalCertain = podState.isTempBasalCertain() == null || podState.isTempBasalCertain();
            if (!status.getDeliveryStatus().isTbrRunning() && hasTempBasal()) {
                if (wasTempBasalCertain || requestMessage.isSuspendDeliveryMessage()) {
                    clearTempBasal(); // Triggers onTbrChanged when appropriate
                } else {
                    // Don't trigger onTbrChanged as we will trigger onUncertainTbrRecovered below
                    podState.setTempBasalStartTime(null);
                    podState.setTempBasalAmount(null);
                    podState.setTempBasalDuration(null);
                }
            }

            if (!wasTempBasalCertain) {
                podState.setTempBasalCertain(true);

                // We exclusively use get status messages to recover from uncertain TBRs
                // DO NOT change this as the recovery mechanism will otherwise interfere with normal delivery commands
                if (requestMessage.isGetStatusMessage()) {
                    onUncertainTbrRecovered();
                }
            }
            if (!wasBasalCertain) {
                podState.setBasalCertain(true);
            }

            if (status instanceof PodInfoDetailedStatus detailedStatus) {
                if (detailedStatus.isFaulted()) {
                    if (!Objects.equals(podState.getFaultEventCode(), detailedStatus.getFaultEventCode())) {
                        podState.setFaultEventCode(detailedStatus.getFaultEventCode());
                        onFaultEventChanged();
                    }
                }
            }

            podState.setLastUpdatedFromResponse(DateTime.now());
        });

        onUpdatedFromResponse();
    }

    protected void onTbrChanged() {
        // Deliberately left empty
        // Can be overridden in subclasses
    }

    protected void onUncertainTbrRecovered() {
        // Deliberately left empty
        // Can be overridden in subclasses
    }

    protected void onActiveAlertsChanged() {
        // Deliberately left empty
        // Can be overridden in subclasses
    }

    protected void onFaultEventChanged() {
        // Deliberately left empty
        // Can be overridden in subclasses
    }

    protected void onUpdatedFromResponse() {
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
    private <T> T getSafe(@NonNull Supplier<T> supplier) {
        if (!hasPodState()) {
            throw new IllegalStateException("Cannot read from PodState: podState is null");
        }
        return supplier.get();
    }

    @NonNull private static Gson createGson() {
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

    @NonNull @Override public String toString() {
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
        private Duration timeActive;
        private FaultEventCode faultEventCode;
        private Double reservoirLevel;
        private Integer totalTicksDelivered;
        private boolean suspended;
        private NonceState nonceState;
        private ActivationProgress activationProgress = ActivationProgress.NONE;
        private PodProgressStatus podProgressStatus;
        private DeliveryStatus lastDeliveryStatus;
        private AlertSet activeAlerts;
        private BasalSchedule basalSchedule;
        private Boolean basalCertain;
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

        public Duration getTimeActive() {
            return timeActive;
        }

        public void setTimeActive(Duration timeActive) {
            this.timeActive = timeActive;
        }

        FaultEventCode getFaultEventCode() {
            return faultEventCode;
        }

        void setFaultEventCode(FaultEventCode faultEventCode) {
            this.faultEventCode = faultEventCode;
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

        ActivationProgress getActivationProgress() {
            return activationProgress;
        }

        void setActivationProgress(ActivationProgress activationProgress) {
            this.activationProgress = activationProgress;
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

        Boolean isBasalCertain() {
            return basalCertain;
        }

        void setBasalCertain(Boolean certain) {
            this.basalCertain = certain;
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

        @NonNull Map<AlertSlot, AlertType> getConfiguredAlerts() {
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
                    ", timeActive=" + timeActive +
                    ", faultEventCode=" + faultEventCode +
                    ", reservoirLevel=" + reservoirLevel +
                    ", totalTicksDelivered=" + totalTicksDelivered +
                    ", suspended=" + suspended +
                    ", nonceState=" + nonceState +
                    ", activationProgress=" + activationProgress +
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
                    ", expirationAlertTimeBeforeShutdown=" + expirationAlertTimeBeforeShutdown +
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

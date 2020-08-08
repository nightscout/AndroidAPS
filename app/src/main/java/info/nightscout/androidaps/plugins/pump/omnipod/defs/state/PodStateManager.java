package info.nightscout.androidaps.plugins.pump.omnipod.defs.state;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.StatusResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoFaultEvent;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FirmwareVersion;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.SetupProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule.BasalSchedule;

public interface PodStateManager {

    boolean hasState();

    void removeState();

    boolean isPaired();

    public int getAddress();

    public int getMessageNumber();

    public void setMessageNumber(int messageNumber);

    public int getPacketNumber();

    public void setPacketNumber(int packetNumber);

    public void increaseMessageNumber();

    public void increasePacketNumber();

    void resyncNonce(int syncWord, int sentNonce, int sequenceNumber);

    int getCurrentNonce();

    void advanceToNextNonce();

    public boolean hasFaultEvent();

    public PodInfoFaultEvent getFaultEvent();

    public void setFaultEvent(PodInfoFaultEvent faultEvent);

    AlertType getConfiguredAlertType(AlertSlot alertSlot);

    void putConfiguredAlert(AlertSlot alertSlot, AlertType alertType);

    void removeConfiguredAlert(AlertSlot alertSlot);

    boolean hasActiveAlerts();

    AlertSet getActiveAlerts();

    Integer getLot();

    Integer getTid();

    FirmwareVersion getPiVersion();

    FirmwareVersion getPmVersion();

    DateTimeZone getTimeZone();

    void setTimeZone(DateTimeZone timeZone);

    DateTime getTime();

    DateTime getActivatedAt();

    DateTime getExpiresAt();

    String getExpiryDateAsString();

    SetupProgress getSetupProgress();

    void setSetupProgress(SetupProgress setupProgress);

    boolean isSuspended();

    Double getReservoirLevel();

    Duration getScheduleOffset();

    BasalSchedule getBasalSchedule();

    void setBasalSchedule(BasalSchedule basalSchedule);

    DeliveryStatus getLastDeliveryStatus();

    void updateFromStatusResponse(StatusResponse statusResponse);
}

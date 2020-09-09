package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response;

import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;

public interface StatusUpdatableResponse {
    DeliveryStatus getDeliveryStatus();

    PodProgressStatus getPodProgressStatus();

    Duration getTimeActive();

    Double getReservoirLevel();

    int getTicksDelivered();

    double getInsulinDelivered();

    double getBolusNotDelivered();

    byte getPodMessageCounter();

    AlertSet getUnacknowledgedAlerts();
}

package app.aaps.pump.omnipod.eros.driver.communication.message.response;

import org.joda.time.Duration;

import app.aaps.pump.omnipod.eros.driver.definition.AlertSet;
import app.aaps.pump.omnipod.eros.driver.definition.DeliveryStatus;
import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus;

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

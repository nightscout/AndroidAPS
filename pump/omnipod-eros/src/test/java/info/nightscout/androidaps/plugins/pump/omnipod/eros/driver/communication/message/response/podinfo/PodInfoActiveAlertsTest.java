package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo;

import org.joda.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import info.nightscout.pump.core.utils.ByteUtil;

class PodInfoActiveAlertsTest {
    @Test
    void testNoActiveAlerts() {
        byte[] encodedMessage = ByteUtil.fromHexString("01000000000000000000000000000000000000"); // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift
        PodInfoActiveAlerts podInfoActiveAlerts = new PodInfoActiveAlerts(encodedMessage);

        List<PodInfoActiveAlerts.AlertActivation> alertActivations = podInfoActiveAlerts.getAlertActivations();
        Assertions.assertEquals(0, alertActivations.size());
    }

    @Test
    void testChangePodAfter3Days() {
        byte[] encodedMessage = ByteUtil.fromHexString("010000000000000000000000000000000010e1"); // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift
        PodInfoActiveAlerts podInfoActiveAlerts = new PodInfoActiveAlerts(encodedMessage);

        List<PodInfoActiveAlerts.AlertActivation> alertActivations = podInfoActiveAlerts.getAlertActivations();
        Assertions.assertEquals(1, alertActivations.size());

        PodInfoActiveAlerts.AlertActivation alertActivation = alertActivations.get(0);
        Duration expectedDuration = Duration.standardHours(72).plus(Duration.standardMinutes(1));
        Assertions.assertTrue(expectedDuration.isEqual(alertActivation.getValueAsDuration()));
    }
}

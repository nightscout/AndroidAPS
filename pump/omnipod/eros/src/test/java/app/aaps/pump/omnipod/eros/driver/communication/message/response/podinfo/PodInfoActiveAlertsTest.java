package app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo;

import static com.google.common.truth.Truth.assertThat;

import org.joda.time.Duration;
import org.junit.jupiter.api.Test;

import java.util.List;

import app.aaps.core.utils.pump.ByteUtil;

class PodInfoActiveAlertsTest {
    @Test
    void testNoActiveAlerts() {
        byte[] encodedMessage = ByteUtil.INSTANCE.fromHexString("01000000000000000000000000000000000000"); // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift
        PodInfoActiveAlerts podInfoActiveAlerts = new PodInfoActiveAlerts(encodedMessage);

        List<PodInfoActiveAlerts.AlertActivation> alertActivations = podInfoActiveAlerts.getAlertActivations();
        assertThat(alertActivations).isEmpty();
    }

    @Test
    void testChangePodAfter3Days() {
        byte[] encodedMessage = ByteUtil.INSTANCE.fromHexString("010000000000000000000000000000000010e1"); // from https://github.com/ps2/rileylink_ios/blob/omnipod-testing/OmniKitTests/PodInfoTests.swift
        PodInfoActiveAlerts podInfoActiveAlerts = new PodInfoActiveAlerts(encodedMessage);

        List<PodInfoActiveAlerts.AlertActivation> alertActivations = podInfoActiveAlerts.getAlertActivations();
        assertThat(alertActivations).hasSize(1);

        PodInfoActiveAlerts.AlertActivation alertActivation = alertActivations.get(0);
        Duration expectedDuration = Duration.standardHours(72).plus(Duration.standardMinutes(1));
        assertThat(expectedDuration).isEqualTo(alertActivation.getValueAsDuration());
    }
}

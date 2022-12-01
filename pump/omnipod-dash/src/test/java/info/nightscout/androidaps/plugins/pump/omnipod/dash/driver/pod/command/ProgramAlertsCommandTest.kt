package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertConfiguration
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertTrigger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepRepetitionType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.BeepType
import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import org.junit.Assert
import org.junit.jupiter.api.Test

class ProgramAlertsCommandTest {

    @Test @Throws(DecoderException::class) fun testExpirationAlerts() {
        val configurations: MutableList<AlertConfiguration> = ArrayList()
        configurations.add(
            AlertConfiguration(
                AlertType.EXPIRATION,
                true,
                420.toShort(),
                false,
                AlertTrigger.TimerTrigger(4305.toShort()),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.XXX3
            )
        )
        configurations.add(
            AlertConfiguration(
                AlertType.EXPIRATION_IMMINENT,
                true,
                0.toShort(),
                false,
                AlertTrigger.TimerTrigger(4725.toShort()),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.XXX4
            )
        )

        val encoded = ProgramAlertsCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(3.toShort())
            .setMultiCommandFlag(true)
            .setNonce(1229869870)
            .setAlertConfigurations(configurations)
            .build()
            .encoded

        Assert.assertArrayEquals(Hex.decodeHex("024200038C121910494E532E79A410D1050228001275060280F5"), encoded)
    }

    @Test @Throws(DecoderException::class) fun testLowReservoirAlert() {
        val configurations: MutableList<AlertConfiguration> = ArrayList()
        configurations.add(
            AlertConfiguration(
                AlertType.LOW_RESERVOIR,
                true,
                0.toShort(),
                false,
                AlertTrigger.ReservoirVolumeTrigger(200.toShort()),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.XXX
            )
        )

        val encoded = ProgramAlertsCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(8.toShort())
            .setNonce(1229869870)
            .setAlertConfigurations(configurations)
            .build()
            .encoded

        Assert.assertArrayEquals(Hex.decodeHex("02420003200C190A494E532E4C0000C801020149"), encoded)
    }

    @Test @Throws(DecoderException::class) fun testUserExpirationAlert() {
        val configurations: MutableList<AlertConfiguration> = ArrayList()
        configurations.add(
            AlertConfiguration(
                AlertType.USER_SET_EXPIRATION,
                true,
                0.toShort(),
                false,
                AlertTrigger.TimerTrigger(4079.toShort()),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.EVERY_MINUTE_AND_EVERY_15_MIN
            )
        )

        val encoded = ProgramAlertsCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(15.toShort())
            .setNonce(1229869870)
            .setAlertConfigurations(configurations)
            .build()
            .encoded

        Assert.assertArrayEquals(Hex.decodeHex("024200033C0C190A494E532E38000FEF030203E2"), encoded)
    }

    @Test @Throws(DecoderException::class) fun testLumpOfCoalAlert() {
        val configurations: MutableList<AlertConfiguration> = ArrayList()
        configurations.add(
            AlertConfiguration(
                AlertType.EXPIRATION,
                true,
                55.toShort(),
                false,
                AlertTrigger.TimerTrigger(5.toShort()),
                BeepType.FOUR_TIMES_BIP_BEEP,
                BeepRepetitionType.XXX5
            )
        )

        val encoded = ProgramAlertsCommand.Builder()
            .setUniqueId(37879811)
            .setSequenceNumber(10.toShort())
            .setMultiCommandFlag(false)
            .setNonce(1229869870)
            .setAlertConfigurations(configurations)
            .build()
            .encoded

        Assert.assertArrayEquals(Hex.decodeHex("02420003280C190A494E532E7837000508020356"), encoded)
    }
}

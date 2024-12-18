package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import org.joda.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.definition.AlertConfiguration;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSlot;
import app.aaps.pump.omnipod.eros.driver.definition.AlertType;
import app.aaps.pump.omnipod.eros.driver.definition.BeepRepeat;
import app.aaps.pump.omnipod.eros.driver.definition.BeepType;
import app.aaps.pump.omnipod.eros.driver.definition.TimerAlertTrigger;
import app.aaps.pump.omnipod.eros.driver.definition.UnitsRemainingAlertTrigger;

class ConfigureAlertsCommandTest {
    @Test
    void testEncoding() {
        Duration softExpirationTime = Duration.standardHours(72).minus(Duration.standardMinutes(1));
        AlertConfiguration alertConfiguration1 = new AlertConfiguration( //
                AlertType.EXPIRATION_ADVISORY_ALERT,
                AlertSlot.SLOT7, //
                true, //
                false, //
                Duration.standardHours(7), //
                new TimerAlertTrigger(softExpirationTime), //
                BeepType.BEEP_BEEP_BEEP, //
                BeepRepeat.EVERY_MINUTE_FOR_15_MINUTES);

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("79a410df0205"), //
                alertConfiguration1.getRawData());

        Duration hardExpirationTime = Duration.standardHours(79).minus(Duration.standardMinutes(1));
        AlertConfiguration alertConfiguration2 = new AlertConfiguration( //
                AlertType.SHUTDOWN_IMMINENT_ALARM,
                AlertSlot.SLOT2, //
                true, //
                false, //
                Duration.ZERO, //
                new TimerAlertTrigger(hardExpirationTime), //
                BeepType.BEEEEEEP, //
                BeepRepeat.EVERY_MINUTE_FOR_15_MINUTES);

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("280012830206"), //
                alertConfiguration2.getRawData());

        AlertConfiguration alertConfiguration3 = new AlertConfiguration( //
                AlertType.AUTO_OFF_ALARM,
                AlertSlot.SLOT0, //
                false, //
                true, //
                Duration.standardMinutes(15), //
                new TimerAlertTrigger(Duration.ZERO), //
                BeepType.BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP, //
                BeepRepeat.EVERY_MINUTE_FOR_15_MINUTES);

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("020f00000202"), //
                alertConfiguration3.getRawData());

        ConfigureAlertsCommand configureAlertsCommand = new ConfigureAlertsCommand( //
                0xfeb6268b, //
                Arrays.asList(alertConfiguration1, alertConfiguration2, alertConfiguration3));

        Assertions.assertArrayEquals( //
                ByteUtil.INSTANCE.fromHexString("1916feb6268b79a410df0205280012830206020f00000202"), //
                configureAlertsCommand.getRawData());
    }

    @Test
    void testLowReservoirAlert() {
        AlertConfiguration alertConfiguration = new AlertConfiguration(//
                AlertType.LOW_RESERVOIR_ALERT, //
                AlertSlot.SLOT4, //
                true, //
                false, //
                Duration.ZERO, //
                new UnitsRemainingAlertTrigger(10.0), //
                BeepType.BIP_BEEP_BIP_BEEP_BIP_BEEP_BIP_BEEP, //
                BeepRepeat.EVERY_MINUTE_FOR_3_MINUTES_REPEAT_EVERY_60_MINUTES);

        ConfigureAlertsCommand configureAlertsCommand = new ConfigureAlertsCommand( //
                0xae01a66c, //
                Collections.singletonList(alertConfiguration));

        Assertions.assertArrayEquals(
                ByteUtil.INSTANCE.fromHexString("190aae01a66c4c0000640102"), //
                configureAlertsCommand.getRawData());
    }
}

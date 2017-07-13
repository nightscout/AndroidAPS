package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import de.jotomo.ruffyscripter.RuffyScripter;

public class BolusCommand implements Command {
    private static final Logger log = LoggerFactory.getLogger(BolusCommand.class);

    private final double bolus;

    public BolusCommand(double bolus) {
        this.bolus = bolus;
    }

    @Override
    public CommandResult execute(RuffyScripter scripter) {
        try {
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
            enterBolusMenu(scripter);
            inputBolusAmount(scripter);
            SystemClock.sleep(500);
            verifyDisplayedBolusAmount(scripter);

            // confirm bolus
            scripter.pressCheckKey();

            // the pump displays the entered bolus and waits a bit to let user check and cancel
            scripter.waitForMenuToBeLeft(MenuType.BOLUS_ENTER);

            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Pump did not return to MAIN_MEU from BOLUS_ENTER to deliver bolus. "
                    + "Check pump manually, the bolus might not have been delivered.");

            // wait for bolus delivery to complete
            Double bolusRemaining = (Double) scripter.currentMenu.getAttribute(MenuAttribute.BOLUS_REMAINING);
            while (bolusRemaining != null) {
                log.debug("Delivering bolus, remaining: " + bolusRemaining);
                SystemClock.sleep(200);
                bolusRemaining = (Double) scripter.currentMenu.getAttribute(MenuAttribute.BOLUS_REMAINING);
            }

            // TODO what if we hit 'cartridge low' alert here? is it immediately displayed or after the bolus?
            // TODO how are error states reported back to the caller that occur outside of calls in genal? Low battery, low cartridge?

            // make sure no alert (occlusion, cartridge empty) has occurred.
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Bolus delivery did not complete as expected. "
                    + "Check pump manually, the bolus might not have been delivered.");

            return new CommandResult().success(true).enacted(true)
                    .message(String.format(Locale.US, "Delivered %02.1f U", bolus));
        } catch (CommandException e) {
            return e.toCommandResult();
        }
    }

    private void enterBolusMenu(RuffyScripter scripter) {
        scripter.navigateToMenu(MenuType.BOLUS_MENU);
        scripter.pressCheckKey();
        scripter.waitForMenuUpdate();
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
    }

    private void inputBolusAmount(RuffyScripter scripter) {
        // press 'up' once for each 0.1 U increment
        long steps = Math.round(bolus * 10);
        for (int i = 0; i < steps; i++) {
            scripter.pressUpKey();
            SystemClock.sleep(100);
        }
    }

    private void verifyDisplayedBolusAmount(RuffyScripter scripter) {
        double displayedBolus = (double) scripter.currentMenu.getAttribute(MenuAttribute.BOLUS);
        log.debug("Final bolus: " + displayedBolus);
        // TODO can't we just use BigDecimal? doubles aren't precise ...
        if (Math.abs(displayedBolus - bolus) > 0.001) {
            throw new CommandException().message("Failed to set correct bolus. Expected: " + bolus + ", actual: " + displayedBolus);
        }
    }
}

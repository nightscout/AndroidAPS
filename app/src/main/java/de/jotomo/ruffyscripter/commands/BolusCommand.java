package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.jotomo.ruffyscripter.RuffyScripter;

public class BolusCommand extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(BolusCommand.class);

    protected final double bolus;

    public BolusCommand(double bolus) {
        this.bolus = bolus;
    }

    @Override
    public List<String> validateArguments() {
        List<String> violations = new ArrayList<>();

        if (bolus <= 0 || bolus > 25) {
            violations.add("Requested bolus " + bolus + " out of limits (0-25)");
        }

        return violations;
    }

    @Override
    public CommandResult execute() {
        try {
            enterBolusMenu(scripter);

            inputBolusAmount(scripter);
            verifyDisplayedBolusAmount(scripter);

            // confirm bolus
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
            scripter.pressCheckKey();

            // the pump displays the entered bolus and waits a bit to let user check and cancel
            scripter.waitForMenuToBeLeft(MenuType.BOLUS_ENTER);

            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Pump did not return to MAIN_MEU from BOLUS_ENTER to deliver bolus. "
                            + "Check pump manually, the bolus might not have been delivered.");

            // wait for bolus delivery to complete; the remaining units to deliver are counted
            // down and are displayed on the main menu.
            Double bolusRemaining = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_REMAINING);
            while (bolusRemaining != null) {
                log.debug("Delivering bolus, remaining: " + bolusRemaining);
                SystemClock.sleep(200);
                if (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
                    throw new CommandException().success(false).enacted(true)
                            .message("Warning/error raised after bolus delivery started. " +
                                    "The treatment has been recorded, please check it against the " +
                                    "pumps records and delete it if it hasn't finished successfully.");
                }
                bolusRemaining = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS_REMAINING);
            }


            // TODO what if we hit 'cartridge low' alert here? is it immediately displayed or after the bolus?
            // TODO how are error states reported back to the caller that occur outside of calls in genal? Low battery, low cartridge?

            // make sure no alert (occlusion, cartridge empty) has occurred.
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Bolus delivery did not complete as expected. "
                            + "Check pump manually, the bolus might not have been delivered.");

            // read last bolus record; those menus display static data and therefore
            // only a single menu update is sent
            scripter.navigateToMenu(MenuType.MY_DATA_MENU);
            scripter.verifyMenuIsDisplayed(MenuType.MY_DATA_MENU);
            scripter.pressCheckKey();
            if (scripter.getCurrentMenu().getType() != MenuType.BOLUS_DATA) {
                scripter.waitForMenuUpdate();
            }

            if (!scripter.getCurrentMenu().attributes().contains(MenuAttribute.BOLUS)) {
                throw new CommandException().success(false).enacted(true)
                        .message("Bolus was delivered, but unable to confirm it with history record");
            }

            double lastBolusInHistory = (double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS);
            if (Math.abs(bolus - lastBolusInHistory) > 0.05) {
                throw new CommandException().success(false).enacted(true)
                        .message("Last bolus shows " + lastBolusInHistory
                                + " U delievered, but " + bolus + " U were requested");
            }
            log.debug("Bolus record in history confirms delivered bolus");

            // leave menu to go back to main menu
            scripter.pressCheckKey();
            scripter.waitForMenuToBeLeft(MenuType.BOLUS_DATA);
            scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU,
                    "Bolus was correctly delivered and checked against history, but we "
                            + "did not return the main menu successfully.");

            return new CommandResult().success(true).enacted(true)
                    .message(String.format(Locale.US, "Delivered %02.1f U", bolus));
        } catch (CommandException e) {
            return e.toCommandResult();
        }
    }

    private void enterBolusMenu(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        scripter.navigateToMenu(MenuType.BOLUS_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_MENU);
        scripter.pressCheckKey();
        scripter.waitForMenuUpdate();
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
    }

    private void inputBolusAmount(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        // press 'up' once for each 0.1 U increment
        long steps = Math.round(bolus * 10);
        for (int i = 0; i < steps; i++) {
            scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
            scripter.pressUpKey();
            SystemClock.sleep(100);
        }
        // Give the pump time to finish any scrolling that might still be going on, can take
        // up to 1100ms. Plus some extra time to be sure
        SystemClock.sleep(2000);
    }

    private void verifyDisplayedBolusAmount(RuffyScripter scripter) {
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        double displayedBolus = readDisplayedBolusAmount(scripter);
        log.debug("Final bolus: " + displayedBolus);
        if (Math.abs(displayedBolus - bolus) > 0.05) {
            throw new CommandException().message("Failed to set correct bolus. Expected: " + bolus + ", actual: " + displayedBolus);
        }

        // check again to ensure the displayed value hasn't change due to due scrolling taking extremely long
        SystemClock.sleep(2000);
        double refreshedDisplayedBolus = readDisplayedBolusAmount(scripter);
        if (Math.abs(displayedBolus - refreshedDisplayedBolus) > 0.05) {
            throw new CommandException().message("Failed to set bolus: bolus changed after input stopped from "
                    + displayedBolus + " -> " + refreshedDisplayedBolus);
        }
    }

    private double readDisplayedBolusAmount(RuffyScripter scripter) {
        // TODO v2 add timeout? Currently the command execution timeout would trigger if exceeded
        scripter.verifyMenuIsDisplayed(MenuType.BOLUS_ENTER);
        // bolus amount is blinking, so we need to make sure we catch it at the right moment
        Object amountObj = scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS);
        while (!(amountObj instanceof Double)) {
            scripter.waitForMenuUpdate();
            amountObj = scripter.getCurrentMenu().getAttribute(MenuAttribute.BOLUS);
        }
        return (double) amountObj;
    }

    @Override
    public String toString() {
        return "BolusCommand{" +
                "bolus=" + bolus +
                '}';
    }
}

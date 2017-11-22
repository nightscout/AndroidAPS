package de.jotomo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.Menu;
import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import de.jotomo.ruffy.spi.BasalProfile;

public class SetBasalProfileCommand extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(SetBasalProfileCommand.class);

    private final BasalProfile basalProfile;

    public SetBasalProfileCommand(BasalProfile basalProfile) {
        this.basalProfile = basalProfile;
    }

    @Override
    public void execute() {
        scripter.navigateToMenu(MenuType.BASAL_1_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.BASAL_1_MENU);
        scripter.pressCheckKey();

        // summary screen is shown; press menu to page through hours, wraps around to summary;
        scripter.verifyMenuIsDisplayed(MenuType.BASAL_TOTAL);
        for (int i = 0; i < 24; i++) {
            scripter.pressMenuKey();
            Menu menu = scripter.getCurrentMenu();
            while (menu.getType() != MenuType.BASAL_SET
                    || ((MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.BASAL_START)).getHour() != i) {
                scripter.waitForScreenUpdate();
                menu = scripter.getCurrentMenu();
            }
            scripter.verifyMenuIsDisplayed(MenuType.BASAL_SET);

            double requestedRate = basalProfile.hourlyRates[i];
            Boolean increasing = inputBasalRate(requestedRate);
            if (increasing != null) {
                verifyDisplayedRate(requestedRate, increasing);
            }

            log.debug("Set basal profile, hour " + i + ": " + requestedRate);
        }

        // move from hourly values to basal total
        scripter.pressCheckKey();
        scripter.verifyMenuIsDisplayed(MenuType.BASAL_TOTAL);

        // check total basal total on pump matches requested total
        Double pumpTotal = (Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.BASAL_TOTAL);
        Double requestedTotal = 0d;
        for (int i = 0; i < 24; i++) {
            requestedTotal += basalProfile.hourlyRates[i];
        }
        if (Math.abs(pumpTotal - requestedTotal) > 0.05) { // TODO leniency actually needed?
            throw new CommandException("Basal total of " + pumpTotal + " differs from requested total of " + requestedTotal);
        }

        // confirm entered basal rate
        scripter.pressCheckKey();

        scripter.returnToRootMenu();
        scripter.verifyRootMenuIsDisplayed();

        result.success(true).basalProfile(basalProfile);
    }

    // TODO boolean to indicate, up, down or neither? yikes
    private Boolean inputBasalRate(double requestedRate) {
        // 0.05 steps; jumps to 0.10 steps if buttons are kept pressed, so there's room for optimization
        double currentRate = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE);
        if (Math.abs(currentRate - requestedRate) < 0.01) {
            return null;
        }
        log.debug("Current rate: " + currentRate + ", requested: " + requestedRate);
        double change = requestedRate - currentRate;
        long steps = Math.round(change * 100);
        boolean increasing = steps > 0;
        log.debug("Pressing " + (increasing ? "up" : "down") + " " + Math.abs(steps) + " times");
        for (int i = 0; i < Math.abs(steps); i++) {
            scripter.verifyMenuIsDisplayed(MenuType.BASAL_SET);
            log.debug("Push #" + (i + 1) + "/" + Math.abs(steps));
            if (increasing) scripter.pressUpKey();
            else scripter.pressDownKey();
            SystemClock.sleep(50);
        }
        return increasing;
    }

    private void verifyDisplayedRate(double requestedRate, boolean increasingPercentage) {
        scripter.verifyMenuIsDisplayed(MenuType.BASAL_SET);
        // wait up to 5s for any scrolling to finish
        double displayedRate = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE);
        long timeout = System.currentTimeMillis() + 10 * 1000;
        while (timeout > System.currentTimeMillis()
                && ((increasingPercentage && displayedRate < requestedRate)
                || (!increasingPercentage && displayedRate > requestedRate))) {
            log.debug("Waiting for pump to process scrolling input for rate, current: "
                    + displayedRate + ", desired: " + requestedRate + ", scrolling "
                    + (increasingPercentage ? "up" : "down"));
            scripter.waitForScreenUpdate();
            displayedRate = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE);
        }
        log.debug("Final displayed basal rate: " + displayedRate);
        if (displayedRate != requestedRate) {
            throw new CommandException("Failed to set basal rate, requested: "
                    + requestedRate + ", actual: " + displayedRate);
        }

        // check again to ensure the displayed value hasn't change and scrolled past the desired
        // value due to due scrolling taking extremely long
        SystemClock.sleep(1000);
        scripter.verifyMenuIsDisplayed(MenuType.BASAL_SET);
        double refreshedDisplayedRate = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE);
        if (displayedRate != refreshedDisplayedRate) {
            throw new CommandException("Failed to set basal rate: " +
                    "percentage changed after input stopped from "
                    + displayedRate + " -> " + refreshedDisplayedRate);
        }
    }

    @Override
    public List<String> validateArguments() {
        ArrayList<String> violations = new ArrayList<>();
        if (basalProfile == null) {
            violations.add("No basal profile supplied");
        }

        return violations;
    }

    @Override
    public String toString() {
        return "SetBasalProfileCommand{" +
                "basalProfile=" + basalProfile +
                '}';
    }
}

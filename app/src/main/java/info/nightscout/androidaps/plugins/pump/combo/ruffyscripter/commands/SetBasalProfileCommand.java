package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

import android.os.SystemClock;

import org.monkey.d.ruffy.ruffy.driver.display.Menu;
import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.BasalProfile;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpState;

public class SetBasalProfileCommand extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(SetBasalProfileCommand.class);

    private final BasalProfile basalProfile;

    public SetBasalProfileCommand(BasalProfile basalProfile) {
        this.basalProfile = basalProfile;
    }

    @Override
    public void execute() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        if (scripter.readPumpStateInternal().unsafeUsageDetected == PumpState.UNSUPPORTED_BASAL_RATE_PROFILE) {
            throw new CommandException("Active basal rate profile != 1");
        }
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
            long change = inputBasalRate(requestedRate);
            if (change != 0) {
                verifyDisplayedRate(requestedRate, change);
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
        if (Math.abs(pumpTotal - requestedTotal) > 0.001) {
            throw new CommandException("Basal total of " + pumpTotal + " differs from requested total of " + requestedTotal);
        }

        // confirm entered basal rate
        scripter.pressCheckKey();
        scripter.verifyRootMenuIsDisplayed();

        result.success(true).basalProfile(basalProfile);
    }

    private long inputBasalRate(double requestedRate) {
        double currentRate = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE);
        log.debug("Current rate: " + currentRate + ", requested: " + requestedRate);
        // the pump changes steps size from 0.01 to 0.05 when crossing 1.00 U
        long steps = 0;
        if (currentRate == 0) {
            // edge case of starting from 0.00;
            steps = stepsToOne(0.05) - stepsToOne(requestedRate) + 1;
        } else {
            steps = stepsToOne(currentRate) - stepsToOne(requestedRate);
        }
        if (steps == 0) {
            return 0;
        }
        log.debug("Pressing " + (steps > 0 ? "up" : "down") + " " + Math.abs(steps) + " times");
        for (int i = 0; i < Math.abs(steps); i++) {
            scripter.verifyMenuIsDisplayed(MenuType.BASAL_SET);
            log.debug("Push #" + (i + 1) + "/" + Math.abs(steps));
            if (steps > 0) scripter.pressUpKey();
            else scripter.pressDownKey();
            SystemClock.sleep(50);
        }
        return steps;
    }

    /**
     * Steps required to go up to 1.0 (positive return value),
     * or down to 1.0 (negative return value).
     */
    private long stepsToOne(double rate) {
        double change = (1.0 - rate);
        if (rate > 1) return Math.round(change / 0.05);
        return Math.round(change / 0.01);
    }

    private void verifyDisplayedRate(double requestedRate, long change) {
        scripter.verifyMenuIsDisplayed(MenuType.BASAL_SET);
        // wait up to 5s for any scrolling to finish
        double displayedRate = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE);
        long timeout = System.currentTimeMillis() + 10 * 1000;
        while (timeout > System.currentTimeMillis()
                && ((change > 0 && requestedRate - displayedRate > 0.001) // displayedRate < requestedRate)
                || (change < 0 && displayedRate - requestedRate > 0.001))) { //displayedRate > requestedRate))) {
            log.debug("Waiting for pump to process scrolling input for rate, current: "
                    + displayedRate + ", desired: " + requestedRate + ", scrolling "
                    + (change > 0 ? "up" : "down"));
            scripter.waitForScreenUpdate();
            displayedRate = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE);
        }
        log.debug("Final displayed basal rate: " + displayedRate);
        if (Math.abs(displayedRate - requestedRate) > 0.001) {
            throw new CommandException("Failed to set basal rate, requested: "
                    + requestedRate + ", actual: " + displayedRate);
        }

        // check again to ensure the displayed value hasn't change and scrolled past the desired
        // value due to due scrolling taking extremely long
        SystemClock.sleep(1000);
        scripter.verifyMenuIsDisplayed(MenuType.BASAL_SET);
        double refreshedDisplayedRate = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE);
        if (Math.abs(displayedRate - refreshedDisplayedRate) > 0.001) {
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

package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.Menu;
import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.BasalProfile;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpState;

public class ReadBasalProfileCommand extends BaseCommand {
    private static final Logger log = LoggerFactory.getLogger(ReadBasalProfileCommand.class);

    @Override
    public void execute() {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        if (scripter.readPumpStateInternal().unsafeUsageDetected == PumpState.UNSUPPORTED_BASAL_RATE_PROFILE) {
            throw new CommandException("Active basal rate profile != 1");
        }
        scripter.navigateToMenu(MenuType.BASAL_1_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.BASAL_1_MENU);
        scripter.pressCheckKey();

        BasalProfile basalProfile = new BasalProfile();

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

            MenuTime startTime = (MenuTime) scripter.getCurrentMenu().getAttribute(MenuAttribute.BASAL_START);
            if (i != startTime.getHour()) {
                throw new CommandException("Attempting to read basal rate for hour " + i + ", but hour " + startTime.getHour() + " is displayed");
            }
            basalProfile.hourlyRates[i] = scripter.readBlinkingValue(Double.class, MenuAttribute.BASAL_RATE);
            log.debug("Read basal profile, hour " + i + ": " + basalProfile.hourlyRates[i]);
        }

        log.debug("Basal profile read: " + Arrays.toString(basalProfile.hourlyRates));

        scripter.returnToRootMenu();
        scripter.verifyRootMenuIsDisplayed();

        result.success(true).basalProfile(basalProfile);
    }

    @Override
    public String toString() {
        return "ReadBasalProfileCommand{}";
    }
}

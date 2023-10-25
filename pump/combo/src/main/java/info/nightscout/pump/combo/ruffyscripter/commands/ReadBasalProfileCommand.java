package info.nightscout.pump.combo.ruffyscripter.commands;

import androidx.annotation.NonNull;

import org.monkey.d.ruffy.ruffy.driver.display.Menu;
import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;
import org.monkey.d.ruffy.ruffy.driver.display.menu.MenuTime;

import java.util.Arrays;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.pump.combo.ruffyscripter.BasalProfile;
import info.nightscout.pump.combo.ruffyscripter.PumpState;

public class ReadBasalProfileCommand extends BaseCommand {
    private final AAPSLogger aapsLogger;

    public ReadBasalProfileCommand(AAPSLogger aapsLogger) {
        this.aapsLogger = aapsLogger;
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
            aapsLogger.debug(LTag.PUMP, "Read basal profile, hour " + i + ": " + basalProfile.hourlyRates[i]);
        }

        aapsLogger.debug(LTag.PUMP, "Basal profile read: " + Arrays.toString(basalProfile.hourlyRates));

        scripter.returnToRootMenu();
        scripter.verifyRootMenuIsDisplayed();

        result.success(true).basalProfile(basalProfile);
    }

    @NonNull @Override
    public String toString() {
        return "ReadBasalProfileCommand{}";
    }
}

package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.history.PumpError;

// TODO rename to ConfirmALarm(alarm) => logic in CP, just report back alarm, then explicitely confirm that one alarm.
// multiple alarms oncy occur for errors (battery/cartdige low/occlusion) => let ring.
public class TakeOverAlarmsCommand extends BaseCommand {
    @Override
    public CommandResult execute() {
        if (scripter.getCurrentMenu().getType() != MenuType.WARNING_OR_ERROR) {
            return new CommandResult().success(false).enacted(false).message("No alarm active on the pump");
        }
        while (scripter.getCurrentMenu().getType() == MenuType.WARNING_OR_ERROR) {
            new PumpError(System.currentTimeMillis(),
                    "",
                    // TODO
                    // codes unqiue across W/E?
//                    (int) currentMenu.getAttribute(MenuAttribute.WARNING),
//                    (int) currentMenu.getAttribute(MenuAttribute.ERROR),
                    (String) scripter.getCurrentMenu().getAttribute(MenuAttribute.MESSAGE));
        }
        // confirm alert
        scripter.verifyMenuIsDisplayed(MenuType.WARNING_OR_ERROR);
        scripter.pressCheckKey();
        // dismiss alert
        scripter.verifyMenuIsDisplayed(MenuType.WARNING_OR_ERROR);
        scripter.pressCheckKey();
        scripter.waitForMenuToBeLeft(MenuType.WARNING_OR_ERROR);

        PumpState pumpState = scripter.readPumpStateInternal();
        return new CommandResult()
                .success(true)
                .enacted(false /* well, no treatments were enacted ... */)
//                .message(pumpState.errorMsg) // todo yikes?
                .state(pumpState);
    }

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }
}

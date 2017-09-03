package info.nightscout.androidaps.plugins.PumpCombo.scripter.commands;

import java.util.List;

public class GetReservoirLevelCommand extends BaseCommand {
    @Override
    public CommandResult execute() {
        // TODO merge this into GetPumpHistory / GetFullPumpState; or maybe just have
        // GetPumpState and parameterize to just read the displayed menu, read reservoir level, read history?
        // TODO rough version, no error handling thought out

        // turn into a method; bolus commands needs this as a precheck, parameterize GetPumpState command
        // to include reservoir level if desired
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        scripter.pressCheckKey();
        scripter.waitForMenuToBeLeft(MenuType.MAIN_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.QUICK_INFO);
        int remainingInsulin = ((Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.REMAINING_INSULIN)).intValue();
        scripter.returnToMainMenu();
        return new CommandResult().success(true).enacted(false).reservoirLevel(remainingInsulin);
    }

    @Override
    public List<String> validateArguments() {
        // TODO stub
        return null;
    }
}

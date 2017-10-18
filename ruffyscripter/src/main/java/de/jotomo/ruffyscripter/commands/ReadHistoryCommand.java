package de.jotomo.ruffyscripter.commands;

import org.monkey.d.ruffy.ruffy.driver.display.MenuAttribute;
import org.monkey.d.ruffy.ruffy.driver.display.MenuType;

import java.util.Collections;
import java.util.List;

import de.jotomo.ruffy.spi.history.PumpHistory;
import de.jotomo.ruffy.spi.history.PumpHistoryRequest;
import de.jotomo.ruffy.spi.CommandResult;

public class ReadHistoryCommand extends BaseCommand {
    private final PumpHistoryRequest request;

    public ReadHistoryCommand(PumpHistoryRequest request) {
        this.request = request;
    }

    @Override
    public CommandResult execute() {
        PumpHistory history = new PumpHistory();
        if (request.reservoirLevel) readReservoirLevel(history);
        if (request.bolusHistory != PumpHistoryRequest.SKIP) readBolusHistory(history, request.bolusHistory);
        return new CommandResult().success(true).enacted(false).history(history);
        }

    private void readBolusHistory(PumpHistory history, long bolusHistory) {
    }

    private void readReservoirLevel(PumpHistory history) {
        scripter.verifyMenuIsDisplayed(MenuType.MAIN_MENU);
        scripter.pressCheckKey();
        scripter.waitForMenuToBeLeft(MenuType.MAIN_MENU);
        scripter.verifyMenuIsDisplayed(MenuType.QUICK_INFO);
        int remainingInsulin = ((Double) scripter.getCurrentMenu().getAttribute(MenuAttribute.REMAINING_INSULIN)).intValue();
        scripter.returnToMainMenu();
        history.reservoirLevel = remainingInsulin;
    }

    @Override
    public List<String> validateArguments() {
        return Collections.emptyList();
    }
}

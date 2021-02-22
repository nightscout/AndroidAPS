package info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.commands;

public class ConfirmAlertCommand extends BaseCommand {
    private final int warningCode;

    public ConfirmAlertCommand(int warningCode) {
        this.warningCode = warningCode;
    }

    @Override
    public void execute() {
        result.success(scripter.confirmAlert(warningCode, 5000));
    }

    @Override
    public boolean needsRunMode() {
        return false;
    }

    @Override
    public String toString() {
        return "ConfirmAlertCommand{" +
                "warningCode=" + warningCode +
                '}';
    }
}

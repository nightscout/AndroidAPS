package info.nightscout.androidaps.plugins.PumpCombo.scripter.commands;

/**
 * Draft.
 *
 * small bolus, don't check reservoir level beforehand ... ??
 * not cancellable
 * less of an issue if it fails
 * can be retried automatically
 */
public class SuperMicroBolusCommand extends BolusCommand {
    public SuperMicroBolusCommand(double bolus) {
        super(bolus);
    }
}

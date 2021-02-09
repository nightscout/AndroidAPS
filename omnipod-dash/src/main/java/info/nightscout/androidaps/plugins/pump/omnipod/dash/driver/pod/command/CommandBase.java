package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

abstract class CommandBase implements Command {
    CommandBase(CommandType commandType) {
    }

    @Override public CommandType getCommandType() {
        return null;
    }
}

package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

public interface Command {
    CommandType getCommandType();

    byte[] getEncoded();
}

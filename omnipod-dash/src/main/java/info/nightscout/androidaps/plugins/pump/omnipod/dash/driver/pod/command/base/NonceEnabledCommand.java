package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base;

public abstract class NonceEnabledCommand extends HeaderEnabledCommand {
    protected final int nonce;

    protected NonceEnabledCommand(CommandType commandType, int uniqueId, short sequenceNumber, boolean multiCommandFlag, int nonce) {
        super(commandType, uniqueId, sequenceNumber, multiCommandFlag);
        this.nonce = nonce;
    }

}

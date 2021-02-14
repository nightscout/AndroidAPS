package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base;

public abstract class NonceEnabledCommand extends HeaderEnabledCommand {
    protected final int nonce;

    protected NonceEnabledCommand(CommandType commandType, int address, short sequenceNumber, boolean multiCommandFlag, int nonce) {
        super(commandType, address, sequenceNumber, multiCommandFlag);
        this.nonce = nonce;
    }

}

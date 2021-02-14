package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base;

public abstract class NonceEnabledCommand extends HeaderEnabledCommand {
    protected final int nonce;

    protected NonceEnabledCommand(CommandType commandType, int address, short sequenceNumber, boolean multiCommandFlag, int nonce) {
        super(commandType, address, sequenceNumber, multiCommandFlag);
        this.nonce = nonce;
    }

    protected static abstract class NonceEnabledBuilder<T extends NonceEnabledBuilder<T, R>, R extends Command> extends HeaderEnabledBuilder<T, R> {
        protected Integer nonce;

        public final R build() {
            if (nonce == null) {
                throw new IllegalArgumentException("nonce can not be null");
            }
            return super.build();
        }

        public final T setNonce(int nonce) {
            this.nonce = nonce;
            return (T) this;
        }
    }
}

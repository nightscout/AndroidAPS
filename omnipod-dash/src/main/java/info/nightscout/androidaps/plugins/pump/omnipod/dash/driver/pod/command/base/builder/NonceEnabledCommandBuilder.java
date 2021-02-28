package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command;

public abstract class NonceEnabledCommandBuilder<T extends NonceEnabledCommandBuilder<T, R>, R extends Command> extends HeaderEnabledCommandBuilder<T, R> {
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

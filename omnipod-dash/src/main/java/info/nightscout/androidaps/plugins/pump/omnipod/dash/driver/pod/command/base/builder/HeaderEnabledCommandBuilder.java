package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command;

public abstract class HeaderEnabledCommandBuilder<T extends HeaderEnabledCommandBuilder<T, R>, R extends Command> implements CommandBuilder<R> {
    protected Integer address;
    protected Short sequenceNumber;
    protected boolean multiCommandFlag = false;

    public R build() {
        if (address == null) {
            throw new IllegalArgumentException("address can not be null");
        }
        if (sequenceNumber == null) {
            throw new IllegalArgumentException("sequenceNumber can not be null");
        }
        return buildCommand();
    }

    public final T setAddress(int address) {
        this.address = address;
        return (T) this;
    }

    public final T setSequenceNumber(short sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
        return (T) this;
    }

    public final T setMultiCommandFlag(boolean multiCommandFlag) {
        this.multiCommandFlag = multiCommandFlag;
        return (T) this;
    }

    protected abstract R buildCommand();
}

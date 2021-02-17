package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command;

public abstract class HeaderEnabledCommandBuilder<T extends HeaderEnabledCommandBuilder<T, R>, R extends Command> implements CommandBuilder<R> {
    protected Integer uniqueId;
    protected Short sequenceNumber;
    protected boolean multiCommandFlag = false;

    public R build() {
        if (uniqueId == null) {
            throw new IllegalArgumentException("uniqueId can not be null");
        }
        if (sequenceNumber == null) {
            throw new IllegalArgumentException("sequenceNumber can not be null");
        }
        return buildCommand();
    }

    public final T setUniqueId(int uniqueId) {
        this.uniqueId = uniqueId;
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

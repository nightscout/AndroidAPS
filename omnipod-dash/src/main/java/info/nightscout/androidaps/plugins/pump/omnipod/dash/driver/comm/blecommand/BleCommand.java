package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.blecommand;

import org.jetbrains.annotations.NotNull;

public abstract class BleCommand {
    private final byte[] data;

    public BleCommand(@NotNull BleCommandType type) {
        this.data = new byte[]{type.getValue()};
    }

    public BleCommand(@NotNull BleCommandType type, @NotNull byte[] payload) {
        int n = payload.length + 1;
        this.data = new byte[n];
        this.data[0] = type.getValue();
        System.arraycopy(payload, 0, data, 1, payload.length);
    }

    public byte[] asByteArray() {
        return this.data;
    }
}

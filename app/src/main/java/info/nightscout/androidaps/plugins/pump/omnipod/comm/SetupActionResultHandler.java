package info.nightscout.androidaps.plugins.pump.omnipod.comm;

@FunctionalInterface
public interface SetupActionResultHandler {
    void handle(SetupActionResult result);
}

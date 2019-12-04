package info.nightscout.androidaps.plugins.pump.omnipod.comm;

// TODO replace with Consumer when our min API level >= 24
@FunctionalInterface
public interface SetupActionResultHandler {
    void handle(SetupActionResult result);
}

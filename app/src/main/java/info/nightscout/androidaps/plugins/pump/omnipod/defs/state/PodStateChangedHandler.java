package info.nightscout.androidaps.plugins.pump.omnipod.defs.state;

@FunctionalInterface
public interface PodStateChangedHandler {
    void handle(PodSessionState podState);
}

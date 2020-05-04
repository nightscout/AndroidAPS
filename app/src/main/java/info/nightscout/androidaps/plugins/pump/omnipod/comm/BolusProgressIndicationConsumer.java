package info.nightscout.androidaps.plugins.pump.omnipod.comm;

// TODO replace with Consumer when our min API level >= 24
@FunctionalInterface
public interface BolusProgressIndicationConsumer {
    void accept(double estimatedUnitsDelivered, int percentage);
}

package info.nightscout.androidaps.plugins.pump.insight.descriptors;

public class AvailableBolusTypes {

    private boolean standardAvailable;
    private boolean extendedAvailable;
    private boolean multiwaveAvailable;

    public boolean isStandardAvailable() {
        return this.standardAvailable;
    }

    public void setStandardAvailable(boolean standardAvailable) {
        this.standardAvailable = standardAvailable;
    }

    public boolean isExtendedAvailable() {
        return this.extendedAvailable;
    }

    public void setExtendedAvailable(boolean extendedAvailable) {
        this.extendedAvailable = extendedAvailable;
    }

    public boolean isMultiwaveAvailable() {
        return this.multiwaveAvailable;
    }

    public void setMultiwaveAvailable(boolean multiwaveAvailable) {
        this.multiwaveAvailable = multiwaveAvailable;
    }

    public boolean isBolusTypeAvailable(BolusType bolusType) {
        switch (bolusType) {
            case STANDARD:
                return standardAvailable;
            case EXTENDED:
                return extendedAvailable;
            case MULTIWAVE:
                return multiwaveAvailable;
            default:
                return false;
        }
    }
}

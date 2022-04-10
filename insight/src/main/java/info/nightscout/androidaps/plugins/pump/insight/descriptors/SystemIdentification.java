package info.nightscout.androidaps.plugins.pump.insight.descriptors;

public class SystemIdentification {

    private String serialNumber;
    private long systemIdAppendix;
    private String manufacturingDate;

    public String getSerialNumber() {
        return this.serialNumber;
    }

    public long getSystemIdAppendix() {
        return this.systemIdAppendix;
    }

    public String getManufacturingDate() {
        return this.manufacturingDate;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public void setSystemIdAppendix(long systemIdAppendix) {
        this.systemIdAppendix = systemIdAppendix;
    }

    public void setManufacturingDate(String manufacturingDate) {
        this.manufacturingDate = manufacturingDate;
    }
}

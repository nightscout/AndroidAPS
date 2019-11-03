package info.nightscout.androidaps.plugins.pump.insight.app_layer;

public enum Service {

    CONNECTION((short) 0x0000, null),
    STATUS((short) 0x0100, null),
    HISTORY((short) 0x0200, null),
    CONFIGURATION((short) 0x0200, "u+5Fhz6Gw4j1Kkas"),
    PARAMETER((short) 0x0200, null),
    REMOTE_CONTROL((short) 0x0100, "MAbcV2X6PVjxuz+R");

    private short version;
    private String servicePassword;

    Service(short version, String servicePassword) {
        this.version = version;
        this.servicePassword = servicePassword;
    }

    public short getVersion() {
        return this.version;
    }

    public String getServicePassword() {
        return this.servicePassword;
    }

    public void setServicePassword(String servicePassword) {
        this.servicePassword = servicePassword;
    }
}

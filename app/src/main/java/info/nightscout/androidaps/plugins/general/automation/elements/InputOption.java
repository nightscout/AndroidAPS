package info.nightscout.androidaps.plugins.general.automation.elements;

public class InputOption {
    private int stringRes;
    private String value;

    public InputOption(int stringRes, String value) {
        this.stringRes = stringRes;
        this.value = value;
    }

    public int getStringRes() {
        return stringRes;
    }

    public String getValue() {
        return value;
    }
}

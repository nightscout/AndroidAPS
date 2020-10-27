package info.nightscout.androidaps.plugins.pump.medtronic.defs;

/**
 * Created by andy on 27.02.15.
 */
public enum PumpConfigurationGroup {
    General(1, "GROUP_GENERAL"), //
    Device(2, "GROUP_DEVICE"), //

    Insulin(3, "GROUP_INSULIN"), //

    Basal(4, "GROUP_BASAL"), //
    Bolus(5, "GROUP_BOLUS"), //
    Sound(6, "GROUP_SOUND"), //

    Other(20, "GROUP_OTHER"), //

    UnknownGroup(21, "GROUP_UNKNOWN"), //

    ; //

    static boolean translated;
    int code;
    String i18nKey;
    String translation;


    PumpConfigurationGroup(int code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }


    public String getTranslation() {
        return translation;
    }


    public void setTranslation(String translation) {
        this.translation = translation;
    }


    public int getCode() {
        return code;
    }


    public String getI18nKey() {
        return i18nKey;
    }


    public String getName() {
        return this.name();
    }

}

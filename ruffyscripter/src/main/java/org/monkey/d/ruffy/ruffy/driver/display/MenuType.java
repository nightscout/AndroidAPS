package org.monkey.d.ruffy.ruffy.driver.display;

/**
 * Created by fishermen21 on 22.05.17.
 */

public enum MenuType {
    MAIN_MENU(true),
    STOP_MENU(true),
    BOLUS_MENU(true),
    BOLUS_ENTER(false),
    EXTENDED_BOLUS_MENU(true),
    BOLUS_DURATION(false),
    MULTIWAVE_BOLUS_MENU(true),
    IMMEDIATE_BOLUS(false),
    TBR_MENU(true),
    MY_DATA_MENU(true),
    BASAL_MENU(true),
    BASAL_1_MENU(true),
    BASAL_2_MENU(true),
    BASAL_3_MENU(true),
    BASAL_4_MENU(true),
    BASAL_5_MENU(true),
    DATE_AND_TIME_MENU(true),
    ALARM_MENU(true),
    MENU_SETTINGS_MENU(true),
    BLUETOOTH_MENU(true),
    THERAPY_MENU(true),
    PUMP_MENU(true),
    QUICK_INFO(false),
    BOLUS_DATA(false),
    DAILY_DATA(false),
    TBR_DATA(false),
    ERROR_DATA(false),
    TBR_SET(false),
    TBR_DURATION(false),
    STOP(false),
    START_MENU(false),
    BASAL_TOTAL(false),
    BASAL_SET(false),
    WARNING_OR_ERROR(false),;

    private boolean maintype = false;
    MenuType(boolean b) {
        maintype=b;
    }

    public boolean isMaintype() {
        return maintype;
    }
}

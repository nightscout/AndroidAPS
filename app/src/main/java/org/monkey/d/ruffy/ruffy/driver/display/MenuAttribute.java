package org.monkey.d.ruffy.ruffy.driver.display;

/**
 * Created by fishermen21 on 22.05.17.
 */

public enum MenuAttribute {
    RUNTIME,//runtime of current operation, remaining time on main menu
    BOLUS,//double units
    BOLUS_REMAINING,//double units remain from current bolus
    TBR,//double 0-500%
    BASAL_RATE,//double units/h
    BASAL_SELECTED,//int selected basal profile
    LOW_BATTERY,//boolean low battery warning
    INSULIN_STATE,//int insulin warning 0 == no warning, 1== low, 2 == empty
    LOCK_STATE,//int keylock state 0==no lock, 1==unlocked, 2==locked
    MULTIWAVE_BOLUS,//double immediate bolus on multiwave
    BOLUS_TYPE,//BolusType, only history uses MULTIWAVE
    TIME,//time MenuTime
    REMAINING_INSULIN,//double units
    DATE,//date MenuDate
    CURRENT_RECORD,//int current record
    TOTAL_RECORD, //int total num record
    ERROR, //int errorcode
    WARNING, //int errorcode
    MESSAGE, //string errormessage
    DAILY_TOTAL, //double units
    BASAL_TOTAL, //double total basal
    BASAL_START, //time MenuTime the basalrate starts
    BASAL_END, // time MenuTime the basalrate ends
    DEBUG_TIMING, //double with timing infos
    WARANTY,  //boolean true if out of waranty
    ERROR_OR_WARNING, // set if menu in blink during error/warning
}

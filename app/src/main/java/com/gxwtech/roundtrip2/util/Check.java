package com.gxwtech.roundtrip2.util;

import com.gxwtech.roundtrip2.CommunicationService.Objects.Bolus;
import com.gxwtech.roundtrip2.CommunicationService.Objects.Integration;
import com.gxwtech.roundtrip2.CommunicationService.Objects.TempBasal;
import com.gxwtech.roundtrip2.RT2Const;

import java.util.Date;

import io.realm.Realm;

/**
 * Created by Tim on 16/06/2016.
 * Class that provides validation and safety checks
 */
public class Check {
    final static boolean DEBUG = true;

    public static String isNewTempBasalSafe(TempBasal tempBasal){
        String reply = "";

        if (isTreatmentTooOld(tempBasal.getStart_time()))   reply += "Treatment is older than " + RT2Const.safety.TREATMENT_MAX_AGE + " mins, rejected.";
        //is TBR supported?
        // TODO: 21/02/2016 perform checks here, return empty string for OK or text detailing the issue

        return reply;
    }

    public static String isCancelTempBasalSafe(TempBasal tempBasal, Integration integrationAPS, Realm realm){
        String reply = "";

        if (isTreatmentTooOld(tempBasal.getStart_time()))        reply += "Treatment is older than " + RT2Const.safety.TREATMENT_MAX_AGE + " mins, rejected. ";
        if (!isThisBasalLastActioned(integrationAPS, realm))     reply += "Current Running Temp Basal does not match this Cancel request, Temp Basal has not been canceled. ";
        //is TBR supported?
        // TODO: 21/02/2016 perform checks here, return empty string for OK or text detailing the issue

        return reply;
    }

    public static String isBolusSafeToAction(Bolus bolus){
        String reply = "";

        if (isTreatmentTooOld(bolus.getTimestamp()))        reply += "Treatment is older than " + RT2Const.safety.TREATMENT_MAX_AGE + " mins, rejected. ";
        // TODO: 16/06/2016 add additional Bolus safety checks here

        return reply;
    }

    public static boolean isPumpSupported(String expectedPump){
        //Do we support the pump requested?
        if (DEBUG) return true;

        switch (expectedPump){
            case "medtronic_absolute":
            case "medtronic_percent":
                return true;
            default:
                return false;
        }
    }

    public static boolean isRequestTooOld(Long date){
        //Is this request too old to action?
        Long ageInMins = (new Date().getTime() - date) /1000/60;
        if (ageInMins > RT2Const.safety.INCOMING_REQUEST_MAX_AGE){
            return true;
        } else {
            return false;
        }
    }

    public static boolean isTreatmentTooOld(Date date){
        //Is this treatment too old to action?
        Long ageInMins = (new Date().getTime() - date.getTime()) /1000/60;
        if (ageInMins > RT2Const.safety.TREATMENT_MAX_AGE){
            return true;
        } else {
            return false;
        }
    }

    private static boolean isThisBasalLastActioned(Integration integrationAPS, Realm realm){
        //Is this Basal the most recent one set to the pump?
        TempBasal lastActive        =   TempBasal.lastActive(realm);                                //Basal that is active or last active
        if (lastActive == null) return false;                                                       //No basal has ever been active

        Integration integration     =   Integration.getIntegration("aps_app","temp_basal",lastActive.getId(),realm);
        if (integration == null) return false;                                                      //We are not aware of any Basal set by this App

        if (integrationAPS.getRemote_id().equals(integration.getRemote_id())) {
            return true;
        } else {
            return false;                                                                           //Basal to cancel does not match the current active basal
        }
    }

}


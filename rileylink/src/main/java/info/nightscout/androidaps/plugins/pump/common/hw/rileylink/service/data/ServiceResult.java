package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data;

import android.os.Bundle;

/**
 * Created by geoff on 6/25/16.
 */
public class ServiceResult extends ServiceMessage {

    public static final int ERROR_MALFORMED_PUMP_RESPONSE = 1;
    public static final int ERROR_NULL_PUMP_RESPONSE = 2;
    public static final int ERROR_INVALID_PUMP_RESPONSE = 3;
    public static final int ERROR_PUMP_BUSY = 4;


    public ServiceResult() {
        init();
    }


    public ServiceResult(Bundle resultBundle) {
        if (resultBundle != null) {
            setMap(resultBundle);
        } else {
            init();
        }
    }


    public static final String getErrorDescription(int errorCode) {
        switch (errorCode) {
            case ERROR_MALFORMED_PUMP_RESPONSE:
                return "Malformed Pump Response";
            case ERROR_NULL_PUMP_RESPONSE:
                return "Null pump response";
            case ERROR_INVALID_PUMP_RESPONSE:
                return "Invalid pump response";
            case ERROR_PUMP_BUSY:
                return "A pump command session is already in progress";
            default:
                return "Unknown error code (" + errorCode + ")";
        }
    }


    @Override
    public void init() {
        super.init();
        map.putString("ServiceMessageType", "ServiceResult");
        setServiceResultType(this.getClass().getSimpleName());
        setResultError(0, "Uninitialized ServiceResult");
    }


    public String getServiceResultType() {
        return map.getString("ServiceResultType", "ServiceResult");
    }


    public void setServiceResultType(String serviceResultType) {
        map.putString("ServiceResultType", serviceResultType);
    }


    public void setResultOK() {
        map.putString("result", "OK");
    }


    public void setResultError(int errorCode) {
        setResultError(errorCode, getErrorDescription(errorCode));
    }


    public void setResultError(int errorCode, String errorDescription) {
        map.putString("result", "error");
        map.putInt("errorCode", errorCode);
        map.putString("errorDescription", errorDescription);
    }


    public boolean resultIsOK() {
        return ("OK".equals(map.getString("result", "")));
    }


    public String getErrorDescription() {
        return map.getString("errorDescription", "");
    }


    public String getResult() {
        return map.getString("result", "");
    }

}

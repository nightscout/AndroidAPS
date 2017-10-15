package info.nightscout.androidaps.plugins.OpenAPSMA;

import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.utils.ToastUtils;

/**
 * Created by adrian on 15/10/17.
 */


public class LoggerCallback extends ScriptableObject {

    private static Logger log = LoggerFactory.getLogger(DetermineBasalAdapterMAJS.class);

    static StringBuffer errorBuffer = new StringBuffer();
    static StringBuffer logBuffer = new StringBuffer();


    public LoggerCallback() {
        //empty constructor needed for Rhino
        errorBuffer = new StringBuffer();
        logBuffer = new StringBuffer();
    }

    @Override
    public String getClassName() {
        return "LoggerCallback";
    }

    public void jsConstructor() {
        //empty constructor on JS site; could work as setter
    }

    public void jsFunction_log(Object obj1) {
        log.debug(obj1.toString());
        logBuffer.append(obj1.toString());
        logBuffer.append(' ');
    }

    public void jsFunction_error(Object obj1) {
        log.error(obj1.toString());
        errorBuffer.append(obj1.toString());
        errorBuffer.append(' ');
    }



    public static String getScriptDebug(){
        String ret = "";
        if(errorBuffer.length() > 0){
            ret += "e:\n" + errorBuffer.toString();
        }
        if(ret.length() > 0 && logBuffer.length() > 0) ret += '\n';
        if(logBuffer.length() > 0){
            ret += "d:\n" + logBuffer.toString();
        }
        return ret;
    }
}
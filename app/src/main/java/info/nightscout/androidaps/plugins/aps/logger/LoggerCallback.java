package info.nightscout.androidaps.plugins.aps.logger;

import org.mozilla.javascript.ScriptableObject;

import javax.inject.Inject;

import info.nightscout.androidaps.db.StaticInjector;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;

/**
 * Created by adrian on 15/10/17.
 */


public class LoggerCallback extends ScriptableObject {

    @Inject
    AAPSLogger aapsLogger;

    private static StringBuffer errorBuffer = new StringBuffer();
    private static StringBuffer logBuffer = new StringBuffer();


    public LoggerCallback() {
        //empty constructor needed for Rhino
        errorBuffer = new StringBuffer();
        logBuffer = new StringBuffer();
        StaticInjector.Companion.getInstance().androidInjector().inject(this);
    }

    @Override
    public String getClassName() {
        return "LoggerCallback";
    }

    public void jsConstructor() {
        //empty constructor on JS site; could work as setter
    }

    public void jsFunction_log(Object obj1) {
        aapsLogger.debug(LTag.APS, obj1.toString().trim());
        logBuffer.append(obj1.toString());
    }

    public void jsFunction_error(Object obj1) {
        aapsLogger.error(LTag.APS, obj1.toString().trim());
        errorBuffer.append(obj1.toString());
    }


    public static String getScriptDebug() {
        String ret = "";
        if (errorBuffer.length() > 0) {
            ret += "e:\n" + errorBuffer.toString();
        }
        if (ret.length() > 0 && logBuffer.length() > 0) ret += '\n';
        if (logBuffer.length() > 0) {
            ret += "d:\n" + logBuffer.toString();
        }
        return ret;
    }
}

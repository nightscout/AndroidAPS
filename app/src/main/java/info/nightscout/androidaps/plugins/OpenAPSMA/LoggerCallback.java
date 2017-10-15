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


    public LoggerCallback() {
        //empty constructor needed for Rhino
    }

    @Override
    public String getClassName() {
        return "LoggerCallback";
    }

    public void jsConstructor() {
        //empty constructor on JS site; could work as setter
    }

    public void jsFunction_log(String s) {
        log.debug(s);
    }

    public void jsFunction_error(String s) {
        log.error(s);
    }
}
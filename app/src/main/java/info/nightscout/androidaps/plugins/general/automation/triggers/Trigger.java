package info.nightscout.androidaps.plugins.general.automation.triggers;


import android.content.Context;
import android.content.ContextWrapper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public abstract class Trigger {
    private static final Logger log = LoggerFactory.getLogger(Trigger.class);

    TriggerConnector connector = null;
    long lastRun;

    Trigger() {
    }

    public TriggerConnector getConnector() {
        return connector;
    }

    public abstract boolean shouldRun();


    public abstract String toJSON();

    /*package*/
    abstract Trigger fromJSON(String data);

    public abstract int friendlyName();

    public abstract String friendlyDescription();

    public abstract Optional<Integer> icon();

    public void executed(long time) {
        lastRun = time;
    }

    public long getLastRun() {
        return lastRun;
    }

    public abstract Trigger duplicate();

    public static Trigger instantiate(String json) {
        try {
            return instantiate(new JSONObject(json));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    @Nullable
    public static Trigger instantiate(JSONObject object) {
        try {
            String type = object.getString("type");
            JSONObject data = object.getJSONObject("data");
            Class clazz = Class.forName(type);
            return ((Trigger) clazz.newInstance()).fromJSON(data.toString());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | JSONException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        TextView title = new TextView(root.getContext());
        title.setText(friendlyName());
        root.addView(title);
    }

    @Nullable
    AppCompatActivity scanForActivity(Context cont) {
        if (cont == null)
            return null;
        else if (cont instanceof AppCompatActivity)
            return (AppCompatActivity) cont;
        else if (cont instanceof ContextWrapper)
            return scanForActivity(((ContextWrapper) cont).getBaseContext());

        return null;
    }
}

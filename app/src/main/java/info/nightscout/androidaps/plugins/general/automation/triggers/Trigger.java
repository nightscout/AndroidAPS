package info.nightscout.androidaps.plugins.general.automation.triggers;


import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;

public abstract class Trigger {

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
            e.printStackTrace();
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
            e.printStackTrace();
        }
        return null;
    }

    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        TextView title = new TextView(root.getContext());
        title.setText(friendlyName());
        root.addView(title);
    }

    @Nullable
    Activity scanForActivity(Context cont) {
        if (cont == null)
            return null;
        else if (cont instanceof Activity)
            return (Activity) cont;
        else if (cont instanceof ContextWrapper)
            return scanForActivity(((ContextWrapper) cont).getBaseContext());

        return null;
    }
}

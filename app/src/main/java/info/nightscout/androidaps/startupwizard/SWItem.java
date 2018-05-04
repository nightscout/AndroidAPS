package info.nightscout.androidaps.startupwizard;

import android.view.View;
import android.widget.LinearLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.startupwizard.events.EventSWUpdate;
import info.nightscout.utils.SP;

public class SWItem {
    private static Logger log = LoggerFactory.getLogger(SWItem.class);

    enum Type {
        NONE,
        URL,
        STRING,
        NUMBER,
        DECIMALNUMBER,
        CHECKBOX,
        RADIOBUTTON,
        PLUGIN
    }

    Type type;
    Integer label;
    Integer comment;
    int preferenceId;


    public SWItem(Type type) {
        this.type = type;
    }

    String getLabel() {
        return MainApp.gs(label);
    }

    String getComment() {
        if (comment != null)
            return MainApp.gs(comment);
        else
            return "";
    }

    Type getType() {
        return type;
    }

    public SWItem label(int label) {
        this.label = label;
        return this;
    }

    public SWItem comment(int comment) {
        this.comment = comment;
        return this;
    }

    SWItem preferenceId(int preferenceId) {
        this.preferenceId = preferenceId;
        return this;
    }

    public void save(String value) {
        SP.putString(preferenceId, value);
        MainApp.bus().post(new EventPreferenceChange(preferenceId));
        MainApp.bus().post(new EventSWUpdate());
    }

    public static LinearLayout generateLayout(View view) {
        LinearLayout layout = (LinearLayout) view.findViewById(view.getId());
        layout.removeAllViews();
        return layout;
    }

    public void generateDialog(View view, LinearLayout layout) {
    }
}

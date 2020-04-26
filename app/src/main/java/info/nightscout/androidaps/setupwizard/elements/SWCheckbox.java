package info.nightscout.androidaps.setupwizard.elements;

import android.content.Context;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.CheckBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by Rumen Georgiev on 5/9/2018.
 */

public class SWCheckbox extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWCheckbox.class);

    int labelsArray;
    int valuesArray;
    String label = "";
    int preferenceID;
    private CheckBox checkBox;

    public SWCheckbox() {
        super(Type.CHECKBOX);
    }

    public SWCheckbox option(String label, int preferenceID) {
        this.label = label;
        this.preferenceID = preferenceID;
        return this;
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();
        // Get if there is already value in SP
        Boolean previousValue;
        previousValue = SP.getBoolean(preferenceId, false);
        checkBox = new CheckBox(context);
        checkBox.setText(label);
        checkBox.setChecked(previousValue);
        checkBox.setVisibility(View.VISIBLE);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                ArrayList<PluginBase> pluginsInCategory;
                pluginsInCategory = MainApp.getSpecificPluginsList(PluginType.PUMP);
                PluginBase found = null;
                for (PluginBase p : pluginsInCategory) {
                    if (p.isEnabled(PluginType.PUMP) && found == null) {
                        found = p;
                    } else if (p.isEnabled(PluginType.PUMP)) {
                        // set others disabled
                        p.setPluginEnabled(PluginType.PUMP, false);
                    }
                }
                log.debug("Enabled pump plugin:"+found.getClass());
                save(checkBox.isChecked());
            }
        });
        layout.addView(checkBox);
        super.generateDialog(layout);
    }
    public void save(boolean value){
        SP.putBoolean(preferenceID, value);
    }
}

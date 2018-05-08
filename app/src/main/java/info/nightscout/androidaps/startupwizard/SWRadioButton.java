package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpCombo.ComboPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpPlugin;
import info.nightscout.utils.SP;

public class SWRadioButton extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWRadioButton.class);

    int labelsArray;
    int valuesArray;
    String groupLabel = "";
    private RadioGroup radioGroup;

    public SWRadioButton() {
        super(Type.RADIOBUTTON);
    }

    public SWRadioButton option(String groupLabel, int labels, int values) {
        this.groupLabel = groupLabel;
        this.labelsArray = labels;
        this.valuesArray = values;
        return this;
    }

    public String[] labels() {
        return MainApp.sResources.getStringArray(labelsArray);
    }

    public String[] values() {
        return MainApp.sResources.getStringArray(valuesArray);
    }

    @Override
    public void generateDialog(View view, LinearLayout layout) {
        Context context = view.getContext();
        // Get if there is already value in SP
        String previousValue = "none";
        if(preferenceId == R.string.key_virtualpump_uploadstatus) {
            Boolean booleanValue = SP.getBoolean(preferenceId, false);
            previousValue = booleanValue.toString();
        } else {
            previousValue = SP.getString(preferenceId, "none");
        }
        if(!groupLabel.equals("")) {
            TextView groupName = new TextView(context);
            groupName.setText(groupLabel);
            layout.addView(groupName);
        }
        radioGroup = new RadioGroup(context);
        radioGroup.clearCheck();
        radioGroup.setOrientation(LinearLayout.VERTICAL);
        radioGroup.setVisibility(View.VISIBLE);
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
        log.debug("Pump selected: DanaR:"+DanaRPlugin.getPlugin().isEnabled(PluginType.PUMP));
        log.debug("Pump selected: Virtual:"+VirtualPumpPlugin.getPlugin().isEnabled(PluginType.PUMP));
        log.debug("Pump selected: Combo:"+ ComboPlugin.getPlugin().isEnabled(PluginType.PUMP));
        log.debug("Pump selected: "+ found.getNameShort());
        for (int i = 0; i < labels().length; i++) {
            RadioButton rdbtn = new RadioButton(context);
            rdbtn.setId(View.generateViewId());
            rdbtn.setText(labels()[i]);
            if (previousValue.equals(values()[i]))
                rdbtn.setChecked(true);
            rdbtn.setTag(i);
            radioGroup.addView(rdbtn);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int i = (int) group.findViewById(checkedId).getTag();
            save(values()[i]);
        });
        layout.addView(radioGroup);
        super.generateDialog(view, layout);
    }
}

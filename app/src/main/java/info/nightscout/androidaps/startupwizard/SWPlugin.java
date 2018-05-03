package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventConfigBuilderChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.startupwizard.events.EventSWUpdate;

public class SWPlugin extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWPlugin.class);

    private PluginType pType;
    private RadioGroup radioGroup;

    public SWPlugin() {
        super(Type.PLUGIN);
    }

    public SWPlugin option(PluginType pType) {
        this.pType = pType;
        return this;
    }

    @Override
    public void generateDialog(View view, LinearLayout layout) {
        Context context = view.getContext();
        radioGroup = new RadioGroup(context);
        radioGroup.clearCheck();

        ArrayList<PluginBase> pluginsInCategory = MainApp.getSpecificPluginsList(pType);

        for (int row = 0; row < 1; row++) {
            radioGroup.setOrientation(LinearLayout.VERTICAL);
            radioGroup.setVisibility(View.VISIBLE);

            for (int i = 0; i < pluginsInCategory.size(); i++) {
                RadioButton rdbtn = new RadioButton(context);
                PluginBase p = pluginsInCategory.get(i);
                rdbtn.setId(View.generateViewId());
                rdbtn.setText(p.getName());
                if (p.isEnabled(pType))
                    rdbtn.setChecked(true);
                rdbtn.setTag(p);
                radioGroup.addView(rdbtn);
            }
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton rb = group.findViewById(checkedId);
            PluginBase plugin1 = (PluginBase) rb.getTag();
            plugin1.setPluginEnabled(pType, rb.isChecked());
            plugin1.setFragmentVisible(pType, rb.isChecked());
            ConfigBuilderFragment.processOnEnabledCategoryChanged(plugin1, pType);
            ConfigBuilderPlugin.getPlugin().storeSettings("SetupWizard");
            MainApp.bus().post(new EventConfigBuilderChange());
            MainApp.bus().post(new EventSWUpdate());
        });
        layout.addView(radioGroup);
    }
}

package info.nightscout.androidaps.plugins.general.automation.elements;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;

public class InputProfileName extends Element {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);
    ProfileStore profileStore;

    String profileName;

    public InputProfileName(String name) {
        super();
        this.profileName = name;
    }

    public InputProfileName(InputProfileName another) {
        super();
        profileName = another.getValue();
    }


    @Override
    public void addToLayout(LinearLayout root) {
        ArrayList<CharSequence> profileList = new ArrayList<>();
        profileStore = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile();
        if (profileStore == null) {
            log.error("ProfileStore is empty");
        } else {
            profileList = profileStore.getProfileList();
        }
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(root.getContext(),
                R.layout.spinner_centered, profileList);
        Spinner spinner = new Spinner(root.getContext());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        spinnerParams.setMargins(0, MainApp.dpToPx(4), 0, MainApp.dpToPx(4));
        spinner.setLayoutParams(spinnerParams);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    setValue(listNames().get(position).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinner.setSelection(0);
        LinearLayout l = new LinearLayout(root.getContext());
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        l.addView(spinner);
        root.addView(l);
    }

    public InputProfileName setValue(String name) {
        this.profileName = name;
        return this;
    }

    public String getValue() {
        return profileName;
    }

    public ArrayList<CharSequence> listNames(){
        ArrayList<CharSequence> profileList = new ArrayList<>();
        // profile
        profileStore = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile();
        if (profileStore == null) {
            log.error("ProfileStore is empty");
        } else {
            profileList = profileStore.getProfileList();
        }
        return profileList;
    }


}
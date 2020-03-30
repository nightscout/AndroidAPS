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
import info.nightscout.androidaps.logging.L;

public class DropdownMenu extends Element {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);
    private ArrayList<CharSequence> itemList;
    private String selected;

    public DropdownMenu(String name) {
        super();
        this.selected = name;
    }

    public DropdownMenu(DropdownMenu another) {
        super();
        selected = another.getValue();
    }


    @Override
    public void addToLayout(LinearLayout root) {
        if (itemList == null) {
            log.error("ItemList is empty!");
            itemList = new ArrayList<>();
        }
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(root.getContext(),
                R.layout.spinner_centered, itemList);
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
                setValue(itemList.get(position).toString());
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

    public DropdownMenu setValue(String name) {
        this.selected = name;
        return this;
    }

    public String getValue() {
        return selected;
    }

    public void setList(ArrayList<CharSequence> values){
        if (itemList == null)
            itemList = new ArrayList<>();
        log.debug("values size is "+values.size());
        itemList = new ArrayList<>(values);
        log.debug("items size is "+itemList.size());
    }

    // For testing only
    public void add(String item){
        if (itemList == null)
            itemList = new ArrayList<>();
        itemList.add(item);
        log.debug("Added " + item + "("+itemList.size()+")");
    }

}
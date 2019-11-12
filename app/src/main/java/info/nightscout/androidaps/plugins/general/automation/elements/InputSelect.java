package info.nightscout.androidaps.plugins.general.automation.elements;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class InputSelect extends Element {

    private InputOption selected;
    private ArrayList<InputOption> options;

    public InputSelect() {
        super();
    }

    public InputSelect(ArrayList<InputOption> options) {
        super();
        this.options = options;
    }

    public InputSelect(ArrayList<InputOption> options, InputOption selected) {
        super();
        this.options = options;
        this.selected = selected;
    }

    public InputSelect(InputSelect another) {
        super();
        options = another.options;
        selected = another.selected;
    }

    @Override
    public void addToLayout(LinearLayout root) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(root.getContext(),
                R.layout.spinner_centered, labels());
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
                setValue(options.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinner.setSelection(selected == null ? 0 : options.indexOf(selected));
        root.addView(spinner);
    }

    public String getValue() {
        return selected == null ? null : selected.getValue();
    }

    public InputSelect setValue(InputOption selected) {
        this.selected = selected;
        return this;
    }

    public InputSelect setValue(String selected) {
        for (InputOption o: options) {
            if (o.getValue().equals(selected)) {
                this.selected = o;
                break;
            }
        }
        return this;
    }

    private List<String> labels() {
        List<String> list = new ArrayList<>();
        for (InputOption o : options) {
            list.add(MainApp.gs(o.getStringRes()));
        }
        return list;
    }
}

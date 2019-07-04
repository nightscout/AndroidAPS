package info.nightscout.androidaps.plugins.general.automation.elements;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class ComparatorExists extends Element {
    public enum Compare {
        EXISTS,
        NOT_EXISTS;

        public @StringRes
        int getStringRes() {
            switch (this) {
                case EXISTS:
                    return R.string.exists;
                case NOT_EXISTS:
                    return R.string.notexists;
                default:
                    return R.string.unknown;
            }
        }

        public static List<String> labels() {
            List<String> list = new ArrayList<>();
            for (Compare c : Compare.values()) {
                list.add(MainApp.gs(c.getStringRes()));
            }
            return list;
        }
    }

    private Compare compare = Compare.EXISTS;

    public ComparatorExists() {
        super();
    }

    public ComparatorExists(ComparatorExists another) {
        super();
        compare = another.getValue();
    }

    @Override
    public void addToLayout(LinearLayout root) {
        Spinner spinner = new Spinner(root.getContext());
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(root.getContext(), android.R.layout.simple_spinner_item, Compare.labels());
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerArrayAdapter);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        spinnerParams.setMargins(0, MainApp.dpToPx(4), 0, MainApp.dpToPx(4));
        spinner.setLayoutParams(spinnerParams);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                compare = Compare.values()[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinner.setSelection(compare.ordinal());
        root.addView(spinner);

    }

    public Compare getValue() {
        return compare;
    }

    public ComparatorExists setValue(Compare compare) {
        this.compare = compare;
        return this;
    }

}

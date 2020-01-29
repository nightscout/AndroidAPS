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

public class Comparator extends Element {
    public enum Compare {
        IS_LESSER,
        IS_EQUAL_OR_LESSER,
        IS_EQUAL,
        IS_EQUAL_OR_GREATER,
        IS_GREATER,
        IS_NOT_AVAILABLE;

        public @StringRes
        int getStringRes() {
            switch (this) {
                case IS_LESSER:
                    return R.string.islesser;
                case IS_EQUAL_OR_LESSER:
                    return R.string.isequalorlesser;
                case IS_EQUAL:
                    return R.string.isequal;
                case IS_EQUAL_OR_GREATER:
                    return R.string.isequalorgreater;
                case IS_GREATER:
                    return R.string.isgreater;
                case IS_NOT_AVAILABLE:
                    return R.string.isnotavailable;
                default:
                    return R.string.unknown;
            }
        }

        public <T extends Comparable> boolean check(T obj1, T obj2) {
            if (obj1 == null || obj2 == null)
                return this.equals(IS_NOT_AVAILABLE);

            int comparison = obj1.compareTo(obj2);
            switch (this) {
                case IS_LESSER:
                    return comparison < 0;
                case IS_EQUAL_OR_LESSER:
                    return comparison <= 0;
                case IS_EQUAL:
                    return comparison == 0;
                case IS_EQUAL_OR_GREATER:
                    return comparison >= 0;
                case IS_GREATER:
                    return comparison > 0;
                default:
                    return false;
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

    private Compare compare = Compare.IS_EQUAL;

    public Comparator() {
        super();
    }

    public Comparator(Comparator another) {
        super();
        compare = another.getValue();
    }

    @Override
    public void addToLayout(LinearLayout root) {
        Spinner spinner = new Spinner(root.getContext());
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(root.getContext(), R.layout.spinner_centered, Compare.labels());
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

    public Comparator setValue(Compare compare) {
        this.compare = compare;
        return this;
    }

}

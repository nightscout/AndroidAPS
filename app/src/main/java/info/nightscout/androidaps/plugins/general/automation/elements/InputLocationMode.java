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

public class InputLocationMode extends Element {

    public enum Mode {
        INSIDE,
        OUTSIDE,
        GOING_IN,
        GOING_OUT;

        public @StringRes
        int getStringRes() {
            switch (this) {
                case INSIDE:
                    return R.string.location_inside;
                case OUTSIDE:
                    return R.string.location_outside;
                case GOING_IN:
                    return R.string.location_going_in;
                case GOING_OUT:
                    return R.string.location_going_out;
                default:
                    return R.string.unknown;
            }
        }

        public static List<String> labels() {
            List<String> list = new ArrayList<>();
            for (Mode c : Mode.values()) {
                list.add(MainApp.gs(c.getStringRes()));
            }
            return list;
        }

        public Mode fromString(String wanted){
            for (Mode c : Mode.values()) {
                if(c.toString() == wanted)
                    return c;
            }
            return null;
        }
    }

    private Mode mode;

    public InputLocationMode() {
        super();
        mode = Mode.INSIDE;
    }

    public InputLocationMode(InputLocationMode another) {
        super();
        this.mode = another.mode;
    }

    @Override
    public void addToLayout(LinearLayout root) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(root.getContext(),
                R.layout.spinner_centered, Mode.labels());
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
                setValue(Mode.values()[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinner.setSelection(this.getValue().ordinal());
        root.addView(spinner);

    }

    public Mode getValue() {
        return mode;
    }

    public InputLocationMode setValue(Mode mode) {
        this.mode = mode;
        return this;
    }


}

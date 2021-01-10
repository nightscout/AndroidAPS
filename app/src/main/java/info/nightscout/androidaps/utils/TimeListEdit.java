package info.nightscout.androidaps.utils;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.TextViewCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.utils.ui.NumberPicker;

/**
 * Created by mike on 29.12.2016.
 */

public class TimeListEdit {
    private final AAPSLogger aapsLogger;
    private final DateUtil dateUtil;

    private final int ONEHOURINSECONDS = 60 * 60;

    private final View[] intervals = new View[24];
    private final SpinnerHelper[] spinners = new SpinnerHelper[24];
    private final NumberPicker[] numberPickers1 = new NumberPicker[24];
    private final NumberPicker[] numberPickers2 = new NumberPicker[24];
    private final ImageView[] addButtons = new ImageView[24];
    private final ImageView[] removeButtons = new ImageView[24];
    private ImageView finalAdd;

    private final Context context;
    private final View view;
    private final int resLayoutId;
    private final String tagPrefix;
    private String label;
    private final JSONArray data1;
    private final JSONArray data2;
    private final double step;
    private final double min;
    private final double max;
    private final NumberFormat formatter;
    private final Runnable save;
    private LinearLayout layout;
    private TextView textlabel;
    private int inflatedUntil = -1;


    public TimeListEdit(
            Context context,
            AAPSLogger aapsLogger,
            DateUtil dateUtil,
            View view, int resLayoutId, String tagPrefix, String label, JSONArray data1, JSONArray data2, double min, double max, double step, NumberFormat formatter, Runnable save) {
        this.context = context;
        this.aapsLogger = aapsLogger;
        this.dateUtil = dateUtil;
        this.view = view;
        this.resLayoutId = resLayoutId;
        this.tagPrefix = tagPrefix;
        this.label = label;
        this.data1 = data1;
        this.data2 = data2;
        this.step = step;
        this.min = min;
        this.max = max;
        this.formatter = formatter;
        this.save = save;
        buildView();
    }

    private void buildView() {
        layout = view.findViewById(resLayoutId);
        layout.removeAllViewsInLayout();

        textlabel = new TextView(context);
        textlabel.setText(label);
        textlabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, 5, 0, 5);
        textlabel.setLayoutParams(llp);
        //textlabel.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.linearBlockBackground));
        TextViewCompat.setTextAppearance(textlabel, android.R.style.TextAppearance_Medium);
        layout.addView(textlabel);

        for (int i = 0; i < 24 && i < itemsCount(); i++) {
            inflateRow(i);
            inflatedUntil = i;
        }

        // last "plus" to append new interval
        float factor = layout.getContext().getResources().getDisplayMetrics().density;
        finalAdd = new ImageView(context);
        finalAdd.setImageResource(R.drawable.ic_add);
        LinearLayout.LayoutParams illp = new LinearLayout.LayoutParams((int) (35d * factor), (int) (35 * factor));
        illp.setMargins(0, 25, 0, 25); // llp.setMargins(left, top, right, bottom);
        illp.gravity = Gravity.CENTER;
        layout.addView(finalAdd);
        finalAdd.setLayoutParams(illp);
        finalAdd.setOnClickListener(view -> {
            addItem(itemsCount(), itemsCount() > 0 ? secondFromMidnight(itemsCount() - 1) + ONEHOURINSECONDS : 0, 0, 0);
            callSave();
            log();
            fillView();
        });

        fillView();
    }

    private void inflateRow(final int position) {

        LayoutInflater inflater = LayoutInflater.from(context);
        int resource = data2 == null ? R.layout.timelistedit_element : R.layout.timelistedit_element_vertical;
        View childView = intervals[position] = inflater.inflate(resource, layout, false);
        spinners[position] = new SpinnerHelper(childView.findViewById(R.id.timelistedit_time));
        numberPickers1[position] = childView.findViewById(R.id.timelistedit_edit1);
        numberPickers2[position] = childView.findViewById(R.id.timelistedit_edit2);
        addButtons[position] = childView.findViewById(R.id.timelistedit_add);
        removeButtons[position] = childView.findViewById(R.id.timelistedit_remove);

        addButtons[position].setOnClickListener(view -> {
            int seconds = secondFromMidnight(position);
            addItem(position, seconds, 0, 0);
            // for here for the rest of values
            for (int i = position + 1; i < itemsCount(); i++) {
                if (secondFromMidnight(i - 1) >= secondFromMidnight(i)) {
                    editItem(i, secondFromMidnight(i - 1) + ONEHOURINSECONDS, value1(i), value2(i));
                }
            }
            while (itemsCount() > 24 || secondFromMidnight(itemsCount() - 1) > 23 * ONEHOURINSECONDS)
                removeItem(itemsCount() - 1);
            callSave();
            log();
            fillView();
        });

        removeButtons[position].setOnClickListener(view -> {
            removeItem(position);
            callSave();
            log();
            fillView();
        });

        spinners[position].setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int selected, long id) {
                        int seconds = ((SpinnerAdapter) spinners[position].getAdapter()).valueForPosition(selected);
                        editItem(position, seconds, value1(position), value2(position));
                        log();
                        callSave();
                        fillView();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );

        numberPickers1[position].setTextWatcher(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                editItem(position, secondFromMidnight(position), SafeParse.stringToDouble(numberPickers1[position].getText()), value2(position));
                callSave();
                log();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });
        numberPickers1[position].setTag(tagPrefix + "-1-" + position);

        numberPickers2[position].setTextWatcher(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                editItem(position, secondFromMidnight(position), value1(position), SafeParse.stringToDouble(numberPickers2[position].getText()));
                callSave();
                log();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });
        numberPickers2[position].setTag(tagPrefix + "-2-" + position);

        layout.addView(childView);
    }

    private void fillView() {
        for (int i = 0; i < 24; i++) {
            if (i < itemsCount()) {
                intervals[i].setVisibility(View.VISIBLE);
                buildInterval(i);
            } else if (i <= inflatedUntil) {
                intervals[i].setVisibility(View.GONE);
            }
        }

        if (!(itemsCount() > 0 && secondFromMidnight(itemsCount() - 1) == 23 * ONEHOURINSECONDS)) {
            finalAdd.setVisibility(View.VISIBLE);
        } else {
            finalAdd.setVisibility(View.GONE);
        }
    }

    private void buildInterval(int i) {
        SpinnerHelper timeSpinner = spinners[i];
        final NumberPicker editText1 = numberPickers1[i];
        final NumberPicker editText2 = numberPickers2[i];


        int previous = i == 0 ? -1 * ONEHOURINSECONDS : secondFromMidnight(i - 1);
        int next = i == itemsCount() - 1 ? 24 * ONEHOURINSECONDS : secondFromMidnight(i + 1);
        if (i == 0) next = ONEHOURINSECONDS;
        fillSpinner(timeSpinner, secondFromMidnight(i), previous, next);

        editText1.setParams(value1(i), min, max, step, formatter, false, null);
        editText2.setParams(value2(i), min, max, step, formatter, false, null);

        if (data2 == null) {
            editText2.setVisibility(View.GONE);
        }


        if (itemsCount() == 1 || i == 0) {
            removeButtons[i].setVisibility(View.INVISIBLE);
        } else
            removeButtons[i].setVisibility(View.VISIBLE);

        if (itemsCount() >= 24 || secondFromMidnight(i) >= 82800) {
            addButtons[i].setVisibility(View.INVISIBLE);
        } else {
            addButtons[i].setVisibility(View.VISIBLE);
        }

    }

    class SpinnerAdapter extends ArrayAdapter<CharSequence> {
        List<Integer> values;

        SpinnerAdapter(@NonNull Context context, int resource, final @NonNull List<CharSequence> objects, final @NonNull List<Integer> values) {
            super(context, resource, objects);
            this.values = values;
        }

        int valueForPosition(int position) {
            return values.get(position);
        }
    }

    private void fillSpinner(final SpinnerHelper spinner, int secondsFromMidnight, int previous, int next) {
        int posInList = 0;
        ArrayList<CharSequence> timeList = new ArrayList<>();
        ArrayList<Integer> timeListValues = new ArrayList<>();
        int pos = 0;
        for (int t = previous + ONEHOURINSECONDS; t < next; t += ONEHOURINSECONDS) {
            timeList.add(dateUtil.timeStringFromSeconds(t));
            timeListValues.add(t);
            if (secondsFromMidnight == t) posInList = pos;
            pos++;
        }

        final SpinnerAdapter adapter = new SpinnerAdapter(context,
                R.layout.spinner_centered, timeList, timeListValues);
        spinner.setAdapter(adapter);
        spinner.setSelection(posInList, false);
        adapter.notifyDataSetChanged();
    }

    private int itemsCount() {
        return data1.length();
    }

    private int secondFromMidnight(int index) {
        try {
            JSONObject item = (JSONObject) data1.get(index);
            if (item.has("timeAsSeconds")) {
                int time = item.getInt("timeAsSeconds");
                if (index == 0 && time != 0) {
                    // fix the bug, every array must start with 0
                    item.put("timeAsSeconds", 0);
                    time = 0;
                }
                return time;
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return 0;
    }

    private double value1(int index) {
        try {
            JSONObject item = (JSONObject) data1.get(index);
            if (item.has("value")) {
                return item.getDouble("value");
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return 0d;
    }

    private double value2(int index) {
        if (data2 != null) {
            try {
                JSONObject item = (JSONObject) data2.get(index);
                if (item.has("value")) {
                    return item.getDouble("value");
                }
            } catch (JSONException e) {
                aapsLogger.error("Unhandled exception", e);
            }
        }
        return 0d;
    }

    private void editItem(int index, int timeAsSeconds, double value1, double value2) {
        try {
            String time;
            int hour = timeAsSeconds / 60 / 60;
            DecimalFormat df = new DecimalFormat("00");
            time = df.format(hour) + ":00";

            JSONObject newObject1 = new JSONObject();
            newObject1.put("time", time);
            newObject1.put("timeAsSeconds", timeAsSeconds);
            newObject1.put("value", value1);
            data1.put(index, newObject1);
            if (data2 != null) {
                JSONObject newObject2 = new JSONObject();
                newObject2.put("time", time);
                newObject2.put("timeAsSeconds", timeAsSeconds);
                newObject2.put("value", value2);
                data2.put(index, newObject2);
            }
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }

    }

    private void addItem(int index, int timeAsSeconds, double value1, double value2) {
        if (itemsCount() >= 24) return;
        if (itemsCount() > inflatedUntil) {
            layout.removeView(finalAdd);
            inflateRow(++inflatedUntil);
            layout.addView(finalAdd);
        }
        try {
            // shift data
            for (int i = data1.length(); i > index; i--) {
                data1.put(i, data1.get(i - 1));
                if (data2 != null)
                    data2.put(i, data2.get(i - 1));
            }
            // add new object
            editItem(index, timeAsSeconds, value1, value2);
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }

    }

    private void removeItem(int index) {
        data1.remove(index);
        if (data2 != null)
            data2.remove(index);
    }

    private void log() {
        for (int i = 0; i < data1.length(); i++) {
            aapsLogger.debug(i + ": @" + dateUtil.timeStringFromSeconds(secondFromMidnight(i)) + " " + value1(i) + (data2 != null ? " " + value2(i) : ""));
        }
    }

    private void callSave() {
        if (save != null) save.run();
    }

    public void updateLabel(String txt) {
        this.label = txt;
        if (textlabel != null)
            textlabel.setText(txt);
    }
}

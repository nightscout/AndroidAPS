package info.nightscout.utils;

import android.content.Context;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.TextViewCompat;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 29.12.2016.
 */

public class TimeListEdit {
    private static Logger log = LoggerFactory.getLogger(TimeListEdit.class);

    private final int ONEHOURINSECONDS = 60 * 60;

    private View[] intervals = new View[24];
    private SpinnerHelper[] spinners = new SpinnerHelper[24];
    private NumberPicker[] numberPickers1 = new NumberPicker[24];
    private NumberPicker[] numberPickers2 = new NumberPicker[24];
    private ImageView[] addButtons = new ImageView[24];
    private ImageView[] removeButtons = new ImageView[24];
    private ImageView finalAdd;

    private Context context;
    private View view;
    private int resLayoutId;
    private String label;
    private JSONArray data1;
    private JSONArray data2;
    private double step;
    private double min;
    private double max;
    private NumberFormat formatter;
    private Runnable save;
    private LinearLayout layout;
    private TextView textlabel;
    private int inflatedUntil = -1;


    public TimeListEdit(Context context, View view, int resLayoutId, String label, JSONArray data1, JSONArray data2, double min, double max, double step, NumberFormat formatter, Runnable save) {
        this.context = context;
        this.view = view;
        this.resLayoutId = resLayoutId;
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
        layout = (LinearLayout) view.findViewById(resLayoutId);
        layout.removeAllViews();

        textlabel = new TextView(context);
        textlabel.setText(label);
        textlabel.setGravity(Gravity.START);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.setMargins(10, 0, 0, 0); // llp.setMargins(left, top, right, bottom);
        textlabel.setLayoutParams(llp);
        textlabel.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.linearBlockBackground));
        TextViewCompat.setTextAppearance(textlabel, android.R.style.TextAppearance_Medium);
        layout.addView(textlabel);

        for (int i = 0; i < 24 && i < itemsCount(); i++) {
            inflateRow(i);
            inflatedUntil = i;
        }

        // last "plus" to append new interval
        finalAdd = new ImageView(context);
        finalAdd.setImageResource(R.drawable.add);
        LinearLayout.LayoutParams illp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        illp.setMargins(0, 25, 0, 25); // llp.setMargins(left, top, right, bottom);
        illp.gravity = Gravity.CENTER;
        layout.addView(finalAdd);
        finalAdd.setLayoutParams(illp);
        finalAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addItem(itemsCount(), itemsCount() > 0 ? secondFromMidnight(itemsCount() - 1) + ONEHOURINSECONDS : 0, 0, 0);
                callSave();
                log();
                fillView();
            }
        });

        fillView();
    }

    private void inflateRow(int i) {

        LayoutInflater inflater = LayoutInflater.from(context);
        View childview = intervals[i] = inflater.inflate(R.layout.timelistedit_element, layout, false);
        spinners[i] = new SpinnerHelper(childview.findViewById(R.id.timelistedit_time));
        numberPickers1[i] = (NumberPicker) childview.findViewById(R.id.timelistedit_edit1);
        numberPickers2[i] = (NumberPicker) childview.findViewById(R.id.timelistedit_edit2);
        addButtons[i] = (ImageView) childview.findViewById(R.id.timelistedit_add);
        removeButtons[i] = (ImageView) childview.findViewById(R.id.timelistedit_remove);

        final int fixedPos = i;
        addButtons[i].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int seconds = secondFromMidnight(fixedPos);
                addItem(fixedPos, seconds, 0, 0);
                // for here for the rest of values
                for (int i = fixedPos + 1; i < itemsCount(); i++) {
                    if (secondFromMidnight(i - 1) >= secondFromMidnight(i)) {
                        editItem(i, secondFromMidnight(i - 1) + ONEHOURINSECONDS, value1(i), value2(i));
                    }
                }
                while (itemsCount() > 24 || secondFromMidnight(itemsCount() - 1) > 23 * ONEHOURINSECONDS)
                    removeItem(itemsCount() - 1);
                callSave();
                log();
                fillView();
            }
        });

        removeButtons[i].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeItem(fixedPos);
                callSave();
                log();
                fillView();
            }
        });

        spinners[i].setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        int seconds = DateUtil.toSeconds(spinners[fixedPos].getSelectedItem().toString());
                        editItem(fixedPos, seconds, value1(fixedPos), value2(fixedPos));
                        log();
                        callSave();
                        fillView();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );

        numberPickers1[i].setTextWatcher(new TextWatcher() {
             @Override
             public void afterTextChanged(Editable s) {
                 editItem(fixedPos, secondFromMidnight(fixedPos), SafeParse.stringToDouble(numberPickers1[fixedPos].getText()), value2(fixedPos));
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


        numberPickers2[i].setTextWatcher(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                editItem(fixedPos, secondFromMidnight(fixedPos), value1(fixedPos), SafeParse.stringToDouble(numberPickers2[fixedPos].getText()));
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

        layout.addView(childview);
    }

    private void fillView() {
        for (int i = 0; i < 24; i++) {
            if (i < itemsCount()) {
                intervals[i].setVisibility(View.VISIBLE);
                buildInterval(i);
            } else if (i <= inflatedUntil){
                intervals[i].setVisibility(View.GONE);
            }
        }

        if (!(itemsCount() > 0 && secondFromMidnight(itemsCount() - 1) == 23 * ONEHOURINSECONDS)) {
            finalAdd.setVisibility(View.VISIBLE);
        } else {
            finalAdd.setVisibility(View.GONE);
        }
    }

    private View buildInterval(int i) {
        SpinnerHelper timeSpinner = spinners[i];
        View childview = intervals[i];
        final NumberPicker editText1 = numberPickers1[i];
        final NumberPicker editText2 = numberPickers2[i];


        int previous = i == 0 ? -1 * ONEHOURINSECONDS : secondFromMidnight(i - 1);
        int next = i == itemsCount() - 1 ? 24 * ONEHOURINSECONDS : secondFromMidnight(i + 1);
        if (i == 0) next = ONEHOURINSECONDS;
        fillSpinner(timeSpinner, secondFromMidnight(i), previous, next);

        editText1.setParams(value1(i), min, max, step, formatter, false);
        editText2.setParams(value2(i), min, max, step, formatter, false);

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

        return childview;
    }

    private void fillSpinner(final SpinnerHelper spinner, int secondsFromMidnight, int previous, int next) {
        int posInList = 0;
        ArrayList<CharSequence> timeList = new ArrayList<>();
        int pos = 0;
        for (int t = previous + ONEHOURINSECONDS; t < next; t += ONEHOURINSECONDS) {
            timeList.add(DateUtil.timeStringFromSeconds(t));
            if (secondsFromMidnight == t) posInList = pos;
            pos++;
        }

        final ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(context,
                R.layout.spinner_centered, timeList);
        spinner.setAdapter(adapter);
        final int finalPosInList = posInList;
        new Handler().postDelayed(new Runnable() {
            public void run() {
                spinner.setSelection(finalPosInList, false);
                adapter.notifyDataSetChanged();
            }
        }, 100);
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
            log.error("Unhandled exception", e);
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
            log.error("Unhandled exception", e);
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
                log.error("Unhandled exception", e);
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
                newObject1.put("time", time);
                newObject2.put("timeAsSeconds", timeAsSeconds);
                newObject2.put("value", value2);
                data2.put(index, newObject2);
            }
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }

    }

    private void addItem(int index, int timeAsSeconds, double value1, double value2) {
        if(itemsCount()>inflatedUntil) {
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
            log.error("Unhandled exception", e);
        }

    }

    private void removeItem(int index) {
        data1.remove(index);
        if (data2 != null)
            data2.remove(index);
    }

    private void log() {
        if (log.isDebugEnabled()) {
            for (int i = 0; i < data1.length(); i++) {
                log.debug(i + ": @" + DateUtil.timeStringFromSeconds(secondFromMidnight(i)) + " " + value1(i) + (data2 != null ? " " + value2(i) : ""));
            }
        }
    }

    private void callSave() {
        if (save != null) save.run();
    }

    public void updateLabel(String txt){
        this.label = txt;
        if(textlabel!=null)
            textlabel.setText(txt);
    }
}

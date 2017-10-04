package info.nightscout.utils;

import android.content.Context;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.TextViewCompat;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
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

    final int ONEHOURINSECONDS = 60 * 60;

    private LinearLayout layout;
    private View[] intervals = new View[24];
    private Spinner[] spinners = new Spinner[24];


    private Context context;
    private View view;
    private int resLayoutId;
    private String label;
    private JSONArray data1;
    private JSONArray data2;
    private double step;
    private NumberFormat formatter;
    private Runnable save;

    public TimeListEdit(Context context, View view, int resLayoutId, String label, JSONArray data1, JSONArray data2, double step, NumberFormat formatter, Runnable save) {
        this.context = context;
        this.view = view;
        this.resLayoutId = resLayoutId;
        this.label = label;
        this.data1 = data1;
        this.data2 = data2;
        this.step = step;
        this.formatter = formatter;
        this.save = save;
        buildView();
    }

    private void buildView() {
        log.debug("buildView start");
        layout = (LinearLayout) view.findViewById(resLayoutId);

        layout.removeAllViews();

        TextView textlabel = new TextView(context);
        textlabel.setText(label);
        textlabel.setGravity(Gravity.LEFT);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.setMargins(10, 0, 0, 0); // llp.setMargins(left, top, right, bottom);
        textlabel.setLayoutParams(llp);
        textlabel.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.linearBlockBackground));
        TextViewCompat.setTextAppearance(textlabel, android.R.style.TextAppearance_Medium);
        layout.addView(textlabel);

        for (int i = 0; i < itemsCount(); i++) {
            View childview = intervals[i] = buildInterval(i);
            layout.addView(childview);
        }

        if (!(itemsCount() > 0 && secondFromMidnight(itemsCount() - 1) == 23 * ONEHOURINSECONDS)) {
            ImageView imageView = new ImageView(context);
            imageView.setImageResource(R.drawable.add);
            LinearLayout.LayoutParams illp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            illp.setMargins(0, 25, 0, 25); // llp.setMargins(left, top, right, bottom);
            illp.gravity = Gravity.CENTER;
            layout.addView(imageView);
            imageView.setLayoutParams(illp);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    log.debug("gggggggg");
                    addItem(itemsCount(), itemsCount() > 0 ? secondFromMidnight(itemsCount() - 1) + ONEHOURINSECONDS : 0, 0, 0);
                    callSave();
                    log();
                    buildView();
                }
            });
        }
        log.debug("buildView end");
    }

    private View buildInterval(int i) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View childview = inflater.inflate(R.layout.timelistedit_element, layout, false);
        final Spinner timeSpinner = spinners[i] = (Spinner) childview.findViewById(R.id.timelistedit_time);
        int previous = i == 0 ? -1 * ONEHOURINSECONDS : secondFromMidnight(i - 1);
        int next = i == itemsCount() - 1 ? 24 * ONEHOURINSECONDS : secondFromMidnight(i + 1);
        if (i == 0) next = ONEHOURINSECONDS;
        fillSpinner(timeSpinner, secondFromMidnight(i), previous, next);

        final int fixedPos = i;
        final NumberPicker editText1 = (NumberPicker) childview.findViewById(R.id.timelistedit_edit1);

        TextWatcher tw1 = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                log.debug("aaaa");
                editItem(fixedPos, secondFromMidnight(fixedPos), SafeParse.stringToDouble(editText1.getText()), value2(fixedPos));
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
        };

        editText1.setParams(value1(i), 0.1d, 100d, step, formatter, false, tw1);

        final NumberPicker editText2 = (NumberPicker) childview.findViewById(R.id.timelistedit_edit2);

        TextWatcher tw2 = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                log.debug("bbbbb");
                editItem(fixedPos, secondFromMidnight(fixedPos), value1(fixedPos), SafeParse.stringToDouble(editText2.getText()));
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
        };
        editText2.setParams(value2(i), 0.1d, 100d, step, formatter, false, tw2);
        if (data2 == null) {
            editText2.setVisibility(View.GONE);
        }


        ImageView addbutton = (ImageView) childview.findViewById(R.id.timelistedit_add);
        ImageView removebutton = (ImageView) childview.findViewById(R.id.timelistedit_remove);

        if (itemsCount() == 1 || i == 0) {
            removebutton.setVisibility(View.INVISIBLE);
        }

        if (itemsCount() >= 24 || secondFromMidnight(i) >= 82800) {
            addbutton.setVisibility(View.INVISIBLE);
        }

        addbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                log.debug("ccccc");
                layout.removeAllViews();
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
                buildView();
            }
        });

        removebutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                log.debug("dddd");
                layout.removeAllViews();
                removeItem(fixedPos);
                callSave();
                log();
                buildView();
            }
        });

        addSpinnerListener(timeSpinner, i);
        return childview;
    }

    private void addSpinnerListener(final Spinner timeSpinner, final int fixedPos) {
        timeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                                  @Override
                                                  public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                                      log.debug("eeeeee");
                                                      int seconds = DateUtil.toSeconds(timeSpinner.getSelectedItem().toString());
                                                      editItem(fixedPos, seconds, value1(fixedPos), value2(fixedPos));
                                                      log();
                                                      callSave();
                                                  }

                                                  @Override
                                                  public void onNothingSelected(AdapterView<?> parent) {
                                                      log.debug("fffff");
                                                      editItem(fixedPos, 0, value1(fixedPos), value2(fixedPos));
                                                      log();
                                                      callSave();
                                                  }
                                              }
        );
    }

    private void fillSpinner(Spinner spinner, int secondsFromMidnight, int previous, int next) {
        int posInList = 0;
        ArrayList<CharSequence> timeList = new ArrayList<>();
        int pos = 0;
        for (int t = previous + ONEHOURINSECONDS; t < next; t += ONEHOURINSECONDS) {
            timeList.add(DateUtil.timeStringFromSeconds(t));
            if (secondsFromMidnight == t) posInList = pos;
            pos++;
        }

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(context,
                R.layout.spinner_centered, timeList);
        spinner.setAdapter(adapter);
        spinner.setSelection(posInList, false);
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
        for (int i = 0; i < data1.length(); i++) {
            log.debug(i + ": @" + DateUtil.timeStringFromSeconds(secondFromMidnight(i)) + " " + value1(i) + (data2 != null ? " " + value2(i) : ""));
        }
    }

    private void callSave() {
        if (save != null) save.run();
    }
}

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

    LinearLayout layout;

    Context context;
    View view;
    int resLayoutId;
    String label;
    JSONArray data1;
    JSONArray data2;
    NumberFormat formatter;
    Runnable save;

    public TimeListEdit(Context context, View view, int resLayoutId, String label, JSONArray data1, JSONArray data2, NumberFormat formatter, Runnable save) {
        this.context = context;
        this.view = view;
        this.resLayoutId = resLayoutId;
        this.label = label;
        this.data1 = data1;
        this.data2 = data2;
        this.formatter = formatter;
        this.save = save;
        buildView();
    }

    private void buildView() {
        LayoutInflater inflater = LayoutInflater.from(context);
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
            View childview = inflater.inflate(R.layout.timelistedit_element, layout, false);
            childview.setId(View.generateViewId());
            final Spinner timeSpinner = (Spinner) childview.findViewById(R.id.timelistedit_time);
            timeSpinner.setId(View.generateViewId());
            int previous = i == 0 ? -1 * ONEHOURINSECONDS : secondFromMidnight(i - 1);
            int next = i == itemsCount() - 1 ? 24 * ONEHOURINSECONDS : secondFromMidnight(i + 1);
            if (i == 0) next = ONEHOURINSECONDS;
            fillSpinner(timeSpinner, secondFromMidnight(i), previous, next);

            final EditText editText1 = (EditText) childview.findViewById(R.id.timelistedit_edit1);
            editText1.setId(View.generateViewId());
            fillNumber(editText1, value1(i));
            final EditText editText2 = ((EditText) childview.findViewById(R.id.timelistedit_edit2));
            fillNumber(editText2, value2(i));
            editText2.setId(View.generateViewId());
            if (data2 == null) {
                editText2.setVisibility(View.GONE);
            }


            ImageView addbutton = (ImageView) childview.findViewById(R.id.timelistedit_add);
            addbutton.setId(View.generateViewId());
            ImageView removebutton = (ImageView) childview.findViewById(R.id.timelistedit_remove);
            removebutton.setId(View.generateViewId());

            if (itemsCount() == 1 && i == 0) {
                removebutton.setVisibility(View.GONE);
            }

            if (itemsCount() >= 24) {
                addbutton.setVisibility(View.GONE);
            }

            final int fixedPos = i;
            addbutton.setOnClickListener(new View.OnClickListener() {
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
                    log();
                    buildView();
                }
            });

            removebutton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    removeItem(fixedPos);
                    log();
                    buildView();
                }
            });

            timeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                                      @Override
                                                      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                                          int seconds = DateUtil.toSeconds(timeSpinner.getSelectedItem().toString());
                                                          editItem(fixedPos, seconds, value1(fixedPos), value2(fixedPos));
                                                          log();
                                                          buildView();
                                                      }

                                                      @Override
                                                      public void onNothingSelected(AdapterView<?> parent) {
                                                          editItem(fixedPos, 0, value1(fixedPos), value2(fixedPos));
                                                          log();
                                                          buildView();
                                                      }
                                                  }
            );

            editText1.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    editItem(fixedPos, secondFromMidnight(fixedPos), SafeParse.stringToDouble(editText1.getText().toString()), value2(fixedPos));
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

            editText2.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    editItem(fixedPos, secondFromMidnight(fixedPos), value1(fixedPos), SafeParse.stringToDouble(editText2.getText().toString()));
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
                    addItem(itemsCount(), itemsCount() > 0 ? secondFromMidnight(itemsCount() - 1) + ONEHOURINSECONDS : 0, 0, 0);
                    log();
                    buildView();
                }
            });
        }

    }

    public void fillSpinner(Spinner spinner, int secondsFromMidnight, int previous, int next) {
        int posInList = 0;
        ArrayList<CharSequence> timeList = new ArrayList<>();
        int pos = 0;
        for (int t = previous + ONEHOURINSECONDS; t < next; t += ONEHOURINSECONDS) {
            timeList.add(DateUtil.timeString(DateUtil.toDate(t)));
            if (secondsFromMidnight == t) posInList = pos;
            pos++;
        }

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(context,
                android.R.layout.simple_spinner_item, timeList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(posInList, false);
    }

    public void fillNumber(EditText edit, Double value) {
        if (value.equals(0d))
            edit.setText("");
        else
            edit.setText(formatter.format(value));
    }

    public int itemsCount() {
        return data1.length();
    }

    public int secondFromMidnight(int index) {
        try {
            JSONObject item = (JSONObject) data1.get(index);
            if (item.has("timeAsSeconds")) {
                return item.getInt("timeAsSeconds");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public double value1(int index) {
        try {
            JSONObject item = (JSONObject) data1.get(index);
            if (item.has("value")) {
                return item.getDouble("value");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0d;
    }

    public double value2(int index) {
        if (data2 != null) {
            try {
                JSONObject item = (JSONObject) data2.get(index);
                if (item.has("value")) {
                    return item.getDouble("value");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return 0d;
    }

    public void editItem(int index, int timeAsSeconds, double value1, double value2) {
        try {
            JSONObject newObject1 = new JSONObject();
            newObject1.put("timeAsSeconds", timeAsSeconds);
            newObject1.put("value", value1);
            data1.put(index, newObject1);
            if (data2 != null) {
                JSONObject newObject2 = new JSONObject();
                newObject2.put("timeAsSeconds", timeAsSeconds);
                newObject2.put("value", value2);
                data2.put(index, newObject2);
            }
            if (save != null) save.run();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void addItem(int index, int timeAsSeconds, double value1, double value2) {
        try {
            // shift data
            for (int i = data1.length(); i > index; i--) {
                data1.put(i, data1.get(i - 1));
                if (data2 != null)
                    data2.put(i, data2.get(i - 1));
            }
            // add new object
            editItem(index, timeAsSeconds, value1, value2);
            if (save != null) save.run();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void removeItem(int index) {
        data1.remove(index);
        if (data2 != null)
            data2.remove(index);
        if (save != null) save.run();
    }

    void log() {
        for (int i = 0; i < data1.length(); i++) {
            int pos = 0;
            log.debug(i + ": @" + DateUtil.timeString(DateUtil.toDate(secondFromMidnight(i))) + " " + value1(i) + (data2 != null ? " " + value2(i) : ""));
        }
    }
}

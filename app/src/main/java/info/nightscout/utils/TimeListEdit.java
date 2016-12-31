package info.nightscout.utils;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
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

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 29.12.2016.
 */

public class TimeListEdit {
    private static Logger log = LoggerFactory.getLogger(TimeListEdit.class);

    LinearLayout layout;

    Context context;
    View view;
    int resLayoutId;
    String label;
    JSONArray data;
    NumberFormat formatter;
    String array1;
    String array2;

    public TimeListEdit(Context context, View view, int resLayoutId, String label, JSONArray data, String array1, String array2, NumberFormat formatter) {
        this.context = context;
        this.view = view;
        this.resLayoutId = resLayoutId;
        this.label = label;
        this.data = data;
        this.array1 = array1;
        this.array2 = array2;
        this.formatter = formatter;
        buildView();
    }

    private void buildView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        layout = (LinearLayout) view.findViewById(resLayoutId);

        layout.removeAllViews();

        TextView textlabel = new TextView(context);
        textlabel.setText(label);
        textlabel.setGravity(Gravity.LEFT);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.setMargins(10, 0, 0, 0); // llp.setMargins(left, top, right, bottom);
        textlabel.setLayoutParams(llp);
        layout.addView(textlabel);

        for (int i = 0; i < itemsCount(); i++) {
            View childview = inflater.inflate(R.layout.timelistedit_element, layout, false);
            final Spinner timeSpinner = (Spinner) childview.findViewById(R.id.timelistedit_time);
            int previous = i == 0 ? -1 * 60 * 60 : secondFromMidnight(i - 1);
            int next = i == itemsCount() - 1 ? 24 * 60 * 60 : secondFromMidnight(i + 1);
            if (i == 0) next = 60 * 60;
            fillSpinner(timeSpinner, secondFromMidnight(i), previous, next);

            final EditText editText1 = (EditText) childview.findViewById(R.id.timelistedit_edit1);
            fillNumber(editText1, value1(i));
            final EditText editText2 = ((EditText) childview.findViewById(R.id.timelistedit_edit2));
            fillNumber(editText2, value2(i));
            if (array2 == null) {
                editText2.setVisibility(View.GONE);
            }


            ImageView addbutton = (ImageView) childview.findViewById(R.id.timelistedit_add);
            ImageView removebutton = (ImageView) childview.findViewById(R.id.timelistedit_remove);


            final int fixedPos = i;
            addbutton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    addItem(fixedPos, 0, 0, 0);
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

        if (!(itemsCount() > 0 && secondFromMidnight(itemsCount() - 1) == 23 * 60 * 60)) {
            ImageView imageView = new ImageView(context);
            imageView.setImageResource(R.drawable.add);
            layout.addView(imageView);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    addItem(itemsCount(), itemsCount() > 0 ? secondFromMidnight(itemsCount() - 1) + 60 * 60 : 0, 0, 0);
                    log();
                    buildView();
                }
            });
        }

    }

    public void fillSpinner(Spinner spinner, int secondsFromMidnight, int previous, int next) {
        int posInList = 0;
        ArrayList<CharSequence> timeList = new ArrayList<>();
        DateFormat df = new SimpleDateFormat("HH:mm");
        int pos = 0;
        for (int t = previous +  60 * 60; t < next; t += 60 * 60) {
            timeList.add(df.format(DateUtil.toDate(t)));
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
        return data.length();
    }

    public int secondFromMidnight(int index) {
        try {
            JSONObject item = (JSONObject) data.get(index);
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
            JSONObject item = (JSONObject) data.get(index);
            if (item.has(array1)) {
                return item.getDouble(array1);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0d;
    }

    public double value2(int index) {
        try {
            JSONObject item = (JSONObject) data.get(index);
            if (item.has(array2)) {
                return item.getDouble(array2);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0d;
    }

    public void editItem(int index, int timeAsSeconds, double value1, double value2) {
        try {
            JSONObject newObject = new JSONObject();
            newObject.put("timeAsSeconds", timeAsSeconds);
            newObject.put(array1, value1);
            if (array2 != null)
                newObject.put(array2, value2);
            data.put(index, newObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void addItem(int index, int timeAsSeconds, double value1, double value2) {
        try {
            // shift data
            for (int i = data.length(); i > index; i--) {
                data.put(i, data.get(i - 1));
            }
            // add new object
            JSONObject newObject = new JSONObject();
            newObject.put("timeAsSeconds", timeAsSeconds);
            newObject.put(array1, value1);
            if (array2 != null)
                newObject.put(array2, value2);
            data.put(index, newObject);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void removeItem(int index) {
        data.remove(index);
    }

    void log() {
        DateFormat df = new SimpleDateFormat("HH:mm");
        for (int i = 0; i < data.length(); i++) {
            int pos = 0;
            log.debug(i + ": " + df.format(DateUtil.toDate(secondFromMidnight(i))) + " " + array1 + ": " + value1(i) + (array2 != null ? " " + array2 + ": " + value2(i) : ""));
        }
    }
}

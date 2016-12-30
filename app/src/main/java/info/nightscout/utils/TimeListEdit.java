package info.nightscout.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import info.nightscout.androidaps.R;

/**
 * Created by mike on 29.12.2016.
 */

public class TimeListEdit {

    LinearLayout layout;

    Context context;
    View view;
    int resLayoutId;
    JSONArray data;
    boolean per30min = false;
    String array1;
    String array2;

    public TimeListEdit(Context context, View view, int resLayoutId, JSONArray data, String array1, String array2, boolean per30min) {
        this.context = context;
        this.view = view;
        this.resLayoutId = resLayoutId;
        this.data = data;
        this.array1 = array1;
        this.array2 = array2;
        this.per30min = per30min;
        buildView();
    }

    private void buildView() {
        /*
        LayoutInflater inflater = LayoutInflater.from(context);
        layout = (LinearLayout) view.findViewById(resLayoutId);

        final EditText[] editTexts = new EditText[24];

        for (int i = 0; i < 24; i++) {
            View childview = inflater.inflate(R.layout.timelistedit_element, layout, false);
            ((TextView) childview.findViewById(R.id.basal_time_elem)).setText((i < 10 ? "0" : "") + i + ":00: ");

            ImageView copyprevbutton = (ImageView) childview.findViewById(R.id.basal_copyprev_elem);

            if (i == 0) {
                copyprevbutton.setVisibility(View.INVISIBLE);
                ;
            } else {
                final int j = i; //needs to be final to be passed to inner class.
                copyprevbutton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        editTexts[j].setText(editTexts[j - 1].getText());
                    }
                });
            }

            editTexts[i] = ((EditText) childview.findViewById(R.id.basal_edittext_elem));
            //editTexts[i].setText(DecimalFormatter.to2Decimal(values[i]));
            layout.addView(childview);

        }
        */
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
}

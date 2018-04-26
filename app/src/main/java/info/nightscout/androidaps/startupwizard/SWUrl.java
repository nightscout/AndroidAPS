package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.TextViewCompat;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class SWUrl extends SWItem {

    private List<String> labels;
    private List<String> values;
    private  String groupName;
    public SWUrl() {
        super(Type.URL);
    }

    public void setOptions(List<String> labels, List<String> values){
        this.labels = labels;
        this.values = values;
    }

    public void setName(String name){
        this.groupName = name;
    }

    @Override
    public void generateDialog(View view) {
        Context context = view.getContext();
        LinearLayout layout = (LinearLayout) view.findViewById(view.getId());
        layout.removeAllViews();

        for (int row = 0; row < 1; row++) {
            for (int i = 0; i < labels.size(); i++) {
                if(values.get(i) != "" && values.get(i) != null) {
                    EditText editText = new EditText(context);
                    editText.setId((row * 2) + i);
                    editText.setText(values.get(i));
                    editText.setInputType(InputType.TYPE_CLASS_TEXT);
                    editText.setMaxLines(1);
                    layout.addView(editText);
                }
            }
        }
    }

    public boolean isValid(){
        // checks for URL validation

        return true;
    }
}

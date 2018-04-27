package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SWUrl extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWUrl.class);
    private List<String> labels;
    private List<String> values;
    private  String groupName;
    public SWUrl() {
        super(Type.URL);
    }

    public void setName(String name){
        this.groupName = name;
    }

    public void setOptions(List<String> labels, List<String> values){
        this.labels = labels;
        this.values = values;
    }

    @Override
    public void generateDialog(View view, LinearLayout layout) {
        Context context = view.getContext();


        if(values.get(values.size()-1) != "" && values.get(values.size()-1) != null) {
            EditText editText = new EditText(context);
            editText.setId(1);
            // get the last value in list
            editText.setText(values.get(values.size()-1));
            editText.setInputType(InputType.TYPE_CLASS_TEXT);
            editText.setMaxLines(1);
            layout.addView(editText);
        }
    }


}

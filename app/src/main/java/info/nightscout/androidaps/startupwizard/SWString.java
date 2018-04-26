package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;


public class SWString extends SWItem {
    private List<String> labels;
    private List<String> values;
    private  String groupName;

    public SWString() {
        super(Type.STRING);
    }

    public void setName(String name){
        this.groupName = name;
    }

    @Override
    public void generateDialog(View view) {
        Context context = view.getContext();
        LinearLayout layout = (LinearLayout) view.findViewById(view.getId());
        layout.removeAllViews();

        TextView textlabel = new TextView(context);
        textlabel.setText(groupName);

        layout.addView(textlabel);

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

}

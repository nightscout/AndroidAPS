package info.nightscout.androidaps.startupwizard.elements;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SWEditString extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWEditString.class);

    public SWEditString() {
        super(Type.STRING);
    }

    @Override
    public void generateDialog(View view, LinearLayout layout) {
        Context context = view.getContext();

        TextView l = new TextView(context);
        l.setId(view.generateViewId());
        l.setText(label);
        l.setTypeface(l.getTypeface(), Typeface.BOLD);
        layout.addView(l);

        TextView c = new TextView(context);
        c.setId(view.generateViewId());
        c.setText(comment);
        c.setTypeface(c.getTypeface(), Typeface.ITALIC);
        layout.addView(c);

        EditText editText = new EditText(context);
        editText.setId(view.generateViewId());
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setMaxLines(1);
        layout.addView(editText);
        super.generateDialog(view, layout);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                save(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

}

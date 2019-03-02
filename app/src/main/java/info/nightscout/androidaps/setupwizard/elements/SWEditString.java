package info.nightscout.androidaps.setupwizard.elements;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.setupwizard.SWTextValidator;
import info.nightscout.androidaps.utils.SP;


public class SWEditString extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWEditString.class);

    private SWTextValidator validator = null;
    private int updateDelay = 0;

    public SWEditString() {
        super(Type.STRING);
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();

        TextView l = new TextView(context);
        l.setId(layout.generateViewId());
        l.setText(label);
        l.setTypeface(l.getTypeface(), Typeface.BOLD);
        layout.addView(l);

        TextView c = new TextView(context);
        c.setId(layout.generateViewId());
        c.setText(comment);
        c.setTypeface(c.getTypeface(), Typeface.ITALIC);
        layout.addView(c);

        EditText editText = new EditText(context);
        editText.setId(layout.generateViewId());
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setMaxLines(1);
        editText.setText(SP.getString(preferenceId, ""));
        layout.addView(editText);
        super.generateDialog(layout);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (validator != null && validator.isValid(s.toString()))
                    save(s.toString(), updateDelay);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public SWEditString preferenceId(int preferenceId) {
        this.preferenceId = preferenceId;
        return this;
    }

    public SWEditString validator(SWTextValidator validator) {
        this.validator = validator;
        return this;
    }

    public SWEditString updateDelay(int updateDelay) {
        this.updateDelay = updateDelay;
        return this;
    }
}

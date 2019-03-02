package info.nightscout.androidaps.setupwizard.elements;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.setupwizard.events.EventSWLabel;
import info.nightscout.androidaps.utils.SP;

public class SWEditUrl extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWEditUrl.class);

    private int updateDelay = 0;

    public SWEditUrl() {
        super(Type.URL);
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();

        TextView l = new TextView(context);
        l.setId(View.generateViewId());
        l.setText(label);
        l.setTypeface(l.getTypeface(), Typeface.BOLD);
        layout.addView(l);

        TextView c = new TextView(context);
        c.setId(View.generateViewId());
        c.setText(comment);
        c.setTypeface(c.getTypeface(), Typeface.ITALIC);
        layout.addView(c);

        EditText editText = new EditText(context);
        editText.setId(View.generateViewId());
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
                if (Patterns.WEB_URL.matcher(s).matches())
                    save(s.toString(), updateDelay);
                else
                    MainApp.bus().post(new EventSWLabel(MainApp.gs(R.string.error_url_not_valid)));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public SWEditUrl preferenceId(int preferenceId) {
        this.preferenceId = preferenceId;
        return this;
    }

    public SWEditUrl updateDelay(int updateDelay) {
        this.updateDelay = updateDelay;
        return this;
    }

}

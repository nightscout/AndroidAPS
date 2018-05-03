package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.startupwizard.events.EventSWUpdate;
import info.nightscout.utils.SP;

public class SWUrl extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWUrl.class);

    public SWUrl() {
        super(Type.URL);
    }

    @Override
    public void generateDialog(View view, LinearLayout layout) {
        Context context = view.getContext();

        TextView l = new TextView(context);
        l.setId(View.generateViewId());
        l.setText(label);
        layout.addView(l);

        TextView c = new TextView(context);
        c.setId(View.generateViewId());
        c.setText(comment);
        layout.addView(c);

        EditText editText = new EditText(context);
        editText.setId(View.generateViewId());
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setMaxLines(1);
        layout.addView(editText);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                SP.putString(preferenceId, s.toString());
                MainApp.bus().post(new EventPreferenceChange(preferenceId));
                MainApp.bus().post(new EventSWUpdate());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}

package info.nightscout.androidaps.plugins.general.automation.elements;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

public class InputString extends Element {

    TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            value = s.toString();
        }
    };

    private String value = "";

    public InputString() {
        super();
    }

    public InputString(InputString another) {
        super();
        value = another.getValue();
    }


    @Override
    public void addToLayout(LinearLayout root) {
        EditText editText = new EditText(root.getContext());
        editText.setText(value);
        editText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        editText.addTextChangedListener(textWatcher);
        root.addView(editText);
    }

    public InputString setValue(String value) {
        this.value = value;
        return this;
    }

    public String getValue() {
        return value;
    }

}

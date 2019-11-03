package info.nightscout.androidaps.plugins.general.automation.elements;

import android.widget.Button;
import android.widget.LinearLayout;

public class InputButton extends Element {

    String text;
    Runnable runnable;

    public InputButton(String text, Runnable runnable) {
        this.text = text;
        this.runnable = runnable;
    }

    @Override
    public void addToLayout(LinearLayout root) {
        Button button = new Button(root.getContext());

        button.setText(text);
        button.setOnClickListener(view -> {
            if (runnable != null)
                runnable.run();
        });

        root.addView(button);
    }
}

package info.nightscout.androidaps.setupwizard.elements;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.setupwizard.SWValidator;

public class SWButton extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWButton.class);

    private Runnable buttonRunnable;
    private int buttonText;
    private SWValidator buttonValidator;

    private Button button;

    public SWButton() {
        super(Type.BUTTON);
    }

    public SWButton text(int buttonText) {
        this.buttonText = buttonText;
        return this;
    }

    public SWButton action(Runnable buttonRunnable) {
        this.buttonRunnable = buttonRunnable;
        return this;
    }

    public SWButton visibility(SWValidator buttonValidator) {
        this.buttonValidator = buttonValidator;
        return this;
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();

        button = new Button(context);
        button.setText(buttonText);
        button.setOnClickListener((v) -> {
            if (buttonRunnable != null)
                buttonRunnable.run();
        });
        processVisibility();
        layout.addView(button);
        super.generateDialog(layout);
    }

    @Override
    public void processVisibility() {
        if (buttonValidator != null && !buttonValidator.isValid())
            button.setVisibility(View.GONE);
        else
            button.setVisibility(View.VISIBLE);
    }
}

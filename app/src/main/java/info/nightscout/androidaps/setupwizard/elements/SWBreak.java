package info.nightscout.androidaps.setupwizard.elements;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.setupwizard.SWValidator;


public class SWBreak extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWBreak.class);

    private TextView l;
    private SWValidator visibilityValidator;

    public SWBreak() {
        super(Type.TEXT);
    }

    public SWBreak visibility(SWValidator visibilityValidator) {
        this.visibilityValidator = visibilityValidator;
        return this;
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();

        l = new TextView(context);
        l.setId(View.generateViewId());
        l.setText("\n");
        layout.addView(l);

    }

    @Override
    public void processVisibility() {
        if (visibilityValidator != null && !visibilityValidator.isValid())
            l.setVisibility(View.GONE);
        else
            l.setVisibility(View.VISIBLE);
    }
}

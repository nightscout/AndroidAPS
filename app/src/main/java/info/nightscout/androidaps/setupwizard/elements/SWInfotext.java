package info.nightscout.androidaps.setupwizard.elements;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.setupwizard.SWValidator;


public class SWInfotext extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWInfotext.class);
    private String textLabel = null;

    private TextView l;
    private SWValidator visibilityValidator;

    public SWInfotext() {
        super(Type.TEXT);
    }

    public SWInfotext label(int label) {
        this.label = label;
        return this;
    }

    public SWInfotext label(String newLabel){
        this.textLabel = newLabel;
        return this;
    }

    public SWInfotext visibility(SWValidator visibilityValidator) {
        this.visibilityValidator = visibilityValidator;
        return this;
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();

        l = new TextView(context);
        l.setId(View.generateViewId());
        if(textLabel != null)
            l.setText(textLabel);
        else
            l.setText(label);
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

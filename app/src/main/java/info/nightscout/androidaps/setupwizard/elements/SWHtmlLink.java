package info.nightscout.androidaps.setupwizard.elements;

import android.content.Context;
import android.text.util.Linkify;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.setupwizard.SWValidator;


public class SWHtmlLink extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWHtmlLink.class);
    private String textLabel = null;

    private TextView l;
    private SWValidator visibilityValidator;

    public SWHtmlLink() {
        super(Type.HTMLLINK);
    }

    public SWHtmlLink label(int label) {
        this.label = label;
        return this;
    }

    public SWHtmlLink label(String newLabel){
        this.textLabel = newLabel;
        return this;
    }

    public SWHtmlLink visibility(SWValidator visibilityValidator) {
        this.visibilityValidator = visibilityValidator;
        return this;
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();

        l = new TextView(context);
        l.setId(View.generateViewId());
        l.setAutoLinkMask(Linkify.ALL);
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

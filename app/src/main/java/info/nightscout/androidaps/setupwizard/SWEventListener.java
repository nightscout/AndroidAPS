package info.nightscout.androidaps.setupwizard;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.setupwizard.elements.SWItem;
import info.nightscout.androidaps.setupwizard.events.EventSWLabel;


public class SWEventListener extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWEventListener.class);

    private int textLabel = 0;
    private String status = "";
    TextView textView;
    Object listener;
    SWDefinition definition;

    SWEventListener(SWDefinition definition) {
        super(Type.LISTENER);
        this.definition = definition;
        MainApp.bus().register(this);
    }

    public SWEventListener label(int newLabel) {
        this.textLabel = newLabel;
        return this;
    }

    public SWEventListener initialStatus(String status) {
        this.status = status;
        return this;
    }

    public SWEventListener listener(Object listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();

        textView = new TextView(context);
        textView.setId(layout.generateViewId());
        textView.setText((textLabel != 0 ? MainApp.gs(textLabel) : "") + " " + status);
        layout.addView(textView);
        if (listener != null)
            try {
                MainApp.bus().register(listener);
            } catch (Exception ignored) {}
    }

    @Subscribe
    public void onEventSWLabel(final EventSWLabel l) {
        status = l.label;
        if (definition != null && definition.getActivity() != null)
            definition.getActivity().runOnUiThread(() -> {
                if (textView != null)
                    textView.setText((textLabel != 0 ? MainApp.gs(textLabel) : "") + " " + status);
            });
    }

}

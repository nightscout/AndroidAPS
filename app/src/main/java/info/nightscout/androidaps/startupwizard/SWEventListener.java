package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.startupwizard.events.EventSWLabel;


public class SWEventListener extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWEventListener.class);

    TextView textView;
    Object listener;
    SWDefinition definition;

    SWEventListener(SWDefinition definition) {
        super(Type.LISTENER);
        this.definition = definition;
        MainApp.bus().register(this);
    }

    public SWEventListener listener(Object listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public void generateDialog(View view, LinearLayout layout) {
        Context context = view.getContext();

        textView = new TextView(context);
        textView.setId(view.generateViewId());
        layout.addView(textView);
        if (listener != null)
            MainApp.bus().register(listener);
    }

    @Subscribe
    public void onEventSWLabel(final EventSWLabel l) {
        if (definition != null && definition.getActivity() != null)
            definition.getActivity().runOnUiThread(() -> {
                if (textView != null)
                    textView.setText(l.label);
            });
    }

}

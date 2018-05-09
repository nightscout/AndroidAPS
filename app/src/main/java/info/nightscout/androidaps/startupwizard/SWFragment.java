package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.Common.SubscriberFragment;


public class SWFragment extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWFragment.class);

    SWDefinition definition;
    SubscriberFragment fragment;

    public SWFragment(SWDefinition definition) {
        super(Type.FRAGMENT);
        this.definition = definition;
    }

    public SWFragment add(SubscriberFragment fragment) {
        this.fragment = fragment;
        return this;
    }

    @Override
    public void generateDialog(View view, LinearLayout layout) {
        definition.getActivity().getSupportFragmentManager().beginTransaction().add(layout.getId(), fragment, fragment.getTag()).commit();
    }

}

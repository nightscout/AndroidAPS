package info.nightscout.androidaps.setupwizard.elements;

import androidx.fragment.app.Fragment;

import android.widget.LinearLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.setupwizard.SWDefinition;


public class SWFragment extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWFragment.class);

    SWDefinition definition;
    Fragment fragment;

    public SWFragment(SWDefinition definition) {
        super(Type.FRAGMENT);
        this.definition = definition;
    }

    public SWFragment add(Fragment fragment) {
        this.fragment = fragment;
        return this;
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        definition.getActivity().getSupportFragmentManager().beginTransaction().add(layout.getId(), fragment, fragment.getTag()).commit();
    }

}

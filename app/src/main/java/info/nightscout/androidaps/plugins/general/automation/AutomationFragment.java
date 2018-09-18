package info.nightscout.androidaps.plugins.general.automation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;

public class AutomationFragment extends SubscriberFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.automation_fragment, container, false);
        return view;
    }

    @Override
    protected void updateGUI() {

    }
}

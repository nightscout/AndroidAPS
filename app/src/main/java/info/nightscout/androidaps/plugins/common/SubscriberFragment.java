package info.nightscout.androidaps.plugins.common;

import androidx.fragment.app.Fragment;

import info.nightscout.androidaps.MainApp;

abstract public class SubscriberFragment extends Fragment {
    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
        updateGUI();
    }

    protected abstract void updateGUI();
}

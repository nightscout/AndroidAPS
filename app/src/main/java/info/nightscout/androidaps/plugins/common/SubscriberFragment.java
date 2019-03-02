package info.nightscout.androidaps.plugins.common;

import android.support.v4.app.Fragment;

import butterknife.Unbinder;
import info.nightscout.androidaps.MainApp;

abstract public class SubscriberFragment extends Fragment {
    protected Unbinder unbinder;

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

    @Override public synchronized void onDestroyView() {
        super.onDestroyView();
        if (unbinder != null)
            unbinder.unbind();
    }


    protected abstract void updateGUI();
}

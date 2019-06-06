package info.nightscout.androidaps.plugins.general.tidepool;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader;
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolDoUpload;
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolResetData;
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolUpdateGUI;
import info.nightscout.androidaps.utils.SP;

public class TidepoolJavaFragment extends SubscriberFragment {
    private TextView logTextView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tidepool_fragment, container, false);

        Button login = view.findViewById(R.id.tidepool_login);
        login.setOnClickListener(v1 -> {
            TidepoolUploader.INSTANCE.doLogin();
        });
        Button uploadnow = view.findViewById(R.id.tidepool_uploadnow);
        uploadnow.setOnClickListener(v2 -> MainApp.bus().post(new EventTidepoolDoUpload()));
        Button removeall = view.findViewById(R.id.tidepool_removeall);
        removeall.setOnClickListener(v3 -> {
            MainApp.bus().post(new EventTidepoolResetData());
        });
        Button resetStart = view.findViewById(R.id.tidepool_resertstart);
        resetStart.setOnClickListener(v4 -> {
            SP.putLong(R.string.key_tidepool_last_end, 0);
        });

        logTextView = view.findViewById(R.id.tidepool_log);

        return view;
    }

    @Subscribe
    public void onStatusEvent(final EventTidepoolUpdateGUI ignored) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                TidepoolPlugin.INSTANCE.updateLog();
                logTextView.setText(TidepoolPlugin.INSTANCE.getTextLog());
            });
    }
}
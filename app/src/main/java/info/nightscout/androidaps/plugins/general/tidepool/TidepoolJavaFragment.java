package info.nightscout.androidaps.plugins.general.tidepool;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.RxBus;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader;
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolDoUpload;
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolResetData;
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolUpdateGUI;
import info.nightscout.androidaps.utils.SP;

public class TidepoolJavaFragment extends SubscriberFragment {
    private TextView logTextView;
    private TextView statusTextView;
    private ScrollView scrollView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tidepool_fragment, container, false);

        view.findViewById(R.id.tidepool_login).setOnClickListener(v1 -> TidepoolUploader.INSTANCE.doLogin(false));
        view.findViewById(R.id.tidepool_uploadnow).setOnClickListener(v2 -> RxBus.INSTANCE.send(new EventTidepoolDoUpload()));
        view.findViewById(R.id.tidepool_removeall).setOnClickListener(v3 -> RxBus.INSTANCE.send(new EventTidepoolResetData()));
        view.findViewById(R.id.tidepool_resertstart).setOnClickListener(v4 -> SP.putLong(R.string.key_tidepool_last_end, 0));

        logTextView = view.findViewById(R.id.tidepool_log);
        statusTextView = view.findViewById(R.id.tidepool_status);
        scrollView = view.findViewById(R.id.tidepool_logscrollview);

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
                statusTextView.setText(TidepoolUploader.INSTANCE.getConnectionStatus().name());
                logTextView.setText(TidepoolPlugin.INSTANCE.getTextLog());
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            });
    }
}
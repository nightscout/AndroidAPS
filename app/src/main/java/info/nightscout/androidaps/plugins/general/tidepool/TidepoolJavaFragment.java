package info.nightscout.androidaps.plugins.general.tidepool;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.common.SubscriberFragment;
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader;
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolDoUpload;
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolResetData;

public class TidepoolJavaFragment extends SubscriberFragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.tidepool_fragment, container, false);

        Button login = view.findViewById(R.id.tidepool_login);
        login.setOnClickListener(v -> {
            TidepoolUploader.INSTANCE.doLogin();
        });
        Button uploadnow = view.findViewById(R.id.tidepool_uploadnow);
        uploadnow.setOnClickListener(v -> {
            MainApp.bus().post(new EventTidepoolDoUpload());
        });
        Button removeall = view.findViewById(R.id.tidepool_removeall);
        removeall.setOnClickListener(v -> {
            MainApp.bus().post(new EventTidepoolResetData());
        });
        return view;
    }

    @Override
    protected void updateGUI() {

    }
}

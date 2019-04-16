package info.nightscout.androidaps.plugins.general.overview.dialogs;


import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.services.AlarmSoundService;

public class ErrorDialog extends DialogFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(ErrorDialog.class);
    Button muteButton;
    Button okButton;
    TextView statusView;
    ErrorHelperActivity helperActivity;

    static String status;
    static String title;
    static int soundId;

    public ErrorDialog() {
        super();
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSound(int soundId) {
        this.soundId = soundId;
    }

    public void setHelperActivity(ErrorHelperActivity activity) {
        this.helperActivity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(title);
        View view = inflater.inflate(R.layout.overview_error_dialog, container, false);
        muteButton = (Button) view.findViewById(R.id.overview_error_mute);
        okButton = (Button) view.findViewById(R.id.overview_error_ok);
        statusView = (TextView) view.findViewById(R.id.overview_error_status);
        muteButton.setOnClickListener(this);
        okButton.setOnClickListener(this);
        setCancelable(false);

        startAlarm();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusView.setText(status);
    }

    @Override
    public void dismiss() {
        super.dismissAllowingStateLoss();
        if (helperActivity != null) {
            helperActivity.finish();
        }
        stopAlarm();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overview_error_mute:
                log.debug("Error dialog mute button pressed");
                stopAlarm();
                break;
            case R.id.overview_error_ok:
                log.debug("Error dialog ok button pressed");
                dismiss();
                break;
        }
    }

    private void startAlarm() {
        Intent alarm = new Intent(MainApp.instance().getApplicationContext(), AlarmSoundService.class);
        alarm.putExtra("soundid", soundId);
        MainApp.instance().startService(alarm);
    }

    private void stopAlarm() {
        Intent alarm = new Intent(MainApp.instance().getApplicationContext(), AlarmSoundService.class);
        MainApp.instance().stopService(alarm);
    }
}

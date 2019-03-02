package info.nightscout.androidaps.plugins.general.overview.Dialogs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import info.nightscout.androidaps.R;

public class MessageDialog extends DialogFragment implements View.OnClickListener {

    private static Logger log = LoggerFactory.getLogger(MessageDialog.class);
    // Button muteButton;
    Button okButton;
    TextView statusView;
    MessageHelperActivity helperActivity;

    static String status;
    static String title;


    // static int soundId;

    public MessageDialog() {
        super();
    }


    public void setStatus(String status) {
        this.status = status;
    }


    public void setTitle(String title) {
        this.title = title;
    }


    public void setHelperActivity(MessageHelperActivity activity) {
        this.helperActivity = activity;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getDialog().setTitle(title);
        View view = inflater.inflate(R.layout.overview_message_dialog, container, false);
        // muteButton = (Button)view.findViewById(R.id.overview_error_mute);
        okButton = (Button)view.findViewById(R.id.overview_message_ok);
        statusView = (TextView)view.findViewById(R.id.overview_message_status);
        // muteButton.setOnClickListener(this);
        okButton.setOnClickListener(this);
        setCancelable(false);

        // startAlarm();
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
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {

            case R.id.overview_message_ok:
                log.debug("Error dialog ok button pressed");
                dismiss();
                break;
        }
    }

}

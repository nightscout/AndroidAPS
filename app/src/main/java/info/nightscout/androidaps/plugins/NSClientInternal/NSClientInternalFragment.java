package info.nightscout.androidaps.plugins.NSClientInternal;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientRestart;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientUpdateGUI;
import info.nightscout.utils.SP;

public class NSClientInternalFragment extends SubscriberFragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(NSClientInternalFragment.class);

    private TextView logTextView;
    private TextView queueTextView;
    private TextView urlTextView;
    private TextView statusTextView;
    private TextView clearlog;
    private TextView restart;
    private TextView delivernow;
    private TextView clearqueue;
    private TextView showqueue;
    private ScrollView logScrollview;
    private CheckBox autoscrollCheckbox;
    private CheckBox pausedCheckbox;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.nsclientinternal_fragment, container, false);

            logScrollview = (ScrollView) view.findViewById(R.id.nsclientinternal_logscrollview);
            autoscrollCheckbox = (CheckBox) view.findViewById(R.id.nsclientinternal_autoscroll);
            autoscrollCheckbox.setChecked(NSClientInternalPlugin.getPlugin().autoscroll);
            autoscrollCheckbox.setOnCheckedChangeListener(this);
            pausedCheckbox = (CheckBox) view.findViewById(R.id.nsclientinternal_paused);
            pausedCheckbox.setChecked(NSClientInternalPlugin.getPlugin().paused);
            pausedCheckbox.setOnCheckedChangeListener(this);
            logTextView = (TextView) view.findViewById(R.id.nsclientinternal_log);
            queueTextView = (TextView) view.findViewById(R.id.nsclientinternal_queue);
            urlTextView = (TextView) view.findViewById(R.id.nsclientinternal_url);
            statusTextView = (TextView) view.findViewById(R.id.nsclientinternal_status);

            clearlog = (TextView) view.findViewById(R.id.nsclientinternal_clearlog);
            clearlog.setOnClickListener(this);
            clearlog.setPaintFlags(clearlog.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            restart = (TextView) view.findViewById(R.id.nsclientinternal_restart);
            restart.setOnClickListener(this);
            restart.setPaintFlags(restart.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            delivernow = (TextView) view.findViewById(R.id.nsclientinternal_delivernow);
            delivernow.setOnClickListener(this);
            delivernow.setPaintFlags(delivernow.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            clearqueue = (TextView) view.findViewById(R.id.nsclientinternal_clearqueue);
            clearqueue.setOnClickListener(this);
            clearqueue.setPaintFlags(clearqueue.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            showqueue = (TextView) view.findViewById(R.id.nsclientinternal_showqueue);
            showqueue.setOnClickListener(this);
            showqueue.setPaintFlags(showqueue.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

            updateGUI();
            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.nsclientinternal_restart:
                MainApp.bus().post(new EventNSClientRestart());
                Answers.getInstance().logCustom(new CustomEvent("NSClientRestart"));
                break;
            case R.id.nsclientinternal_delivernow:
                NSClientInternalPlugin.getPlugin().resend("GUI");
                Answers.getInstance().logCustom(new CustomEvent("NSClientDeliverNow"));
                break;
            case R.id.nsclientinternal_clearlog:
                NSClientInternalPlugin.getPlugin().clearLog();
                break;
            case R.id.nsclientinternal_clearqueue:
                final Context context = getContext();
                AlertDialog.Builder builder = new AlertDialog.Builder(context);

                builder.setTitle(this.getContext().getString(R.string.confirmation));
                builder.setMessage("Clear queue? All data in queue will be lost!");
                builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        UploadQueue.clearQueue();
                        updateGUI();
                        Answers.getInstance().logCustom(new CustomEvent("NSClientClearQueue"));
                    }
                });
                builder.setNegativeButton(getString(R.string.cancel), null);
                builder.show();
                break;
            case R.id.nsclientinternal_showqueue:
                MainApp.bus().post(new EventNSClientNewLog("QUEUE", NSClientInternalPlugin.getPlugin().queue().textList()));
                Answers.getInstance().logCustom(new CustomEvent("NSClientShowQueue"));
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.nsclientinternal_paused:
                SP.putBoolean(R.string.key_nsclientinternal_paused, isChecked);
                NSClientInternalPlugin.getPlugin().paused = isChecked;
                MainApp.bus().post(new EventPreferenceChange(R.string.key_nsclientinternal_paused));
                updateGUI();
                Answers.getInstance().logCustom(new CustomEvent("NSClientPause"));
                break;
            case R.id.nsclientinternal_autoscroll:
                SP.putBoolean(R.string.key_nsclientinternal_autoscroll, isChecked);
                NSClientInternalPlugin.getPlugin().autoscroll = isChecked;
                updateGUI();
                break;
        }
    }

    @Subscribe
    public void onStatusEvent(final EventNSClientUpdateGUI ev) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    NSClientInternalPlugin.updateLog();
                    logTextView.setText(NSClientInternalPlugin.textLog);
                    if (NSClientInternalPlugin.getPlugin().autoscroll) {
                        logScrollview.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                    urlTextView.setText(NSClientInternalPlugin.getPlugin().url());
                    Spanned queuetext = Html.fromHtml(MainApp.sResources.getString(R.string.queue) + " <b>" + UploadQueue.size() + "</b>");
                    queueTextView.setText(queuetext);
                    statusTextView.setText(NSClientInternalPlugin.getPlugin().status);
                }
            });
    }

}

package info.nightscout.androidaps.plugins.NSClientInternal;


import android.app.Activity;
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

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientRestart;
import info.nightscout.androidaps.plugins.NSClientInternal.events.EventNSClientUpdateGUI;
import info.nightscout.utils.SP;

public class NSClientInternalFragment extends Fragment implements FragmentBase, View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private static Logger log = LoggerFactory.getLogger(NSClientInternalFragment.class);

    static NSClientInternalPlugin nsClientInternalPlugin;

    static public NSClientInternalPlugin getPlugin() {
        if (nsClientInternalPlugin == null) {
            nsClientInternalPlugin = new NSClientInternalPlugin();
        }
        return nsClientInternalPlugin;
    }

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

    String status = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.nsclientinternal_fragment, container, false);

        logScrollview = (ScrollView) view.findViewById(R.id.nsclientinternal_logscrollview);
        autoscrollCheckbox = (CheckBox) view.findViewById(R.id.nsclientinternal_autoscroll);
        autoscrollCheckbox.setChecked(getPlugin().autoscroll);
        autoscrollCheckbox.setOnCheckedChangeListener(this);
        pausedCheckbox = (CheckBox) view.findViewById(R.id.nsclientinternal_paused);
        pausedCheckbox.setChecked(getPlugin().paused);
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
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.nsclientinternal_restart:
                MainApp.bus().post(new EventNSClientRestart());
                break;
            case R.id.nsclientinternal_delivernow:
                getPlugin().resend("GUI");
                break;
            case R.id.nsclientinternal_clearlog:
                getPlugin().clearLog();
                break;
            case R.id.nsclientinternal_clearqueue:
                getPlugin().queue().clearQueue();
                break;
            case R.id.nsclientinternal_showqueue:
                MainApp.bus().post(new EventNSClientNewLog("QUEUE", getPlugin().queue().textList()));
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.nsclientinternal_paused:
                SP.putBoolean(R.string.key_nsclientinternal_paused, isChecked);
                getPlugin().paused = isChecked;
                MainApp.bus().post(new EventPreferenceChange(R.string.key_nsclientinternal_paused));
                updateGUI();
                break;
            case R.id.nsclientinternal_autoscroll:
                SP.putBoolean(R.string.key_nsclientinternal_autoscroll, isChecked);
                getPlugin().autoscroll = isChecked;
                updateGUI();
                break;
        }
    }

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

    @Subscribe
    public void onStatusEvent(final EventNSClientUpdateGUI ev) {
        updateGUI();
    }

    private void updateGUI() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    logTextView.setText(getPlugin().textLog);
                    if (getPlugin().autoscroll) {
                        logScrollview.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                    urlTextView.setText(getPlugin().url());
                    Spanned queuetext = Html.fromHtml(MainApp.sResources.getString(R.string.queue) + " <b>" + getPlugin().queue().size() + "</b>");
                    queueTextView.setText(queuetext);
                    statusTextView.setText(getPlugin().status);
                }
            });
    }


}

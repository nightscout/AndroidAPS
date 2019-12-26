package info.nightscout.androidaps.plugins.general.nsclient;


import android.graphics.Paint;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.SP;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class NSClientFragment extends Fragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private CompositeDisposable disposable = new CompositeDisposable();

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
        View view = inflater.inflate(R.layout.nsclientinternal_fragment, container, false);

        logScrollview = (ScrollView) view.findViewById(R.id.nsclientinternal_logscrollview);
        autoscrollCheckbox = (CheckBox) view.findViewById(R.id.nsclientinternal_autoscroll);
        autoscrollCheckbox.setChecked(NSClientPlugin.getPlugin().autoscroll);
        autoscrollCheckbox.setOnCheckedChangeListener(this);
        pausedCheckbox = (CheckBox) view.findViewById(R.id.nsclientinternal_paused);
        pausedCheckbox.setChecked(NSClientPlugin.getPlugin().paused);
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

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventNSClientUpdateGUI.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), FabricPrivacy::logException)
        );
        updateGui();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.nsclientinternal_restart:
                RxBus.INSTANCE.send(new EventNSClientRestart());
                FabricPrivacy.getInstance().logCustom("NSClientRestart");
                break;
            case R.id.nsclientinternal_delivernow:
                NSClientPlugin.getPlugin().resend("GUI");
                FabricPrivacy.getInstance().logCustom("NSClientDeliverNow");
                break;
            case R.id.nsclientinternal_clearlog:
                NSClientPlugin.getPlugin().clearLog();
                break;
            case R.id.nsclientinternal_clearqueue:
                OKDialog.showConfirmation(getContext(),MainApp.gs(R.string.nsclientinternal), MainApp.gs(R.string.clearqueueconfirm), () -> {
                    UploadQueue.clearQueue();
                    updateGui();
                    FabricPrivacy.getInstance().logCustom("NSClientClearQueue");
                });
                break;
            case R.id.nsclientinternal_showqueue:
                RxBus.INSTANCE.send(new EventNSClientNewLog("QUEUE", NSClientPlugin.getPlugin().queue().textList()));
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.nsclientinternal_paused:
                NSClientPlugin.getPlugin().pause(isChecked);
                updateGui();
                FabricPrivacy.getInstance().logCustom("NSClientPause");
                break;
            case R.id.nsclientinternal_autoscroll:
                SP.putBoolean(R.string.key_nsclientinternal_autoscroll, isChecked);
                NSClientPlugin.getPlugin().autoscroll = isChecked;
                updateGui();
                break;
        }
    }

    protected void updateGui() {
        NSClientPlugin.getPlugin().updateLog();
        pausedCheckbox.setChecked(SP.getBoolean(R.string.key_nsclientinternal_paused, false));
        logTextView.setText(NSClientPlugin.getPlugin().textLog);
        if (NSClientPlugin.getPlugin().autoscroll) {
            logScrollview.fullScroll(ScrollView.FOCUS_DOWN);
        }
        urlTextView.setText(NSClientPlugin.getPlugin().url());
        Spanned queuetext = Html.fromHtml(MainApp.gs(R.string.queue) + " <b>" + UploadQueue.size() + "</b>");
        queueTextView.setText(queuetext);
        statusTextView.setText(NSClientPlugin.getPlugin().status);
    }

}

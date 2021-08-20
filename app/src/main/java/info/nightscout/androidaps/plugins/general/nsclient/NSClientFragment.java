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

import javax.inject.Inject;

import dagger.android.support.DaggerFragment;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart;
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.HtmlHelper;
import info.nightscout.androidaps.utils.alertDialogs.OKDialog;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class NSClientFragment extends DaggerFragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    @Inject NSClientPlugin nsClientPlugin;
    @Inject SP sp;
    @Inject ResourceHelper resourceHelper;
    @Inject RxBusWrapper rxBus;
    @Inject UploadQueue uploadQueue;
    @Inject FabricPrivacy fabricPrivacy;

    private final CompositeDisposable disposable = new CompositeDisposable();

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

        logScrollview = view.findViewById(R.id.nsclientinternal_logscrollview);
        autoscrollCheckbox = view.findViewById(R.id.nsclientinternal_autoscroll);
        autoscrollCheckbox.setChecked(nsClientPlugin.autoscroll);
        autoscrollCheckbox.setOnCheckedChangeListener(this);
        pausedCheckbox = view.findViewById(R.id.nsclientinternal_paused);
        pausedCheckbox.setChecked(nsClientPlugin.paused);
        pausedCheckbox.setOnCheckedChangeListener(this);
        logTextView = view.findViewById(R.id.nsclientinternal_log);
        queueTextView = view.findViewById(R.id.nsclientinternal_queue);
        urlTextView = view.findViewById(R.id.nsclientinternal_url);
        statusTextView = view.findViewById(R.id.nsclientinternal_status);

        clearlog = view.findViewById(R.id.nsclientinternal_clearlog);
        clearlog.setOnClickListener(this);
        clearlog.setPaintFlags(clearlog.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        restart = view.findViewById(R.id.nsclientinternal_restart);
        restart.setOnClickListener(this);
        restart.setPaintFlags(restart.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        delivernow = view.findViewById(R.id.nsclientinternal_delivernow);
        delivernow.setOnClickListener(this);
        delivernow.setPaintFlags(delivernow.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        clearqueue = view.findViewById(R.id.nsclientinternal_clearqueue);
        clearqueue.setOnClickListener(this);
        clearqueue.setPaintFlags(clearqueue.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        showqueue = view.findViewById(R.id.nsclientinternal_showqueue);
        showqueue.setOnClickListener(this);
        showqueue.setPaintFlags(showqueue.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(rxBus
                .toObservable(EventNSClientUpdateGUI.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGui(), fabricPrivacy::logException)
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
                rxBus.send(new EventNSClientRestart());
                fabricPrivacy.logCustom("NSClientRestart");
                break;
            case R.id.nsclientinternal_delivernow:
                nsClientPlugin.resend("GUI");
                fabricPrivacy.logCustom("NSClientDeliverNow");
                break;
            case R.id.nsclientinternal_clearlog:
                nsClientPlugin.clearLog();
                break;
            case R.id.nsclientinternal_clearqueue:
                OKDialog.showConfirmation(getContext(), resourceHelper.gs(R.string.nsclientinternal), resourceHelper.gs(R.string.clearqueueconfirm), () -> {
                    uploadQueue.clearQueue();
                    updateGui();
                    fabricPrivacy.logCustom("NSClientClearQueue");
                });
                break;
            case R.id.nsclientinternal_showqueue:
                rxBus.send(new EventNSClientNewLog("QUEUE", uploadQueue.textList()));
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.nsclientinternal_paused:
                nsClientPlugin.pause(isChecked);
                updateGui();
                fabricPrivacy.logCustom("NSClientPause");
                break;
            case R.id.nsclientinternal_autoscroll:
                sp.putBoolean(R.string.key_nsclientinternal_autoscroll, isChecked);
                nsClientPlugin.autoscroll = isChecked;
                updateGui();
                break;
        }
    }

    protected void updateGui() {
        nsClientPlugin.updateLog();
        pausedCheckbox.setChecked(sp.getBoolean(R.string.key_nsclientinternal_paused, false));
        logTextView.setText(nsClientPlugin.textLog);
        if (nsClientPlugin.autoscroll) {
            logScrollview.fullScroll(ScrollView.FOCUS_DOWN);
        }
        urlTextView.setText(nsClientPlugin.url());
        Spanned queuetext = HtmlHelper.INSTANCE.fromHtml(resourceHelper.gs(R.string.queue) + " <b>" + uploadQueue.size() + "</b>");
        queueTextView.setText(queuetext);
        statusTextView.setText(nsClientPlugin.status);
    }

}

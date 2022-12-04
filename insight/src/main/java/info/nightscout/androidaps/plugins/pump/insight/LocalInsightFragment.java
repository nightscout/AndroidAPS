package info.nightscout.androidaps.plugins.pump.insight;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.android.support.DaggerFragment;
import info.nightscout.androidaps.insight.R;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.TBROverNotificationBlock;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveBasalRate;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveBolus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveTBR;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.CartridgeStatus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.InsightState;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.TotalDailyDose;
import info.nightscout.androidaps.plugins.pump.insight.events.EventLocalInsightUpdateGUI;
import info.nightscout.core.utils.fabric.FabricPrivacy;
import info.nightscout.interfaces.queue.Callback;
import info.nightscout.interfaces.queue.CommandQueue;
import info.nightscout.interfaces.utils.DecimalFormatter;
import info.nightscout.rx.AapsSchedulers;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.shared.interfaces.ResourceHelper;
import info.nightscout.shared.utils.DateUtil;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class LocalInsightFragment extends DaggerFragment implements View.OnClickListener {
    @Inject LocalInsightPlugin localInsightPlugin;
    @Inject CommandQueue commandQueue;
    @Inject RxBus rxBus;
    @Inject ResourceHelper rh;
    @Inject FabricPrivacy fabricPrivacy;
    @Inject DateUtil dateUtil;
    @Inject AapsSchedulers aapsSchedulers;

    private final CompositeDisposable disposable = new CompositeDisposable();

    private static final boolean ENABLE_OPERATING_MODE_BUTTON = false;

    private boolean viewsCreated;
    private Button operatingMode;
    private Button tbrOverNotification;
    private Button refresh;
    private LinearLayout statusItemContainer = null;

    private Callback operatingModeCallback;
    private Callback tbrOverNotificationCallback;
    private Callback refreshCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.local_insight_fragment, container, false);
        statusItemContainer = view.findViewById(R.id.status_item_container);
        tbrOverNotification = view.findViewById(R.id.tbr_over_notification);
        tbrOverNotification.setOnClickListener(this);
        operatingMode = view.findViewById(R.id.operating_mode);
        operatingMode.setOnClickListener(this);
        refresh = view.findViewById(R.id.refresh);
        refresh.setOnClickListener(this);
        viewsCreated = true;
        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(rxBus
                .toObservable(EventLocalInsightUpdateGUI.class)
                .observeOn(aapsSchedulers.getMain())
                .subscribe(event -> updateGUI(), fabricPrivacy::logException)
        );
        updateGUI();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    @Override
    public synchronized void onDestroyView() {
        super.onDestroyView();
        viewsCreated = false;
    }

    @Override
    public void onClick(View v) {
        if (v == operatingMode) {
            if (localInsightPlugin.getOperatingMode() != null) {
                operatingMode.setEnabled(false);
                operatingModeCallback = new Callback() {
                    @Override
                    public void run() {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            operatingModeCallback = null;
                            updateGUI();
                        });
                    }
                };
                switch (localInsightPlugin.getOperatingMode()) {
                    case PAUSED:
                    case STOPPED:
                        commandQueue.startPump(operatingModeCallback);
                        break;
                    case STARTED:
                        commandQueue.stopPump(operatingModeCallback);
                }
            }
        } else if (v == tbrOverNotification) {
            TBROverNotificationBlock notificationBlock = localInsightPlugin.getTBROverNotificationBlock();
            if (notificationBlock != null) {
                tbrOverNotification.setEnabled(false);
                tbrOverNotificationCallback = new Callback() {
                    @Override
                    public void run() {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            tbrOverNotificationCallback = null;
                            updateGUI();
                        });
                    }
                };
                commandQueue.setTBROverNotification(tbrOverNotificationCallback, !notificationBlock.isEnabled());
            }
        } else if (v == refresh) {
            refresh.setEnabled(false);
            refreshCallback = new Callback() {
                @Override
                public void run() {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        refreshCallback = null;
                        updateGUI();
                    });
                }
            };
            commandQueue.readStatus(rh.gs(R.string.insight_refresh_button), refreshCallback);
        }
    }

    protected void updateGUI() {
        if (!viewsCreated) return;
        statusItemContainer.removeAllViews();
        if (!localInsightPlugin.isInitialized()) {
            operatingMode.setVisibility(View.GONE);
            tbrOverNotification.setVisibility(View.GONE);
            refresh.setVisibility(View.GONE);
            return;
        }
        refresh.setVisibility(View.VISIBLE);
        refresh.setEnabled(refreshCallback == null);
        TBROverNotificationBlock notificationBlock = localInsightPlugin.getTBROverNotificationBlock();
        tbrOverNotification.setVisibility(notificationBlock == null ? View.GONE : View.VISIBLE);
        if (notificationBlock != null)
            tbrOverNotification.setText(notificationBlock.isEnabled() ? R.string.disable_tbr_over_notification : R.string.enable_tbr_over_notification);
        tbrOverNotification.setEnabled(tbrOverNotificationCallback == null);
        List<View> statusItems = new ArrayList<>();
        getConnectionStatusItem(statusItems);
        getLastConnectedItem(statusItems);
        getOperatingModeItem(statusItems);
        getBatteryStatusItem(statusItems);
        getCartridgeStatusItem(statusItems);
        getTDDItems(statusItems);
        getBaseBasalRateItem(statusItems);
        getTBRItem(statusItems);
        getLastBolusItem(statusItems);
        getBolusItems(statusItems);
        for (int i = 0; i < statusItems.size(); i++) {
            statusItemContainer.addView(statusItems.get(i));
            if (i != statusItems.size() - 1)
                getLayoutInflater().inflate(R.layout.local_insight_status_delimitter, statusItemContainer);
        }
    }

    private View getStatusItem(String label, String value) {
        @SuppressLint("InflateParams") View statusItem = getLayoutInflater().inflate(R.layout.local_insight_status_item, null);
        ((TextView) statusItem.findViewById(R.id.label)).setText(label);
        ((TextView) statusItem.findViewById(R.id.value)).setText(value);
        return statusItem;
    }

    private void getConnectionStatusItem(List<View> statusItems) {
        int string = 0;
        InsightState state = localInsightPlugin.getConnectionService().getState();
        switch (state) {
            case NOT_PAIRED:
                string = R.string.not_paired;
                break;
            case DISCONNECTED:
                string = info.nightscout.core.ui.R.string.disconnected;
                break;
            case CONNECTING:
            case SATL_CONNECTION_REQUEST:
            case SATL_KEY_REQUEST:
            case SATL_SYN_REQUEST:
            case SATL_VERIFY_CONFIRM_REQUEST:
            case SATL_VERIFY_DISPLAY_REQUEST:
            case APP_ACTIVATE_PARAMETER_SERVICE:
            case APP_ACTIVATE_STATUS_SERVICE:
            case APP_BIND_MESSAGE:
            case APP_CONNECT_MESSAGE:
            case APP_FIRMWARE_VERSIONS:
            case APP_SYSTEM_IDENTIFICATION:
            case AWAITING_CODE_CONFIRMATION:
                string = info.nightscout.core.ui.R.string.connecting;
                break;
            case CONNECTED:
                string = info.nightscout.shared.R.string.connected;
                break;
            case RECOVERING:
                string = R.string.recovering;
                break;
        }
        statusItems.add(getStatusItem(rh.gs(R.string.insight_status), rh.gs(string)));
        if (state == InsightState.RECOVERING) {
            statusItems.add(getStatusItem(rh.gs(R.string.recovery_duration), localInsightPlugin.getConnectionService().getRecoveryDuration() / 1000 + "s"));
        }
    }

    private void getLastConnectedItem(List<View> statusItems) {
        switch (localInsightPlugin.getConnectionService().getState()) {
            case CONNECTED:
            case NOT_PAIRED:
                return;
            default:
                long lastConnection = localInsightPlugin.getConnectionService().getLastConnected();
                if (lastConnection == 0) return;
                long agoMsc = System.currentTimeMillis() - lastConnection;
                double lastConnectionMinAgo = agoMsc / 60d / 1000d;
                String ago;
                if (lastConnectionMinAgo < 60) {
                    ago = dateUtil.minAgo(rh, lastConnection);
                } else {
                    ago = dateUtil.hourAgo(lastConnection, rh);
                }
                statusItems.add(getStatusItem(rh.gs(R.string.last_connected),
                        dateUtil.timeString(lastConnection) + " (" + ago + ")"));
        }
    }

    private void getOperatingModeItem(List<View> statusItems) {
        if (localInsightPlugin.getOperatingMode() == null) {
            operatingMode.setVisibility(View.GONE);
            return;
        }
        int string = 0;
        if (ENABLE_OPERATING_MODE_BUTTON) operatingMode.setVisibility(View.VISIBLE);
        operatingMode.setEnabled(operatingModeCallback == null);
        switch (localInsightPlugin.getOperatingMode()) {
            case STARTED:
                operatingMode.setText(R.string.stop_pump);
                string = R.string.started;
                break;
            case STOPPED:
                operatingMode.setText(R.string.start_pump);
                string = R.string.stopped;
                break;
            case PAUSED:
                operatingMode.setText(R.string.start_pump);
                string = info.nightscout.core.ui.R.string.paused;
                break;
        }
        statusItems.add(getStatusItem(rh.gs(R.string.operating_mode), rh.gs(string)));
    }

    private void getBatteryStatusItem(List<View> statusItems) {
        if (localInsightPlugin.getBatteryStatus() == null) return;
        statusItems.add(getStatusItem(rh.gs(info.nightscout.core.ui.R.string.battery_label),
                localInsightPlugin.getBatteryStatus().getBatteryAmount() + "%"));
    }

    private void getCartridgeStatusItem(List<View> statusItems) {
        CartridgeStatus cartridgeStatus = localInsightPlugin.getCartridgeStatus();
        if (cartridgeStatus == null) return;
        String status;
        if (cartridgeStatus.isInserted())
            status = DecimalFormatter.INSTANCE.to2Decimal(cartridgeStatus.getRemainingAmount()) + "U";
        else status = rh.gs(R.string.not_inserted);
        statusItems.add(getStatusItem(rh.gs(info.nightscout.core.ui.R.string.reservoir_label), status));
    }

    private void getTDDItems(List<View> statusItems) {
        if (localInsightPlugin.getTotalDailyDose() == null) return;
        TotalDailyDose tdd = localInsightPlugin.getTotalDailyDose();
        statusItems.add(getStatusItem(rh.gs(R.string.tdd_bolus), DecimalFormatter.INSTANCE.to2Decimal(tdd.getBolus())));
        statusItems.add(getStatusItem(rh.gs(R.string.tdd_basal), DecimalFormatter.INSTANCE.to2Decimal(tdd.getBasal())));
        statusItems.add(getStatusItem(rh.gs(info.nightscout.core.ui.R.string.tdd_total), DecimalFormatter.INSTANCE.to2Decimal(tdd.getBolusAndBasal())));
    }

    private void getBaseBasalRateItem(List<View> statusItems) {
        if (localInsightPlugin.getActiveBasalRate() == null) return;
        ActiveBasalRate activeBasalRate = localInsightPlugin.getActiveBasalRate();
        statusItems.add(getStatusItem(rh.gs(info.nightscout.core.ui.R.string.base_basal_rate_label),
                DecimalFormatter.INSTANCE.to2Decimal(activeBasalRate.getActiveBasalRate()) + " U/h (" + activeBasalRate.getActiveBasalProfileName() + ")"));
    }

    private void getTBRItem(List<View> statusItems) {
        if (localInsightPlugin.getActiveTBR() == null) return;
        ActiveTBR activeTBR = localInsightPlugin.getActiveTBR();
        statusItems.add(getStatusItem(rh.gs(info.nightscout.core.ui.R.string.tempbasal_label),
                rh.gs(R.string.tbr_formatter, activeTBR.getPercentage(), activeTBR.getInitialDuration() - activeTBR.getRemainingDuration(), activeTBR.getInitialDuration())));
    }

    private void getLastBolusItem(List<View> statusItems) {
        if (localInsightPlugin.lastBolusAmount == 0 || localInsightPlugin.lastBolusTimestamp == 0) return;
        long agoMsc = System.currentTimeMillis() - localInsightPlugin.lastBolusTimestamp;
        double bolusMinAgo = agoMsc / 60d / 1000d;
        String unit = rh.gs(info.nightscout.core.ui.R.string.insulin_unit_shortname);
        String ago;
        if (bolusMinAgo < 60) {
            ago = dateUtil.minAgo(rh, localInsightPlugin.lastBolusTimestamp);
        } else {
            ago = dateUtil.hourAgo(localInsightPlugin.lastBolusTimestamp, rh);
        }
        statusItems.add(getStatusItem(rh.gs(R.string.insight_last_bolus),
                rh.gs(R.string.insight_last_bolus_formater, localInsightPlugin.lastBolusAmount, unit, ago)));
    }

    private void getBolusItems(List<View> statusItems) {
        if (localInsightPlugin.getActiveBoluses() == null) return;
        for (ActiveBolus activeBolus : localInsightPlugin.getActiveBoluses()) {
            String label;
            switch (activeBolus.getBolusType()) {
                case MULTIWAVE:
                    label = rh.gs(R.string.multiwave_bolus);
                    break;
                case EXTENDED:
                    label = rh.gs(info.nightscout.core.ui.R.string.extended_bolus);
                    break;
                default:
                    continue;
            }
            statusItems.add(getStatusItem(label, rh.gs(R.string.eb_formatter, activeBolus.getRemainingAmount(), activeBolus.getInitialAmount(), activeBolus.getRemainingDuration())));
        }
    }
}
package info.nightscout.androidaps.plugins.pump.insight;

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
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.TBROverNotificationBlock;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveBasalRate;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveBolus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveTBR;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.CartridgeStatus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.InsightState;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.TotalDailyDose;
import info.nightscout.androidaps.plugins.pump.insight.events.EventLocalInsightUpdateGUI;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class LocalInsightFragment extends Fragment implements View.OnClickListener {
    private CompositeDisposable disposable = new CompositeDisposable();

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
        disposable.add(RxBus.INSTANCE
                .toObservable(EventLocalInsightUpdateGUI.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGUI(), FabricPrivacy::logException)
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
            if (LocalInsightPlugin.getPlugin().getOperatingMode() != null) {
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
                switch (LocalInsightPlugin.getPlugin().getOperatingMode()) {
                    case PAUSED:
                    case STOPPED:
                        ConfigBuilderPlugin.getPlugin().getCommandQueue().startPump(operatingModeCallback);
                        break;
                    case STARTED:
                        ConfigBuilderPlugin.getPlugin().getCommandQueue().stopPump(operatingModeCallback);
                }
            }
        } else if (v == tbrOverNotification) {
            TBROverNotificationBlock notificationBlock = LocalInsightPlugin.getPlugin().getTBROverNotificationBlock();
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
                ConfigBuilderPlugin.getPlugin().getCommandQueue()
                        .setTBROverNotification(tbrOverNotificationCallback, !notificationBlock.isEnabled());
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
            ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("InsightRefreshButton", refreshCallback);
        }
    }

    protected void updateGUI() {
        if (!viewsCreated) return;
        statusItemContainer.removeAllViews();
        if (!LocalInsightPlugin.getPlugin().isInitialized()) {
            operatingMode.setVisibility(View.GONE);
            tbrOverNotification.setVisibility(View.GONE);
            refresh.setVisibility(View.GONE);
            return;
        }
        refresh.setVisibility(View.VISIBLE);
        refresh.setEnabled(refreshCallback == null);
        TBROverNotificationBlock notificationBlock = LocalInsightPlugin.getPlugin().getTBROverNotificationBlock();
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
        getBolusItems(statusItems);
        for (int i = 0; i < statusItems.size(); i++) {
            statusItemContainer.addView(statusItems.get(i));
            if (i != statusItems.size() - 1)
                getLayoutInflater().inflate(R.layout.local_insight_status_delimitter, statusItemContainer);
        }
    }

    private View getStatusItem(String label, String value) {
        View statusItem = getLayoutInflater().inflate(R.layout.local_insight_status_item, null);
        ((TextView) statusItem.findViewById(R.id.label)).setText(label);
        ((TextView) statusItem.findViewById(R.id.value)).setText(value);
        return statusItem;
    }

    private void getConnectionStatusItem(List<View> statusItems) {
        int string = 0;
        InsightState state = LocalInsightPlugin.getPlugin().getConnectionService().getState();
        switch (state) {
            case NOT_PAIRED:
                string = R.string.not_paired;
                break;
            case DISCONNECTED:
                string = R.string.disconnected;
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
                string = R.string.connecting;
                break;
            case CONNECTED:
                string = R.string.connected;
                break;
            case RECOVERING:
                string = R.string.recovering;
                break;
        }
        statusItems.add(getStatusItem(MainApp.gs(R.string.insight_status), MainApp.gs(string)));
        if (state == InsightState.RECOVERING) {
            statusItems.add(getStatusItem(MainApp.gs(R.string.recovery_duration), LocalInsightPlugin.getPlugin().getConnectionService().getRecoveryDuration() / 1000 + "s"));
        }
    }

    private void getLastConnectedItem(List<View> statusItems) {
        switch (LocalInsightPlugin.getPlugin().getConnectionService().getState()) {
            case CONNECTED:
            case NOT_PAIRED:
                return;
            default:
                long lastConnection = LocalInsightPlugin.getPlugin().getConnectionService().getLastConnected();
                if (lastConnection == 0) return;
                int min = (int) ((System.currentTimeMillis() - lastConnection) / 60000);
                statusItems.add(getStatusItem(MainApp.gs(R.string.last_connected), DateUtil.timeString(lastConnection)));
        }
    }

    private void getOperatingModeItem(List<View> statusItems) {
        if (LocalInsightPlugin.getPlugin().getOperatingMode() == null) {
            operatingMode.setVisibility(View.GONE);
            return;
        }
        int string = 0;
        if (ENABLE_OPERATING_MODE_BUTTON) operatingMode.setVisibility(View.VISIBLE);
        operatingMode.setEnabled(operatingModeCallback == null);
        switch (LocalInsightPlugin.getPlugin().getOperatingMode()) {
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
                string = R.string.paused;
                break;
        }
        statusItems.add(getStatusItem(MainApp.gs(R.string.operating_mode), MainApp.gs(string)));
    }

    private void getBatteryStatusItem(List<View> statusItems) {
        if (LocalInsightPlugin.getPlugin().getBatteryStatus() == null) return;
        statusItems.add(getStatusItem(MainApp.gs(R.string.pump_battery_label),
                LocalInsightPlugin.getPlugin().getBatteryStatus().getBatteryAmount() + "%"));
    }

    private void getCartridgeStatusItem(List<View> statusItems) {
        CartridgeStatus cartridgeStatus = LocalInsightPlugin.getPlugin().getCartridgeStatus();
        if (cartridgeStatus == null) return;
        String status;
        if (cartridgeStatus.isInserted())
            status = DecimalFormatter.to2Decimal(LocalInsightPlugin.getPlugin().getCartridgeStatus().getRemainingAmount()) + "U";
        else status = MainApp.gs(R.string.not_inserted);
        statusItems.add(getStatusItem(MainApp.gs(R.string.pump_reservoir_label), status));
    }

    private void getTDDItems(List<View> statusItems) {
        if (LocalInsightPlugin.getPlugin().getTotalDailyDose() == null) return;
        TotalDailyDose tdd = LocalInsightPlugin.getPlugin().getTotalDailyDose();
        statusItems.add(getStatusItem(MainApp.gs(R.string.tdd_bolus), DecimalFormatter.to2Decimal(tdd.getBolus())));
        statusItems.add(getStatusItem(MainApp.gs(R.string.tdd_basal), DecimalFormatter.to2Decimal(tdd.getBasal())));
        statusItems.add(getStatusItem(MainApp.gs(R.string.tdd_total), DecimalFormatter.to2Decimal(tdd.getBolusAndBasal())));
    }

    private void getBaseBasalRateItem(List<View> statusItems) {
        if (LocalInsightPlugin.getPlugin().getActiveBasalRate() == null) return;
        ActiveBasalRate activeBasalRate = LocalInsightPlugin.getPlugin().getActiveBasalRate();
        statusItems.add(getStatusItem(MainApp.gs(R.string.pump_basebasalrate_label),
                DecimalFormatter.to2Decimal(activeBasalRate.getActiveBasalRate()) + " U/h (" + activeBasalRate.getActiveBasalProfileName() + ")"));
    }

    private void getTBRItem(List<View> statusItems) {
        if (LocalInsightPlugin.getPlugin().getActiveTBR() == null) return;
        ActiveTBR activeTBR = LocalInsightPlugin.getPlugin().getActiveTBR();
        statusItems.add(getStatusItem(MainApp.gs(R.string.pump_tempbasal_label),
                MainApp.gs(R.string.tbr_formatter, activeTBR.getPercentage(), activeTBR.getInitialDuration() - activeTBR.getRemainingDuration(), activeTBR.getInitialDuration())));
    }

    private void getBolusItems(List<View> statusItems) {
        if (LocalInsightPlugin.getPlugin().getActiveBoluses() == null) return;
        for (ActiveBolus activeBolus : LocalInsightPlugin.getPlugin().getActiveBoluses()) {
            String label;
            switch (activeBolus.getBolusType()) {
                case MULTIWAVE:
                    label = MainApp.gs(R.string.multiwave_bolus);
                    break;
                case EXTENDED:
                    label = MainApp.gs(R.string.extended_bolus);
                    break;
                default:
                    continue;
            }
            statusItems.add(getStatusItem(label, MainApp.gs(R.string.eb_formatter, activeBolus.getRemainingAmount(), activeBolus.getInitialAmount(), activeBolus.getRemainingDuration())));
        }
    }
}
package info.nightscout.androidaps.plugins.PumpInsightLocal;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.parameter_blocks.TBROverNotificationBlock;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.ActiveBasalRate;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.ActiveBolus;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.ActiveTBR;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.CartridgeStatus;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.InsightState;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.TotalDailyDose;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

public class LocalInsightFragment extends SubscriberFragment implements View.OnClickListener {

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
    public synchronized void onDestroyView() {
        super.onDestroyView();
        viewsCreated = false;
    }

    @Override
    public void onClick(View v) {
        if (v == operatingMode) {
            if (LocalInsightPlugin.getInstance().getOperatingMode() != null) {
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
                switch (LocalInsightPlugin.getInstance().getOperatingMode()) {
                    case PAUSED:
                    case STOPPED:
                        ConfigBuilderPlugin.getPlugin().getCommandQueue().startPump(operatingModeCallback);
                        break;
                    case STARTED:
                        ConfigBuilderPlugin.getPlugin().getCommandQueue().stopPump(operatingModeCallback);
                }
            }
        } else if (v == tbrOverNotification) {
            TBROverNotificationBlock notificationBlock = LocalInsightPlugin.getInstance().getTBROverNotificationBlock();
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

    @Subscribe
    public void onUpdateGUIEvent(EventLocalInsightUpdateGUI event) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        if (!viewsCreated) return;
        statusItemContainer.removeAllViews();
        if (!LocalInsightPlugin.getInstance().isInitialized()) {
            operatingMode.setVisibility(View.GONE);
            tbrOverNotification.setVisibility(View.GONE);
            refresh.setVisibility(View.GONE);
            return;
        }
        refresh.setVisibility(View.VISIBLE);
        refresh.setEnabled(refreshCallback == null);
        TBROverNotificationBlock notificationBlock = LocalInsightPlugin.getInstance().getTBROverNotificationBlock();
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
        InsightState state = LocalInsightPlugin.getInstance().getConnectionService().getState();
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
            statusItems.add(getStatusItem(MainApp.gs(R.string.recovery_duration), LocalInsightPlugin.getInstance().getConnectionService().getRecoveryDuration() / 1000 + "s"));
        }
    }

    private void getLastConnectedItem(List<View> statusItems) {
        switch (LocalInsightPlugin.getInstance().getConnectionService().getState()) {
            case CONNECTED:
            case NOT_PAIRED:
                return;
            default:
                long lastConnection = LocalInsightPlugin.getInstance().getConnectionService().getLastConnected();
                if (lastConnection == 0) return;
                int min = (int) ((System.currentTimeMillis() - lastConnection) / 60000);
                statusItems.add(getStatusItem(MainApp.gs(R.string.last_connected), DateUtil.timeString(lastConnection)));
        }
    }

    private void getOperatingModeItem(List<View> statusItems) {
        if (LocalInsightPlugin.getInstance().getOperatingMode() == null) {
            operatingMode.setVisibility(View.GONE);
            return;
        }
        int string = 0;
        if (ENABLE_OPERATING_MODE_BUTTON) operatingMode.setVisibility(View.VISIBLE);
        operatingMode.setEnabled(operatingModeCallback == null);
        switch (LocalInsightPlugin.getInstance().getOperatingMode()) {
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
        if (LocalInsightPlugin.getInstance().getBatteryStatus() == null) return;
        statusItems.add(getStatusItem(MainApp.gs(R.string.pump_battery_label),
                LocalInsightPlugin.getInstance().getBatteryStatus().getBatteryAmount() + "%"));
    }

    private void getCartridgeStatusItem(List<View> statusItems) {
        CartridgeStatus cartridgeStatus = LocalInsightPlugin.getInstance().getCartridgeStatus();
        if (cartridgeStatus == null) return;
        String status;
        if (cartridgeStatus.isInserted())
            status = DecimalFormatter.to2Decimal(LocalInsightPlugin.getInstance().getCartridgeStatus().getRemainingAmount()) + "U";
        else status = MainApp.gs(R.string.not_inserted);
        statusItems.add(getStatusItem(MainApp.gs(R.string.pump_reservoir_label), status));
    }

    private void getTDDItems(List<View> statusItems) {
        if (LocalInsightPlugin.getInstance().getTotalDailyDose() == null) return;
        TotalDailyDose tdd = LocalInsightPlugin.getInstance().getTotalDailyDose();
        statusItems.add(getStatusItem(MainApp.gs(R.string.tdd_bolus), DecimalFormatter.to2Decimal(tdd.getBolus())));
        statusItems.add(getStatusItem(MainApp.gs(R.string.tdd_basal), DecimalFormatter.to2Decimal(tdd.getBasal())));
        statusItems.add(getStatusItem(MainApp.gs(R.string.tdd_total), DecimalFormatter.to2Decimal(tdd.getBolusAndBasal())));
    }

    private void getBaseBasalRateItem(List<View> statusItems) {
        if (LocalInsightPlugin.getInstance().getActiveBasalRate() == null) return;
        ActiveBasalRate activeBasalRate = LocalInsightPlugin.getInstance().getActiveBasalRate();
        statusItems.add(getStatusItem(MainApp.gs(R.string.pump_basebasalrate_label),
                DecimalFormatter.to2Decimal(activeBasalRate.getActiveBasalRate()) + " U/h (" + activeBasalRate.getActiveBasalProfileName() + ")"));
    }

    private void getTBRItem(List<View> statusItems) {
        if (LocalInsightPlugin.getInstance().getActiveTBR() == null) return;
        ActiveTBR activeTBR = LocalInsightPlugin.getInstance().getActiveTBR();
        statusItems.add(getStatusItem(MainApp.gs(R.string.pump_tempbasal_label),
                MainApp.gs(R.string.tbr_formatter, activeTBR.getPercentage(), activeTBR.getInitialDuration() - activeTBR.getRemainingDuration(), activeTBR.getInitialDuration())));
    }

    private void getBolusItems(List<View> statusItems) {
        if (LocalInsightPlugin.getInstance().getActiveBoluses() == null) return;
        for (ActiveBolus activeBolus : LocalInsightPlugin.getInstance().getActiveBoluses()) {
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

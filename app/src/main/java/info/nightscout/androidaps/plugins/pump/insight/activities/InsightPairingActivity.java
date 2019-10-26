package info.nightscout.androidaps.plugins.pump.insight.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.InsightState;
import info.nightscout.androidaps.plugins.pump.insight.utils.ExceptionTranslator;

public class InsightPairingActivity extends NoSplashAppCompatActivity implements InsightConnectionService.StateCallback, View.OnClickListener, InsightConnectionService.ExceptionCallback {

    private boolean scanning;
    private LinearLayout deviceSearchSection;
    private TextView pleaseWaitSection;
    private LinearLayout codeCompareSection;
    private LinearLayout pairingCompletedSection;
    private Button yes;
    private Button no;
    private TextView code;
    private Button exit;
    private RecyclerView deviceList;
    private DeviceAdapter deviceAdapter = new DeviceAdapter();

    private InsightConnectionService service;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((InsightConnectionService.LocalBinder) binder).getService();
            if (service.isPaired()) return;
            else {
                service.requestConnection(InsightPairingActivity.this);
                service.registerStateCallback(InsightPairingActivity.this);
                service.registerExceptionCallback(InsightPairingActivity.this);
                onStateChanged(service.getState());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insight_pairing);

        deviceSearchSection = findViewById(R.id.device_search_section);
        pleaseWaitSection = findViewById(R.id.please_wait_section);
        codeCompareSection = findViewById(R.id.code_compare_section);
        pairingCompletedSection = findViewById(R.id.pairing_completed_section);
        yes = findViewById(R.id.yes);
        no = findViewById(R.id.no);
        code = findViewById(R.id.code);
        exit = findViewById(R.id.exit);
        deviceList = findViewById(R.id.device_list);

        yes.setOnClickListener(this);
        no.setOnClickListener(this);
        exit.setOnClickListener(this);

        deviceList.setLayoutManager(new LinearLayoutManager(this));
        deviceList.setAdapter(deviceAdapter);


        bindService(new Intent(this, InsightConnectionService.class), serviceConnection, BIND_AUTO_CREATE);
}

    @Override
    protected void onDestroy() {
        if (service != null) {
            service.withdrawConnectionRequest(InsightPairingActivity.this);
            service.unregisterStateCallback(InsightPairingActivity.this);
            service.unregisterExceptionCallback(InsightPairingActivity.this);
        }
        unbindService(serviceConnection);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (service != null && service.getState() == InsightState.NOT_PAIRED) startBLScan();
    }

    @Override
    protected void onStop() {
        stopBLScan();
        super.onStop();
    }

    @Override
    public void onStateChanged(InsightState state) {
        runOnUiThread(() -> {
            switch (state) {
                case NOT_PAIRED:
                    startBLScan();
                    deviceSearchSection.setVisibility(View.VISIBLE);
                    pleaseWaitSection.setVisibility(View.GONE);
                    codeCompareSection.setVisibility(View.GONE);
                    pairingCompletedSection.setVisibility(View.GONE);
                    break;
                case CONNECTING:
                case SATL_CONNECTION_REQUEST:
                case SATL_KEY_REQUEST:
                case SATL_VERIFY_DISPLAY_REQUEST:
                case SATL_VERIFY_CONFIRM_REQUEST:
                case APP_BIND_MESSAGE:
                    stopBLScan();
                    deviceSearchSection.setVisibility(View.GONE);
                    pleaseWaitSection.setVisibility(View.VISIBLE);
                    codeCompareSection.setVisibility(View.GONE);
                    pairingCompletedSection.setVisibility(View.GONE);
                    break;
                case AWAITING_CODE_CONFIRMATION:
                    stopBLScan();
                    deviceSearchSection.setVisibility(View.GONE);
                    pleaseWaitSection.setVisibility(View.GONE);
                    codeCompareSection.setVisibility(View.VISIBLE);
                    pairingCompletedSection.setVisibility(View.GONE);
                    code.setText(service.getVerificationString());
                    break;
                case DISCONNECTED:
                case CONNECTED:
                    stopBLScan();
                    deviceSearchSection.setVisibility(View.GONE);
                    pleaseWaitSection.setVisibility(View.GONE);
                    codeCompareSection.setVisibility(View.GONE);
                    pairingCompletedSection.setVisibility(View.VISIBLE);
                    break;
            }
        });
    }

    private void startBLScan() {
        if (!scanning) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null) {
                if (!bluetoothAdapter.isEnabled()) bluetoothAdapter.enable();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
                registerReceiver(broadcastReceiver, intentFilter);
                bluetoothAdapter.startDiscovery();
                scanning = true;
            }
        }
    }

    private void stopBLScan() {
        if (scanning) {
            unregisterReceiver(broadcastReceiver);
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null) {
                bluetoothAdapter.cancelDiscovery();
            }
            scanning = false;
        }
    }
    @Override
    public void onClick(View v) {
        if (v == exit) finish();
        else if (v == yes) service.confirmVerificationString();
        else if (v == no) service.rejectVerificationString();
    }

    @Override
    public void onExceptionOccur(Exception e) {
        ExceptionTranslator.makeToast(this, e);
    }

    private void deviceSelected(BluetoothDevice device) {
        service.pair(device.getAddress());
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
                BluetoothAdapter.getDefaultAdapter().startDiscovery();
            else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                deviceAdapter.addDevice(bluetoothDevice);
            }
        }
    };

    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

        private List<BluetoothDevice> bluetoothDevices = new ArrayList<>();

        public void addDevice(BluetoothDevice bluetoothDevice) {
            if (!bluetoothDevices.contains(bluetoothDevice)) {
                bluetoothDevices.add(bluetoothDevice);
                notifyDataSetChanged();
            }
        }

        public void clear() {
            bluetoothDevices.clear();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.bluetooth_device, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BluetoothDevice bluetoothDevice = bluetoothDevices.get(position);
            holder.deviceName.setText(bluetoothDevice.getName() == null ? bluetoothDevice.getAddress() : bluetoothDevice.getName());
            holder.deviceName.setOnClickListener((v) -> deviceSelected(bluetoothDevice));
        }

        @Override
        public int getItemCount() {
            return bluetoothDevices.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            private TextView deviceName;

            public ViewHolder(View itemView) {
                super(itemView);
                deviceName = (TextView) itemView;
            }
        }

    }
}

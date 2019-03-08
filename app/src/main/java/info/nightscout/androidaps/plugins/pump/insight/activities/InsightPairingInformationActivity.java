package info.nightscout.androidaps.plugins.pump.insight.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService;

public class InsightPairingInformationActivity extends AppCompatActivity {

    private InsightConnectionService connectionService;

    private TextView serialNumber;
    private TextView releaseSWVersion;
    private TextView uiProcSWVersion;
    private TextView pcProcSWVersion;
    private TextView mdTelSWVersion;
    private TextView safetyProcSWVersion;
    private TextView btInfoPageVersion;
    private TextView bluetoothAddress;
    private TextView systemIdAppendix;
    private TextView manufacturingDate;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            connectionService = ((InsightConnectionService.LocalBinder) binder).getService();
            if (!connectionService.isPaired()) {
                overridePendingTransition(0, 0);
                finish();
                startActivity(new Intent(InsightPairingInformationActivity.this, InsightPairingActivity.class));
            } else {
                serialNumber.setText(connectionService.getPumpSystemIdentification().getSerialNumber());
                manufacturingDate.setText(connectionService.getPumpSystemIdentification().getManufacturingDate());
                systemIdAppendix.setText(connectionService.getPumpSystemIdentification().getSystemIdAppendix() + "");
                releaseSWVersion.setText(connectionService.getPumpFirmwareVersions().getReleaseSWVersion());
                uiProcSWVersion.setText(connectionService.getPumpFirmwareVersions().getUiProcSWVersion());
                pcProcSWVersion.setText(connectionService.getPumpFirmwareVersions().getPcProcSWVersion());
                mdTelSWVersion.setText(connectionService.getPumpFirmwareVersions().getMdTelProcSWVersion());
                safetyProcSWVersion.setText(connectionService.getPumpFirmwareVersions().getSafetyProcSWVersion());
                btInfoPageVersion.setText(connectionService.getPumpFirmwareVersions().getBtInfoPageVersion());
                bluetoothAddress.setText(connectionService.getBluetoothAddress());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connectionService = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insight_pairing_information);
        serialNumber = findViewById(R.id.serial_number);
        releaseSWVersion = findViewById(R.id.release_sw_version);
        uiProcSWVersion = findViewById(R.id.ui_proc_sw_version);
        pcProcSWVersion = findViewById(R.id.pc_proc_sw_version);
        mdTelSWVersion = findViewById(R.id.md_tel_sw_version);
        safetyProcSWVersion = findViewById(R.id.safety_proc_sw_version);
        btInfoPageVersion = findViewById(R.id.bt_info_page_version);
        bluetoothAddress = findViewById(R.id.bluetooth_address);
        systemIdAppendix = findViewById(R.id.system_id_appendix);
        manufacturingDate = findViewById(R.id.manufacturing_date);
        bindService(new Intent(this, InsightConnectionService.class), serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        unbindService(serviceConnection);
        super.onDestroy();
    }

    public void deletePairing(View view) {
        if (connectionService != null) {
            connectionService.reset();
            finish();
        }
    }
}

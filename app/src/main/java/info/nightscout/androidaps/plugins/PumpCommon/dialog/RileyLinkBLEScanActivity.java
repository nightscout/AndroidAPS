package info.nightscout.androidaps.plugins.PumpCommon.dialog;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.PumpCommon.utils.LocationHelper;
import info.nightscout.utils.SP;

public class RileyLinkBLEScanActivity extends AppCompatActivity {

    private static final Logger LOG = LoggerFactory.getLogger(RileyLinkBLEScanActivity.class);
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 30241; // arbitrary.
    private static final int REQUEST_ENABLE_BT = 30242; // arbitrary
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 30000;
    public boolean mScanning;
    public Snackbar snackbar;
    public ScanSettings settings;
    public List<ScanFilter> filters;
    public ListView listBTScan;
    public Toolbar toolbarBTScan;
    public Context mContext = this;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLEScanner;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private Handler mHandler;
    private ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            final BluetoothDevice device = result.getDevice();
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (device.getName() != null && device.getName().length() > 0) {
                        mLeDeviceListAdapter.addDevice(device);
                        mLeDeviceListAdapter.notifyDataSetChanged();
                        LOG.debug("Found BLE" + device.getName());
                    }
                }
            });
        }


        @Override
        public void onBatchScanResults(final List<ScanResult> results) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    for (ScanResult result : results) {
                        BluetoothDevice device = result.getDevice();
                        if (device.getName() != null && device.getName().length() > 0) {
                            mLeDeviceListAdapter.addDevice(device);
                            LOG.debug("Found BLE" + result.toString());
                        } else {
                            LOG.error("Found BLE, but name appears to be missing. Ignoring. " + device.getAddress());
                        }
                    }
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }


        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
            Toast.makeText(mContext, "Scan Failed " + errorCode, Toast.LENGTH_LONG).show();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rileylink_scan_activity);

        // Initializes Bluetooth adapter.
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = new Handler();

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        listBTScan = (ListView)findViewById(R.id.rileylink_listBTScan);
        listBTScan.setAdapter(mLeDeviceListAdapter);
        listBTScan.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TextView textview = (TextView)view.findViewById(R.id.rileylink_device_address);
                String bleAddress = textview.getText().toString();

                SP.putString(RileyLinkConst.Prefs.RileyLinkAddress, bleAddress);

                // //Notify that we have a new rileylinkAddressKey
                // RileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.INTENT_NEW_rileylinkAddressKey);
                //
                // LOG.debug("New rileylinkAddressKey: " + bleAddress);
                //
                // //Notify that we have a new pumpIDKey
                // RileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.INTENT_NEW_pumpIDKey);
                finish();
            }
        });

        toolbarBTScan = (Toolbar)findViewById(R.id.rileylink_toolbarBTScan);
        toolbarBTScan.setTitle(R.string.rileylink_scanner_title);
        setSupportActionBar(toolbarBTScan);

        snackbar = Snackbar.make(findViewById(R.id.RileyLinkScan), "Scanning...", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("STOP", new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                scanLeDevice(false);
            }
        });

        startScanBLE();
    }


    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
        mLeDeviceListAdapter.notifyDataSetChanged();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_rileylink_ble_scan, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.rileylink_miScan:
                startScanBLE();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    public void startScanBLE() {
        // https://developer.android.com/training/permissions/requesting.html
        // http://developer.radiusnetworks.com/2015/09/29/is-your-beacon-app-ready-for-android-6.html
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "R.string.ble_not_supported", Toast.LENGTH_SHORT).show();
        } else {
            // Use this check to determine whether BLE is supported on the device. Then
            // you can selectively disable BLE-related features.
            if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // your code that requires permission
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_COARSE_LOCATION },
                    PERMISSION_REQUEST_COARSE_LOCATION);
            }

            // Ensures Bluetooth is available on the device and it is enabled. If not,
            // displays a dialog requesting user permission to enable Bluetooth.
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Toast.makeText(this, "R.string.ble_not_enabled", Toast.LENGTH_SHORT).show();
            } else {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Will request that GPS be enabled for devices running Marshmallow or newer.
                    LocationHelper.requestLocationForBluetooth(this);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }

                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
                filters = new ArrayList<ScanFilter>();

                scanLeDevice(true);
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                // User allowed Bluetooth to turn on
            } else if (resultCode == RESULT_CANCELED) {
                // Error, or user said "NO"
                finish();
            }
        }
    }


    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    mScanning = false;
                    mLEScanner.stopScan(mScanCallback);
                    LOG.debug("scanLeDevice: Scanning Stop");
                    // Toast.makeText(mContext, "Scanning finished", Toast.LENGTH_SHORT).show();
                    snackbar.dismiss();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mLEScanner.startScan(mScanCallback);
            LOG.debug("scanLeDevice: Scanning Start");
            // Toast.makeText(this, "Scanning", Toast.LENGTH_SHORT).show();
            snackbar.show();
        } else {
            mScanning = false;
            mLEScanner.stopScan(mScanCallback);

            LOG.debug("scanLeDevice: Scanning Stop");
            // Toast.makeText(this, "Scanning finished", Toast.LENGTH_SHORT).show();
            snackbar.dismiss();

        }
    }

    static class ViewHolder {

        TextView deviceName;
        TextView deviceAddress;
    }

    private class LeDeviceListAdapter extends BaseAdapter {

        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;


        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = RileyLinkBLEScanActivity.this.getLayoutInflater();
        }


        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
                notifyDataSetChanged();
            }
        }


        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }


        public void clear() {
            mLeDevices.clear();
            notifyDataSetChanged();
        }


        @Override
        public int getCount() {
            return mLeDevices.size();
        }


        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }


        @Override
        public long getItemId(int i) {
            return i;
        }


        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.rileylink_scan_item, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView)view.findViewById(R.id.rileylink_device_address);
                viewHolder.deviceName = (TextView)view.findViewById(R.id.rileylink_device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder)view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            String deviceName = device.getName();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            if (SP.getString(RileyLinkConst.Prefs.RileyLinkAddress, "").compareTo(device.getAddress()) == 0) {
                // viewHolder.deviceName.setTextColor(getColor(R.color.secondary_text_light));
                // viewHolder.deviceAddress.setTextColor(getColor(R.color.secondary_text_light));
                deviceName += " (" + getResources().getString(R.string.rileylink_scanner_selected_device) + ")";
            }
            viewHolder.deviceName.setText(deviceName);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

}

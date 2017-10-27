package info.nightscout.androidaps.plugins.PumpCombo.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpDanaRS.events.EventDanaRSDeviceChange;
import info.nightscout.utils.SP;

public class PairingActivity extends AppCompatActivity {
    private static Logger log = LoggerFactory.getLogger(PairingActivity.class);

    private Context mContext = null;

    private ListView listView = null;
    private PairingActivity.ListAdapter mListAdapter = null;
    private ArrayList<PairingActivity.BluetoothDeviceItem> mDevices = new ArrayList<>();

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothLeScanner mBluetoothLeScanner = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.danars_blescanner_activity);

        mListAdapter = new PairingActivity.ListAdapter();

        listView = (ListView) findViewById(R.id.danars_blescanner_listview);
        listView.setEmptyView(findViewById(R.id.danars_blescanner_nodevice));
        listView.setAdapter(mListAdapter);

        initView();
    }

    private void initView() {
        mContext = getApplicationContext();

        BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        // MIKE: test mBluetoothLeScanner for null (bt disabled)

        mListAdapter.notifyDataSetChanged();

    }

    @Override
    protected void onResume() {
        super.onResume();

        startScan();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopScan();
    }

    private void startScan() {
        mBluetoothLeScanner.startScan(mBleScanCallback);
    }

    private void stopScan() {
        mBluetoothLeScanner.stopScan(mBleScanCallback);
    }

    private void addBleDevice(BluetoothDevice device) {
        if (device == null || device.getName() == null || device.getName().equals("")) {
            return;
        }
        PairingActivity.BluetoothDeviceItem item = new PairingActivity.BluetoothDeviceItem(device);
        if (!isSNCheck(device.getName()) || mDevices.contains(item)) {
            return;
        }

        mDevices.add(item);
        new Handler().post(new Runnable() {
            public void run() {
                mListAdapter.notifyDataSetChanged();
            }
        });
    }

    private ScanCallback mBleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addBleDevice(result.getDevice());
        }
    };

    class ListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mDevices.size();
        }

        @Override
        public PairingActivity.BluetoothDeviceItem getItem(int i) {
            return mDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            View v = convertView;
            PairingActivity.ListAdapter.ViewHolder holder;

            if (v == null) {
                v = View.inflate(mContext, R.layout.danars_blescanner_item, null);
                holder = new PairingActivity.ListAdapter.ViewHolder(v);
                v.setTag(holder);
            } else {
                holder = (PairingActivity.ListAdapter.ViewHolder) v.getTag();
            }

            PairingActivity.BluetoothDeviceItem item = getItem(i);
            holder.setData(i, item);
            return v;
        }

        private class ViewHolder implements View.OnClickListener {
            private PairingActivity.BluetoothDeviceItem item = null;

            private TextView mName = null;
            private TextView mAddress = null;

            public ViewHolder(View v) {
                mName = (TextView) v.findViewById(R.id.ble_name);
                mAddress = (TextView) v.findViewById(R.id.ble_address);

                v.setOnClickListener(PairingActivity.ListAdapter.ViewHolder.this);
            }

            @Override
            public void onClick(View v) {
                SP.putString(R.string.key_danars_address, item.device.getAddress());
                SP.putString(R.string.key_danars_name, mName.getText().toString());
                MainApp.bus().post(new EventDanaRSDeviceChange());
                finish();
            }

            public void setData(int pos, PairingActivity.BluetoothDeviceItem data) {
                if (data != null) {
                    try {
                        String tTitle = data.device.getName();
                        if (tTitle == null || tTitle.equals("")) {
                            tTitle = "(unknown)";
                        } else if (tTitle.length() > 10) {
                            tTitle = tTitle.substring(0, 10);
                        }
                        mName.setText(tTitle);

                        mAddress.setText(data.device.getAddress());

                        item = data;
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    //
    private class BluetoothDeviceItem {
        private BluetoothDevice device;

        public BluetoothDeviceItem(BluetoothDevice device) {
            super();
            this.device = device;
        }

        @Override
        public boolean equals(Object o) {
            if (device == null || o == null || !(o instanceof PairingActivity.BluetoothDeviceItem)) {
                return false;
            }
            PairingActivity.BluetoothDeviceItem checkItem = (PairingActivity.BluetoothDeviceItem) o;
            if (checkItem.device == null) {
                return false;
            }
            return stringEquals(device.getAddress(), checkItem.device.getAddress());
        }

        public boolean stringEquals(String arg1, String arg2) {
            try {
                return arg1.equals(arg2);
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static boolean isSNCheck(String sn) {
        String regex = "^([a-zA-Z]{3})([0-9]{5})([a-zA-Z]{2})$";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(sn);

        return m.matches();
    }
}

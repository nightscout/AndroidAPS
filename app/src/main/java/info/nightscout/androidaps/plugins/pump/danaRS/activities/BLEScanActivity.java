package info.nightscout.androidaps.plugins.pump.danaRS.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.danaRS.events.EventDanaRSDeviceChange;
import info.nightscout.androidaps.utils.SP;

public class BLEScanActivity extends NoSplashAppCompatActivity {
    private ListAdapter mListAdapter = null;
    private ArrayList<BluetoothDeviceItem> mDevices = new ArrayList<>();

    private BluetoothLeScanner mBluetoothLeScanner = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.danars_blescanner_activity);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mListAdapter = new ListAdapter();

        ListView listView = findViewById(R.id.danars_blescanner_listview);
        listView.setEmptyView(findViewById(R.id.danars_blescanner_nodevice));
        listView.setAdapter(mListAdapter);

        mListAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
            if (!mBluetoothAdapter.isEnabled()) mBluetoothAdapter.enable();
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

            if (mBluetoothLeScanner == null) {
                mBluetoothAdapter.enable();
                mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            }
            startScan();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopScan();
    }

    private void startScan() {
        if (mBluetoothLeScanner != null)
            mBluetoothLeScanner.startScan(mBleScanCallback);
    }

    private void stopScan() {
        if (mBluetoothLeScanner != null)
            mBluetoothLeScanner.stopScan(mBleScanCallback);
    }

    private void addBleDevice(BluetoothDevice device) {
        if (device == null || device.getName() == null || device.getName().equals("")) {
            return;
        }
        BluetoothDeviceItem item = new BluetoothDeviceItem(device);
        if (!isSNCheck(device.getName()) || mDevices.contains(item)) {
            return;
        }

        mDevices.add(item);
        new Handler().post(() -> mListAdapter.notifyDataSetChanged());
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
        public BluetoothDeviceItem getItem(int i) {
            return mDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            View v = convertView;
            ViewHolder holder;

            if (v == null) {
                v = View.inflate(getApplicationContext(), R.layout.danars_blescanner_item, null);
                holder = new ViewHolder(v);
                v.setTag(holder);
            } else {
                holder = (ViewHolder) v.getTag();
            }

            BluetoothDeviceItem item = getItem(i);
            holder.setData(item);
            return v;
        }

        private class ViewHolder implements View.OnClickListener {
            private BluetoothDeviceItem item = null;

            private TextView mName;
            private TextView mAddress;

            ViewHolder(View v) {
                mName = v.findViewById(R.id.ble_name);
                mAddress = v.findViewById(R.id.ble_address);

                v.setOnClickListener(ViewHolder.this);
            }

            @Override
            public void onClick(View v) {
                SP.putString(R.string.key_danars_address, item.device.getAddress());
                SP.putString(R.string.key_danars_name, mName.getText().toString());
                item.device.createBond();
                RxBus.INSTANCE.send(new EventDanaRSDeviceChange());
                finish();
            }

            public void setData(BluetoothDeviceItem data) {
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
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    //
    private class BluetoothDeviceItem {
        private BluetoothDevice device;

        BluetoothDeviceItem(BluetoothDevice device) {
            super();
            this.device = device;
        }

        @Override
        public boolean equals(Object o) {
            if (device == null || !(o instanceof BluetoothDeviceItem)) {
                return false;
            }
            BluetoothDeviceItem checkItem = (BluetoothDeviceItem) o;
            if (checkItem.device == null) {
                return false;
            }
            return stringEquals(device.getAddress(), checkItem.device.getAddress());
        }

        boolean stringEquals(String arg1, String arg2) {
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

package info.nightscout.androidaps.plugins.pump.danaR;

import android.bluetooth.*;
import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import java.util.Set;
import java.util.Vector;

public class BluetoothDevicePreference extends ListPreference {

    public BluetoothDevicePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
        Vector<CharSequence> entries = new Vector<CharSequence>();
        if (bta != null) {
            Set<BluetoothDevice> pairedDevices = bta.getBondedDevices();
            for (BluetoothDevice dev : pairedDevices) {
                String name = dev.getName();
                if(name != null) {
                    entries.add(name);
                }
            }
        }
        setEntries(entries.toArray(new CharSequence[0]));
        setEntryValues(entries.toArray(new CharSequence[0]));
    }

    public BluetoothDevicePreference(Context context) {
        this(context, null);
    }

}

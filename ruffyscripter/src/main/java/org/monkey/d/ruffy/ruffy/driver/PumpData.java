package org.monkey.d.ruffy.ruffy.driver;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;

import java.security.InvalidKeyException;

/**
 * Created by fishermen21 on 20.05.17.
 */

public class PumpData {
    private String pumpMac;
    private Object pump_tf;
    private Object driver_tf;
    private byte address;
    private byte[] nonceTx;
    private Context activity;

    public PumpData(Context activity) {
        this.activity = activity;
        this.nonceTx = new byte[13];
    }

    public static PumpData loadPump(Context activity, IRTHandler handler) {
        PumpData data = new PumpData(activity);
        
        SharedPreferences prefs = activity.getSharedPreferences("pumpdata", Activity.MODE_PRIVATE);
        String dp = prefs.getString("dp","E9 24 39 46 84 8A B6 B7 B0 7E 90 C3 2E 2E C2 40 ");
        String pd = prefs.getString("pd","29 E2 BD 11 DE 42 49 24 40 30 70 61 DD 4A DF DD ");
        data.pumpMac = prefs.getString("device", "00:0E:2F:E8:A5:89");

        try {
            handler.log("Loading data of Pump "+data.pumpMac);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        if(data.pumpMac != null)
        {
            try {
                data.pump_tf = Twofish_Algorithm.makeKey(Utils.hexStringToByteArray(pd));
                data.driver_tf = Twofish_Algorithm.makeKey(Utils.hexStringToByteArray(dp));
                data.address = (byte)prefs.getInt("address",16); //0);

                data.nonceTx = Utils.hexStringToByteArray(prefs.getString("nonceTx", "F5 01 00 00 00 00 00 00 00 00 00 00 00 "));//"00 00 00 00 00 00 00 00 00 00 00 00 00"));

            } catch(Exception e)
            {
                e.printStackTrace();
                try {
                    handler.fail("unable to load keys!");
                } catch (RemoteException e1) {
                    e1.printStackTrace();
                }
                return null;
            }
        }
        return data;
    }

    public byte[] getNonceTx() {
        return nonceTx;
    }

    public void setAndSaveAddress(byte address) {
        this.address = address;
        SharedPreferences prefs = activity.getSharedPreferences("pumpdata", Activity.MODE_PRIVATE);
        prefs.edit().putInt("address",address).commit();

    }

    public byte getAddress() {
        return address;
    }

    public String getPumpMac() {
        return pumpMac;
    }

    public void resetTxNonce() {
        for (int i = 0; i < nonceTx.length; i++)
            nonceTx[i] = 0;
        SharedPreferences prefs = activity.getSharedPreferences("pumpdata", Activity.MODE_PRIVATE);
        prefs.edit().putString("nonceTx", Utils.byteArrayToHexString(nonceTx,nonceTx.length)).apply();
    }

    public void incrementNonceTx() {
        Utils.incrementArray(nonceTx);
        SharedPreferences prefs = activity.getSharedPreferences("pumpdata", Activity.MODE_PRIVATE);
        prefs.edit().putString("nonceTx", Utils.byteArrayToHexString(nonceTx,nonceTx.length)).apply();
    }

    public Context getActivity() {
        return activity;
    }

    public Object getToPumpKey() {
        return driver_tf;
    }

    public Object getToDeviceKey() {
        return pump_tf;
    }

    public void setAndSaveToDeviceKey(byte[] key_pd, Object tf) throws InvalidKeyException {

        byte[] key_pd_de = Twofish_Algorithm.blockDecrypt(key_pd, 0, tf);
        this.pump_tf = Twofish_Algorithm.makeKey(key_pd_de);

        SharedPreferences prefs = getActivity().getSharedPreferences("pumpdata", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("pd", Utils.byteArrayToHexString(key_pd_de,key_pd_de.length));
        editor.apply();
    }
    public void setAndSaveToPumpKey(byte[] key_dp, Object tf) throws InvalidKeyException {
        byte[] key_dp_de = Twofish_Algorithm.blockDecrypt(key_dp, 0, tf);

        this.driver_tf = Twofish_Algorithm.makeKey(key_dp_de);
        SharedPreferences prefs = getActivity().getSharedPreferences("pumpdata", Activity.MODE_PRIVATE);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("dp", Utils.byteArrayToHexString(key_dp_de,key_dp_de.length));
        editor.apply();

    }

    public void setAndSavePumpMac(String pumpMac) {
        this.pumpMac = pumpMac;
        SharedPreferences prefs = getActivity().getSharedPreferences("pumpdata", Activity.MODE_PRIVATE);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("device",pumpMac);
        editor.putBoolean("paired",true);
        editor.apply();
    }
}

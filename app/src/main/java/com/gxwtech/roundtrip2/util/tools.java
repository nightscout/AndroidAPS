package com.gxwtech.roundtrip2.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.ClipboardManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Created by Tim on 15/06/2016.
 */
public class tools {
    final static String TAG = "Tools";

    public static void showLogging(){
        String logCat = "no logs";
        final String processId = Integer.toString(android.os.Process.myPid());
        try {
            Process process = Runtime.getRuntime().exec("logcat -d");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder log = new StringBuilder();
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                if(line.contains(processId)) log.append(line + "\n");
            }
            logCat = log.toString();

        } catch (IOException e) {
            logCat = e.getLocalizedMessage();
        } finally {
            //showAlertText(logCat, MainApp.instance());
            showAlertText(logCat, null);
        }
    }

    public static void showAlertText(final String msg, final Context context){
//        try {
//            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.mContext) // TODO: 09/07/2016 @TIM this should not be needed, should be context
//                    .setMessage(msg)
//                    .setPositiveButton("Copy to Clipboard", new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int which) {
//                            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
//                            clipboard.setText(msg);
//                            Toast.makeText(MainApp.instance(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
//                        }
//                    })
//                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int which) {
//                            // do nothing
//                        }
//                    })
//                    .show();
//
//            if (msg.length() > 100) {
//                TextView textView = (TextView) alertDialog.findViewById(android.R.id.message);
//                textView.setTextSize(10);
//            }
//        } catch (Exception e){
//            //Crashlytics.logException(e);
//            Log.e(TAG, "showAlertText: " + e.getLocalizedMessage());
//        }

        Log.e(TAG, "showAlertText: " + msg);
    }

    public static Double round(Double value, int decPoints){
        if (value == null || value.isInfinite() || value.isNaN()) return 0D;
        DecimalFormat df;
        DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
        otherSymbols.setDecimalSeparator('.');
        otherSymbols.setGroupingSeparator(',');

        switch (decPoints){
            case 1:
                //if (precisionRounding()){
                //    df = new DecimalFormat("##0.00", otherSymbols);
                //} else {
                    df = new DecimalFormat("##0.0", otherSymbols);
                //}
                break;
            case 2:
                df = new DecimalFormat("##0.00", otherSymbols);
                break;
            case 3:
                df = new DecimalFormat("##0.000", otherSymbols);
                break;
            default:
                df = new DecimalFormat("##0.0000", otherSymbols);
        }
        return Double.parseDouble(df.format(value));
    }


    public static String formatDisplayInsulin(Double value, int decPoints){
        return round(value,decPoints) + "u";
    }
    public static String formatDisplayBasal(Double value, Boolean doubleLine){
        if (doubleLine) {
            return round(value, 2) + "\n" + "U/h";
        } else {
            return round(value, 2) + "U/h";
        }
    }
}

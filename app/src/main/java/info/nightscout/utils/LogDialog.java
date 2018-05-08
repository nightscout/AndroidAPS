package info.nightscout.utils;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ClipboardManager;
import android.widget.TextView;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 09.02.2017.
 */

public class LogDialog {

    public static void showLogcat(Context context) {
        String logCat = "no logs";
        final String processId = Integer.toString(android.os.Process.myPid());
        try {
            Process process = Runtime.getRuntime().exec("logcat -d " + MainApp.gs(R.string.app_name) + ":D");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder log = new StringBuilder();
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(processId)) log.append(line + "\n");
            }
            logCat = log.toString();

        } catch (IOException e) {
            logCat = e.getLocalizedMessage();
        } finally {
            showAlertText(logCat, context);
        }
    }

    public static void showAlertText(final String msg, final Context context) {
        try {
            AlertDialog alertDialog = new AlertDialog.Builder(context)
                    .setMessage(msg)
                    .setPositiveButton(MainApp.gs(R.string.copy_to_clipboard), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                            clipboard.setPrimaryClip(ClipData.newPlainText(null, msg));
                            ToastUtils.showToastInUiThread(context, MainApp.gs(R.string.copied_to_clipboard));
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();

            if (msg.length() > 100) {
                TextView textView = (TextView) alertDialog.findViewById(android.R.id.message);
                textView.setTextSize(10);
            }
        } catch (Exception e) {
            // crashing on screen rotation
        }
    }
}

package info.nightscout.utils;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 31.03.2017.
 */

public class OKDialog {
    private static Logger log = LoggerFactory.getLogger(OKDialog.class);

    public static void show(final Activity activity, String title, String message, final Runnable runnable) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.AppTheme));
            builder.setTitle(title);
            builder.setMessage(message);
            builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    if (runnable != null) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
                        activity.runOnUiThread(runnable);
                    }
                }
            });

            builder.create().show();
        } catch (Exception e) {
            log.debug("show_dialog exception: " + e);
        }
    }
}

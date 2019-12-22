package info.nightscout.androidaps.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class OKDialog {
    public static void show(final Context context, String title, String message, final Runnable runnable) {
        if (title.isEmpty()) title = MainApp.gs(R.string.message);
        View titleLayout = LayoutInflater.from(context).inflate(R.layout.dialog_alert_custom, null);
        ((TextView)titleLayout.findViewById(R.id.alertdialog_title)).setText(title);
        ((ImageView)titleLayout.findViewById(R.id.alertdialog_icon)).setImageResource(R.drawable.ic_check_while_48dp);

        new AlertDialog.Builder(new ContextThemeWrapper(context, R.style.AppTheme))
                .setCustomTitle(titleLayout)
                .setMessage(message)
                .setPositiveButton(MainApp.gs(R.string.ok), (dialog, which) -> {
                    dialog.dismiss();
                    if (runnable != null) {
                        SystemClock.sleep(100);
                        runOnUiThread(runnable);
                    }
                })
                .show()
                .setCanceledOnTouchOutside(false);
    }

    public static boolean runOnUiThread(Runnable theRunnable) {
        final Handler mainHandler = new Handler(MainApp.instance().getApplicationContext().getMainLooper());
        return mainHandler.post(theRunnable);
    }

    public static void show(final Activity activity, String title, Spanned message, final Runnable runnable) {
        if (title.isEmpty()) title = MainApp.gs(R.string.message);
        View titleLayout = activity.getLayoutInflater().inflate(R.layout.dialog_alert_custom, null);
        ((TextView)titleLayout.findViewById(R.id.alertdialog_title)).setText(title);
        ((ImageView)titleLayout.findViewById(R.id.alertdialog_icon)).setImageResource(R.drawable.ic_check_while_48dp);

        new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.AppTheme))
                .setCustomTitle(titleLayout)
                .setMessage(message)
                .setPositiveButton(MainApp.gs(R.string.ok), (dialog, which) -> {
                    dialog.dismiss();
                    if (runnable != null) {
                        SystemClock.sleep(100);
                        activity.runOnUiThread(runnable);
                    }
                })
                .show()
                .setCanceledOnTouchOutside(false);
    }

    public static void showConfirmation(final Activity activity, String message, final Runnable ok) {
        showConfirmation(activity, message, ok, null);
    }

    public static void showConfirmation(final Activity activity, Spanned message, final Runnable ok) {
        showConfirmation(activity, message, ok, null);
    }

    public static void showConfirmation(final Activity activity, Spanned message, final Runnable ok, final Runnable cancel) {
        View titleLayout = activity.getLayoutInflater().inflate(R.layout.dialog_alert_custom, null);
        ((TextView)titleLayout.findViewById(R.id.alertdialog_title)).setText(R.string.confirmation);
        ((ImageView)titleLayout.findViewById(R.id.alertdialog_icon)).setImageResource(R.drawable.ic_check_while_48dp);

        new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.AppTheme))
                .setMessage(message)
                .setCustomTitle(titleLayout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    if (ok != null) {
                        SystemClock.sleep(100);
                        activity.runOnUiThread(ok);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    if (cancel != null) {
                        SystemClock.sleep(100);
                        activity.runOnUiThread(cancel);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show()
                .setCanceledOnTouchOutside(false);
    }

    public static void showConfirmation(final Activity activity, String message, final Runnable ok, final Runnable cancel) {
        View titleLayout = activity.getLayoutInflater().inflate(R.layout.dialog_alert_custom, null);
        ((TextView)titleLayout.findViewById(R.id.alertdialog_title)).setText(R.string.confirmation);
        ((ImageView)titleLayout.findViewById(R.id.alertdialog_icon)).setImageResource(R.drawable.ic_check_while_48dp);

        new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.AppTheme))
                .setMessage(message)
                .setCustomTitle(titleLayout)
                .setView(R.layout.dialog_alert_custom)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    if (ok != null) {
                        SystemClock.sleep(100);
                        activity.runOnUiThread(ok);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    if (cancel != null) {
                        SystemClock.sleep(100);
                        activity.runOnUiThread(cancel);
                    }
                })
                .show()
                .setCanceledOnTouchOutside(false);
    }

}

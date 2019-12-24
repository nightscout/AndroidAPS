package info.nightscout.androidaps.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 14.02.2017.
 */

public class PasswordProtection {
    static public boolean isLocked(String preference) {
        final String password = SP.getString(preference, "");
        return !password.equals("");
    }

    static public void QueryPassword(final Context context, int stringID, String preference, final Runnable ok, final Runnable fail) {
        final String password = SP.getString(preference, "");
        if (password.equals("")) {
            if (ok != null) ok.run();
            return;
        }
        View promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null);

        View titleLayout = LayoutInflater.from(context).inflate(R.layout.dialog_alert_custom, null);
        ((TextView) titleLayout.findViewById(R.id.alertdialog_title)).setText(R.string.confirmation);
        ((ImageView) titleLayout.findViewById(R.id.alertdialog_icon)).setImageResource(R.drawable.ic_check_while_48dp);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setView(promptsView);
        alertDialogBuilder.setCustomTitle(titleLayout);

        final TextView label = promptsView.findViewById(R.id.passwordprompt_text);
        label.setText(MainApp.gs(stringID));
        final EditText userInput = promptsView.findViewById(R.id.passwordprompt_pass);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok,
                        (dialog, id) -> {
                            String enteredPassword = userInput.getText().toString();
                            if (password.equals(enteredPassword)) {
                                if (ok != null) ok.run();
                            } else {
                                ToastUtils.showToastInUiThread(context, MainApp.gs(R.string.wrongpassword));
                                if (fail != null) fail.run();
                            }
                        })
                .setNegativeButton(android.R.string.cancel,
                        (dialog, id) -> dialog.cancel());

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
        alertDialog.setCanceledOnTouchOutside(false);
    }
}

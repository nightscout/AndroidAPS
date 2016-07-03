package info.nightscout.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

/**
 * Created by mike on 03.07.2016.
 */
public class ImportExportPrefs {
    static File path = new File(Environment.getExternalStorageDirectory().toString());
    static final File file = new File(path, MainApp.resources.getString(R.string.app_name) + "Preferences");

    //exports shared Preferences
    public static void exportSharedPreferences(final Context c) {

        new AlertDialog.Builder(c)
                .setMessage(MainApp.resources.getString(R.string.export_to) + " " + path + "/" + file + "?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
                        try {
                            FileWriter fw = new FileWriter(file);
                            PrintWriter pw = new PrintWriter(fw);
                            Map<String, ?> prefsMap = prefs.getAll();
                            for (Map.Entry<String, ?> entry : prefsMap.entrySet()) {
                                pw.println(entry.getKey() + "::" + entry.getValue().toString());
                            }
                            pw.close();
                            fw.close();
                            ToastUtils.showToastInUiThread(c, MainApp.resources.getString(R.string.exported));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void importSharedPreferences(final Context c) {
        new AlertDialog.Builder(c)
                .setMessage(MainApp.resources.getString(R.string.import_from) + " " + path + "/" + file + "?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
                        SharedPreferences.Editor editor = prefs.edit();
                        String line;
                        String[] lineParts;
                        try {
                            editor.clear();
                            editor.commit();

                            BufferedReader reader = new BufferedReader(new FileReader(file));
                            while ((line = reader.readLine()) != null) {
                                lineParts = line.split("::");
                                if (lineParts.length == 2) {
                                    if (lineParts[1].equals("true") || lineParts[1].equals("false")) {
                                        editor.putBoolean(lineParts[0], Boolean.parseBoolean(lineParts[1]));
                                    } else {
                                        editor.putString(lineParts[0], lineParts[1]);
                                    }
                                }
                            }
                            reader.close();
                            editor.commit();
                            ToastUtils.showToastInUiThread(c, MainApp.resources.getString(R.string.setting_imported));
                        } catch (FileNotFoundException e) {
                            ToastUtils.showToastInUiThread(c, MainApp.resources.getString(R.string.filenotfound) + " " + file);
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

}

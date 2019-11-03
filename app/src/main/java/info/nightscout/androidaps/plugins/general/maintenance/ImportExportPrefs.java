package info.nightscout.androidaps.plugins.general.maintenance;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.ToastUtils;

/**
 * Created by mike on 03.07.2016.
 */

public class ImportExportPrefs {
    private static Logger log = LoggerFactory.getLogger(L.CORE);
    static File path = new File(Environment.getExternalStorageDirectory().toString());
    static public final File file = new File(path, MainApp.gs(R.string.app_name) + "Preferences");

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public static void verifyStoragePermissions(Fragment fragment) {
        int permission = ContextCompat.checkSelfPermission(fragment.getContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            fragment.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }

    }

    public static void exportSharedPreferences(final Fragment f) {
        exportSharedPreferences(f.getContext());
    }

    public static void exportSharedPreferences(final Context c) {

        new AlertDialog.Builder(c)
                .setMessage(MainApp.gs(R.string.export_to) + " " + file + " ?")
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {

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
                        ToastUtils.showToastInUiThread(c, MainApp.gs(R.string.exported));
                    } catch (FileNotFoundException e) {
                        ToastUtils.showToastInUiThread(c, MainApp.gs(R.string.filenotfound) + " " + file);
                        log.error("Unhandled exception", e);
                    } catch (IOException e) {
                        log.error("Unhandled exception", e);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void importSharedPreferences(final Fragment fragment) {
        importSharedPreferences(fragment.getContext());
    }

    public static void importSharedPreferences(final Context context) {
        new AlertDialog.Builder(context)
                .setMessage(MainApp.gs(R.string.import_from) + " " + file + " ?")
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {

                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
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
                        OKDialog.show(context, MainApp.gs(R.string.setting_imported), MainApp.gs(R.string.restartingapp), () -> {
                            log.debug("Exiting");
                            MainApp.instance().stopKeepAliveService();
                            RxBus.INSTANCE.send(new EventAppExit());
                            MainApp.closeDbHelper();
                            if (context instanceof Activity) {
                                ((Activity)context).finish();
                            }
                            System.runFinalization();
                            System.exit(0);
                        });
                    } catch (FileNotFoundException e) {
                        ToastUtils.showToastInUiThread(context, MainApp.gs(R.string.filenotfound) + " " + file);
                        log.error("Unhandled exception", e);
                    } catch (IOException e) {
                        log.error("Unhandled exception", e);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

}

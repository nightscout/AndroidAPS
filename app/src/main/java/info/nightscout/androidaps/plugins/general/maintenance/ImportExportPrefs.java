package info.nightscout.androidaps.plugins.general.maintenance;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

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
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;

/**
 * Created by mike on 03.07.2016.
 */

public class ImportExportPrefs {
    private static Logger log = LoggerFactory.getLogger(L.CORE);
    private static File path = new File(Environment.getExternalStorageDirectory().toString());
    static public final File file = new File(path, MainApp.gs(R.string.app_name) + "Preferences");

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    static void verifyStoragePermissions(Fragment fragment) {
        int permission = ContextCompat.checkSelfPermission(fragment.getContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            fragment.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }

    }

    static void exportSharedPreferences(final Fragment f) {
        exportSharedPreferences(f.getContext());
    }

    private static void exportSharedPreferences(final Context context) {
        OKDialog.showConfirmation(context, MainApp.gs(R.string.maintenance), MainApp.gs(R.string.export_to) + " " + file + " ?", () -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            try {
                FileWriter fw = new FileWriter(file);
                PrintWriter pw = new PrintWriter(fw);
                Map<String, ?> prefsMap = prefs.getAll();
                for (Map.Entry<String, ?> entry : prefsMap.entrySet()) {
                    pw.println(entry.getKey() + "::" + entry.getValue().toString());
                }
                pw.close();
                fw.close();
                ToastUtils.showToastInUiThread(context, MainApp.gs(R.string.exported));
            } catch (FileNotFoundException e) {
                ToastUtils.showToastInUiThread(context, MainApp.gs(R.string.filenotfound) + " " + file);
                log.error("Unhandled exception", e);
            } catch (IOException e) {
                log.error("Unhandled exception", e);
            }
        });
    }

    static void importSharedPreferences(final Fragment fragment) {
        importSharedPreferences(fragment.getContext());
    }

    public static void importSharedPreferences(final Context context) {
        OKDialog.showConfirmation(context, MainApp.gs(R.string.maintenance), MainApp.gs(R.string.import_from) + " " + file + " ?", () -> {
            String line;
            String[] lineParts;
            try {
                SP.clear();

                BufferedReader reader = new BufferedReader(new FileReader(file));
                while ((line = reader.readLine()) != null) {
                    lineParts = line.split("::");
                    if (lineParts.length == 2) {
                        if (lineParts[1].equals("true") || lineParts[1].equals("false")) {
                            SP.putBoolean(lineParts[0], Boolean.parseBoolean(lineParts[1]));
                        } else {
                            SP.putString(lineParts[0], lineParts[1]);
                        }
                    }
                }
                reader.close();
                SP.putBoolean(R.string.key_setupwizard_processed, true);
                OKDialog.show(context, MainApp.gs(R.string.setting_imported), MainApp.gs(R.string.restartingapp), () -> {
                    log.debug("Exiting");
                    RxBus.INSTANCE.send(new EventAppExit());
                    if (context instanceof Activity) {
                        ((Activity) context).finish();
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
        });
    }
}

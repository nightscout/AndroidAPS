package info.nightscout.androidaps.plugins.Maintenance;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.Food.FoodPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.ImportExportPrefs;
import info.nightscout.utils.LoggerUtils;
import info.nightscout.utils.SP;

public class MaintenancePlugin extends PluginBase {

    private static final Logger LOG = LoggerFactory.getLogger(MaintenancePlugin.class);

    private final Context ctx;

    private static MaintenancePlugin maintenancePlugin;

    public static MaintenancePlugin getPlugin() {
        return maintenancePlugin;
    }

    public static MaintenancePlugin initPlugin(Context ctx) {

        if (maintenancePlugin == null) {
            maintenancePlugin = new MaintenancePlugin(ctx);
        }

        return maintenancePlugin;
    }

    public MaintenancePlugin() {
        // required for testing
        super(null);
        this.ctx = null;
    }

    MaintenancePlugin(Context ctx) {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(MaintenanceFragment.class.getName())
                .alwayVisible(true)
                .alwaysEnabled(true)
                .pluginName(R.string.maintenance)
                .shortName(R.string.maintenance_shortname)
                .preferencesId(R.xml.pref_maintenance)
                .description(R.string.description_maintenance)
        );
        this.ctx = ctx;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {

    }

    public void sendLogs() {
        String recipient = SP.getString("key_maintenance_logs_email", "logs@androidaps.org");
        int amount = SP.getInt("key_maintenance_logs_amount", 2);

        String logDirectory = LoggerUtils.getLogDirectory();
        List<File> logs = this.getLogfiles(logDirectory, amount);

        File zipDir = this.ctx.getExternalFilesDir("exports");
        File zipFile = new File(zipDir, this.constructName());

        LOG.debug("zipFile: {}", zipFile.getAbsolutePath());
        File zip = this.zipLogs(zipFile, logs);

        Uri attachementUri = FileProvider.getUriForFile(this.ctx, "info.nightscout.androidaps.fileprovider", zip);
        Intent emailIntent = this.sendMail(attachementUri, recipient, "Log Export");
        LOG.debug("sending emailIntent");
        ctx.startActivity(emailIntent);
    }

    public void deleteLogs() {
        String logDirectory = LoggerUtils.getLogDirectory();
        File logDir = new File(logDirectory);

        File[] files = logDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                return name.startsWith("AndroidAPS")
                            && name.endsWith(".zip");
            }
        });

        Arrays.sort(files, new Comparator<File>() {
            public int compare(File f1, File f2) {
                return f1.getName().compareTo(f2.getName());
            }
        });

        List<File> delFiles = Arrays.asList(files);
        int amount = SP.getInt("key_logshipper_amount", 2);
        int keepIndex = amount - 1;

        if (keepIndex < delFiles.size()) {
            delFiles = delFiles.subList(keepIndex, delFiles.size());

            for (File file : delFiles) {
                file.delete();
            }
        }

        File exportDir = new File(logDirectory, "exports");

        if (exportDir.exists()) {
            File[] expFiles = exportDir.listFiles();

            for (File file : expFiles) {
                file.delete();
            }
            exportDir.delete();
        }
    }

    public void resetDb() {
//        new AlertDialog.Builder(this)
//                .setTitle(R.string.nav_resetdb)
//                .setMessage(R.string.reset_db_confirm)
//                .setNegativeButton(android.R.string.cancel, null)
//                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
//                    MainApp.getDbHelper().resetDatabases();
//                    // should be handled by Plugin-Interface and
//                    // additional service interface and plugin registry
//                    FoodPlugin.getPlugin().getService().resetFood();
//                    TreatmentsPlugin.getPlugin().getService().resetTreatments();
//                })
//                .create()
//                .show();
    }

    public void exportSettings() {
//        ImportExportPrefs.verifyStoragePermissions(this);
//        ImportExportPrefs.exportSharedPreferences(this);
    }

    public void importSettings() {
//        ImportExportPrefs.verifyStoragePermissions(this);
//        ImportExportPrefs.importSharedPreferences(this);
    }

    /**
     * returns a list of log files. The number of returned logs is given via the amount
     * parameter. The log files are sorted by the name descending.
     *
     * @param directory
     * @param amount
     * @return
     */
    public List<File> getLogfiles(String directory, int amount) {
        LOG.debug("getting {} logs from directory {}", amount, directory);
        File logDir = new File(directory);

        File[] files = logDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                return name.startsWith("AndroidAPS")
                        && (name.endsWith(".log")
                        || (name.endsWith(".zip") && !name.endsWith(LoggerUtils.SUFFIX)));
            }
        });

        Arrays.sort(files, new Comparator<File>() {
            public int compare(File f1, File f2) {
                return f2.getName().compareTo(f1.getName());
            }
        });

        List<File> result = Arrays.asList(files);
        int toIndex = amount++;

        if (toIndex > result.size()) {
            toIndex = result.size();
        }

        LOG.debug("returning sublist 0 to {}", toIndex);
        return result.subList(0, toIndex);
    }

    /**
     * Zips the given files in a zipfile which is stored in the given zipDir using the givven
     * name.
     *
     * @param zipFile
     * @param files
     * @return
     */
    public File zipLogs(File zipFile, List<File> files) {
        LOG.debug("creating zip {}", zipFile.getAbsolutePath());

        try {
            zip(zipFile, files);
        } catch (IOException e) {
            LOG.error("Cannot retrieve zip", e);
        }

        return zipFile;
    }

    /**
     * construct the name of zip file which is used to export logs.
     *
     * The name is constructed using the following scheme:
     * AndroidAPS_LOG_ + Long Time + .log.zip
     *
     * @return
     */
    public String constructName() {
        return "AndroidAPS_LOG_" + String.valueOf(new Date().getTime()) + LoggerUtils.SUFFIX;
    }

    /**
     * This method stores all given files inside the given zipFile.
     *
     * @param zipFile
     * @param files
     * @throws IOException
     */
    public void zip(File zipFile, List<File> files) throws IOException {
        final int BUFFER_SIZE = 2048;

        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));

        for (File file : files) {
            byte data[] = new byte[BUFFER_SIZE];

            try(FileInputStream fileInputStream = new FileInputStream( file )) {

                try(BufferedInputStream origin = new BufferedInputStream(fileInputStream, BUFFER_SIZE)) {
                    ZipEntry entry = new ZipEntry(file.getName());

                    out.putNextEntry(entry);
                    int count;
                    while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
                        out.write(data, 0, count);
                    }

                }
            }
        }

        out.close();
    }

    /**
     * send a mail with the given file to the recipients with the given subject.
     *
     * the returned intent should be used to really send the mail using
     *
     * startActivity(Intent.createChooser(emailIntent , "Send email..."));
     *
     * @param attachementUri
     * @param recipient
     * @param subject
     * @return
     */
    public static Intent sendMail(Uri attachementUri, String recipient, String subject) {
        LOG.debug("sending email to {} with subject {}", recipient, subject);
        Intent emailIntent = new Intent(Intent.ACTION_SEND);

        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL  , new String[]{recipient});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, "");

        LOG.debug("put path {}", attachementUri.toString());
        emailIntent.putExtra(Intent.EXTRA_STREAM, attachementUri);
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return emailIntent;
    }


}

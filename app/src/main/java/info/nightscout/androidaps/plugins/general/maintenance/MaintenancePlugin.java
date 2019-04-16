package info.nightscout.androidaps.plugins.general.maintenance;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.FileProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.utils.SP;

public class MaintenancePlugin extends PluginBase {

    private static final Logger LOG = LoggerFactory.getLogger(L.CORE);

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
                .alwayVisible(false)
                .alwaysEnabled(true)
                .pluginName(R.string.maintenance)
                .shortName(R.string.maintenance_shortname)
                .preferencesId(R.xml.pref_maintenance)
                .description(R.string.description_maintenance)
        );
        this.ctx = ctx;
    }

    public void sendLogs() {
        String recipient = SP.getString(R.string.key_maintenance_logs_email, "logs@androidaps.org");
        int amount = SP.getInt(R.string.key_maintenance_logs_amount, 2);

        String logDirectory = LoggerUtils.getLogDirectory();
        List<File> logs = this.getLogfiles(logDirectory, amount);

        File zipDir = this.ctx.getExternalFilesDir("exports");
        File zipFile = new File(zipDir, this.constructName());

        LOG.debug("zipFile: {}", zipFile.getAbsolutePath());
        File zip = this.zipLogs(zipFile, logs);

        Uri attachementUri = FileProvider.getUriForFile(this.ctx, BuildConfig.APPLICATION_ID + ".fileprovider", zip);
        Intent emailIntent = this.sendMail(attachementUri, recipient, "Log Export");
        LOG.debug("sending emailIntent");
        ctx.startActivity(emailIntent);
    }

    //todo replace this with a call on startup of the application, specifically to remove
    // unnecessary garbage from the log exports
    public void deleteLogs() {
        String logDirectory = LoggerUtils.getLogDirectory();
        File logDir = new File(logDirectory);

        File[] files = logDir.listFiles((file, name) -> name.startsWith("AndroidAPS")
                    && name.endsWith(".zip"));

        Arrays.sort(files, (f1, f2) -> f1.getName().compareTo(f2.getName()));

        List<File> delFiles = Arrays.asList(files);
        int amount = SP.getInt(R.string.key_logshipper_amount, 2);
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

    /**
     * returns a list of log files. The number of returned logs is given via the amount
     * parameter.
     *
     * The log files are sorted by the name descending.
     *
     * @param directory
     * @param amount
     * @return
     */
    public List<File> getLogfiles(String directory, int amount) {
        LOG.debug("getting {} logs from directory {}", amount, directory);
        File logDir = new File(directory);

        File[] files = logDir.listFiles((file, name) -> name.startsWith("AndroidAPS")
                && (name.endsWith(".log")
                || (name.endsWith(".zip") && !name.endsWith(LoggerUtils.SUFFIX))));

        Arrays.sort(files, (f1, f2) -> f2.getName().compareTo(f1.getName()));

        List<File> result = Arrays.asList(files);
        int toIndex = amount++;

        if (toIndex > result.size()) {
            toIndex = result.size();
        }

        LOG.debug("returning sublist 0 to {}", toIndex);
        return result.subList(0, toIndex);
    }

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

    public static Intent sendMail(Uri attachementUri, String recipient, String subject)  {
        StringBuilder builder =new StringBuilder();
        
        builder.append("ADD TIME OF EVENT HERE: " + System.lineSeparator());
        builder.append("ADD ISSUE DESCRIPTION OR GITHUB ISSUE REFERENCE NUMBER: " + System.lineSeparator());
        builder.append("-------------------------------------------------------" + System.lineSeparator());
        builder.append("(Please remember this will send only very recent logs." + System.lineSeparator());
        builder.append("If you want to provide logs for event older than a few hours," + System.lineSeparator());
        builder.append("you have to do it manually)" + System.lineSeparator());
        builder.append("-------------------------------------------------------" + System.lineSeparator());
        builder.append(MainApp.gs(R.string.app_name) + " " + BuildConfig.VERSION + System.lineSeparator());
        if (Config.NSCLIENT)
            builder.append("NSCLIENT" + System.lineSeparator());

        builder.append("Build: " + BuildConfig.BUILDVERSION + System.lineSeparator());
        builder.append("Remote: " + BuildConfig.REMOTE + System.lineSeparator());
        builder.append("Flavor: " + BuildConfig.FLAVOR + BuildConfig.BUILD_TYPE + System.lineSeparator());
        builder.append(MainApp.gs(R.string.configbuilder_nightscoutversion_label) + " " + NSSettingsStatus.getInstance().nightscoutVersionName + System.lineSeparator());
        if (MainApp.engineeringMode)
            builder.append(MainApp.gs(R.string.engineering_mode_enabled));

        return sendMail(attachementUri, recipient, subject, builder.toString());
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
     * @param body
     *
     * @return
     */
    public static Intent sendMail(Uri attachementUri, String recipient, String subject, String body) {
        LOG.debug("sending email to {} with subject {}", recipient, subject);
        Intent emailIntent = new Intent(Intent.ACTION_SEND);

        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL  , new String[]{recipient});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);

        LOG.debug("put path {}", attachementUri.toString());
        emailIntent.putExtra(Intent.EXTRA_STREAM, attachementUri);
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return emailIntent;
    }


}

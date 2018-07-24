package info.nightscout.utils;

import android.content.Intent;
import android.net.Uri;

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

import ch.qos.logback.classic.LoggerContext;

/**
 * This class provides serveral methods for log-handling (eg. sending logs as emails).
 */
public class LoggerUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerUtils.class);

    private static String SUFFIX = ".log.zip";

    /**
     * Returns the directory, in which the logs are stored on the system. This is configured in the
     * logback.xml file.
     *
     * @return
     */
    public static String getLogDirectory() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        return lc.getProperty("EXT_FILES_DIR");
    }


    /**
     * returns a list of log files. The number of returned logs is given via the amount
     * parameter. The log files are sorted by the name descending.
     *
     * @param directory
     * @param amount
     * @return
     */
    public static List<File> getLogfiles(String directory, int amount) {
        LOGGER.debug("getting {} logs from directory {}", amount, directory);
        File logDir = new File(directory);

        File[] files = logDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                return name.startsWith("AndroidAPS")
                        && (name.endsWith(".log")
                        || (name.endsWith(".zip") && !name.endsWith(SUFFIX)));
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

        LOGGER.debug("returning sublist 0 to {}", toIndex);
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
    public static File zipLogs(File zipFile, List<File> files) {
        LOGGER.debug("creating zip {}", zipFile.getAbsolutePath());

        try {
            zip(zipFile, files);
        } catch (IOException e) {
            LOGGER.error("Cannot retrieve zip", e);
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
    public static String constructName() {
        return "AndroidAPS_LOG_" + String.valueOf(new Date().getTime()) + SUFFIX;
    }

    /**
     * This method stores all given files inside the given zipFile.
     *
     * @param zipFile
     * @param files
     * @throws IOException
     */
    public static void zip(File zipFile, List<File> files) throws IOException {
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
        LOGGER.debug("sending email to {} with subject {}", recipient, subject);
        Intent emailIntent = new Intent(Intent.ACTION_SEND);

        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL  , new String[]{recipient});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, "");

        LOGGER.debug("put path {}", attachementUri.toString());
        emailIntent.putExtra(Intent.EXTRA_STREAM, attachementUri);
        emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return emailIntent;
    }

}

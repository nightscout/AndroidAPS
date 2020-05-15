package info.nightscout.androidaps.plugins.general.autotune;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import info.nightscout.androidaps.plugins.general.maintenance.LoggerUtils;
import info.nightscout.androidaps.utils.DateUtil;

public class AutotuneFS {
    static final String logDirectory = LoggerUtils.getLogDirectory();
    static final int BUFFER_SIZE = 2048;
    static final String AUTOTUNEFOLDER = "autotune";
    static final String SETTINGSFOLDER = "settings";
    static final String RECOMMENDATIONS = "autotune_recommendations.log";
    static final String SETTINGS = "settings.json";
    static final String PROFIL = "profil";

    static File autotune_path;
    static File autotune_settings;

    /*****************************************************************************
     * Create autotune folder for all files created during an autotune session
     ****************************************************************************/
    public static void createAutotuneFolder() {
        //create autotune subfolder for autotune files if not exists
        autotune_path = new File(logDirectory, AUTOTUNEFOLDER);
        if (! (autotune_path.exists() && autotune_path.isDirectory())) {
            autotune_path.mkdir();
        }
        autotune_settings = new File(logDirectory, SETTINGSFOLDER);
        if (! (autotune_settings.exists() && autotune_settings.isDirectory())) {
            autotune_settings.mkdir();
        }
    }

    /*****************************************************************************
     * between each run of autotune, clean autotune folder content
     ****************************************************************************/
    public static void deleteAutotuneFiles() {
        for (File file : autotune_path.listFiles()) {
            if(file.isFile())
                file.delete();
        }
        for (File file : autotune_settings.listFiles()) {
            if(file.isFile())
                file.delete();
        }
    }

    /*****************************************************************************
     * Create a JSON autotune file or settings file
     *****************************************************************************/
    public static void createAutotunefile(String fileName, String stringFile) {
        createAutotunefile(fileName, stringFile, false);
    }

    public static void createAutotunefile(String fileName, String stringFile, boolean isSettingFile) {
        if (fileName != null && !fileName.isEmpty()) {
            if (stringFile.isEmpty())
                stringFile = "";
            File autotuneFile = new File(isSettingFile ? autotune_settings.getAbsolutePath() : autotune_path.getAbsolutePath() , fileName);
            try {
                FileWriter fw = new FileWriter(autotuneFile);
                PrintWriter pw = new PrintWriter(fw);
                pw.println(stringFile);
                pw.close();
                fw.close();
            } catch (FileNotFoundException e) {
                //log.error("Unhandled exception", e);
            } catch (IOException e) {
                //log.error("Unhandled exception", e);
            }
        }
    }

    public static String profilName(Date daterun) {
        String strdate = "";
        String prefixe = "aaps-";
        if (daterun != null) {
            prefixe = "aaps-new";
            strdate= "." + formatDate(daterun);
        }
        return prefixe + PROFIL + strdate + ".json";
    }

    public static String profilName(long longdayrun) {
        Date daterun = null;
        if (longdayrun != 0) {
            daterun = new Date(longdayrun);
        }
        return profilName(daterun);
    }


    /**********************************************************************************
     * create a zip file with all autotune files and settings in autotune folder at the end of run
     *********************************************************************************/

    public static void zipAutotune(Date lastRun) {
        if (lastRun!=null) {
            try {
                String zipFileName = "Autotune_" + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".zip";
                File zipFile = new File(logDirectory, zipFileName);
                ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
                if (autotune_path != null)
                    zipDirectory(autotune_path, autotune_path.getName(), out);
                if (autotune_settings != null)
                    zipDirectory(autotune_settings, autotune_settings.getName(), out);
                out.flush();
                out.close();
            } catch (IOException e) {}

        }
    }

    private static void zipDirectory(File folder, String parentFolder, ZipOutputStream out) {

        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                zipDirectory(file, parentFolder + "/" + file.getName(), out);
                continue;
            }
            try {
                out.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                //long bytesRead = 0;
                byte[] bytesIn = new byte[BUFFER_SIZE];
                int read = 0;
                while ((read = bis.read(bytesIn)) != -1) {
                    out.write(bytesIn, 0, read);
                    //bytesRead += read;
                }
                out.closeEntry();
            } catch (IOException e) {}
        }
    }

    public static String formatDate(Date date){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(date);
    }

}

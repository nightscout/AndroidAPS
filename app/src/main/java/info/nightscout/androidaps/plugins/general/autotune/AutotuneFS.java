package info.nightscout.androidaps.plugins.general.autotune;

import org.json.JSONException;

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

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile;
import info.nightscout.androidaps.plugins.general.autotune.data.PreppedGlucose;
import info.nightscout.androidaps.plugins.general.maintenance.LoggerUtils;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

//Todo replace by injection and manage autotune file generation here
//@Singleton
public class AutotuneFS {
    final String logDirectory = LoggerUtils.getLogDirectory();
    static final int BUFFER_SIZE = 2048;
    final String AUTOTUNEFOLDER = "autotune";
    final String SETTINGSFOLDER = "settings";
    final String RECOMMENDATIONS = "autotune_recommendations.log";
    final String ENTRIES_PREF = "aaps-entries.";
    final String TREATMENTS_PREF = "aaps-treatments.";
    final String PREPPED_PREF = "aaps-autotune.";
    final String SETTINGS = "settings.json";
    final String PROFIL = "profil";
    final String PUMPPROFILE = "pumpprofile.json";
    final String TUNEDPROFILE = "newaapsprofile.";
    final String LOG_PREF = "autotune.";
    final String ZIP_PREF = "autotune_";

    File autotune_path;
    File autotune_settings;

    @Inject AutotunePlugin autotunePlugin;
    @Inject SP sp;
    @Inject DateUtil dateUtil;
    @Inject ResourceHelper resourceHelper;
    private final HasAndroidInjector injector;

    @Inject
    public AutotuneFS(
            HasAndroidInjector injector
    ) {
        this.injector=injector;
        this.injector.androidInjector().inject(this);
    }

    /*****************************************************************************
     * Create autotune folder for all files created during an autotune session
     ****************************************************************************/
    public void createAutotuneFolder() {
        //create autotune subfolder for autotune files if not exists
        autotune_path = new File(logDirectory, AUTOTUNEFOLDER);
        if (! (autotune_path.exists() && autotune_path.isDirectory())) {
            autotune_path.mkdir();
            log("Create " + AUTOTUNEFOLDER + " subfolder in " + logDirectory);
        }
        autotune_settings = new File(logDirectory, SETTINGSFOLDER);
        if (! (autotune_settings.exists() && autotune_settings.isDirectory())) {
            autotune_settings.mkdir();
            log("Create " + SETTINGSFOLDER + " subfolder in " + logDirectory);
        }
    }

    /*****************************************************************************
     * between each run of autotune, clean autotune folder content
     ****************************************************************************/
    public void deleteAutotuneFiles() {
        for (File file : autotune_path.listFiles()) {
            if(file.isFile())
                file.delete();
        }
        for (File file : autotune_settings.listFiles()) {
            if(file.isFile())
                file.delete();
        }
        log("Delete previous Autotune files");
    }

    /*****************************************************************************
     * Create a JSON autotune files or settings files
     *****************************************************************************/
    public void exportSettings(String settings) {
        createAutotunefile(SETTINGS,settings,true);
    }

    public void exportPumpProfile(ATProfile profile) {
        createAutotunefile(PUMPPROFILE, profile.profiletoOrefJSON(),true);
        createAutotunefile(PUMPPROFILE, profile.profiletoOrefJSON());
    }

    public void exportTunedProfile(ATProfile tunedProfile) {
        createAutotunefile(TUNEDPROFILE + formatDate(new Date(tunedProfile.from)) + ".json", tunedProfile.profiletoOrefJSON());
        try {
            createAutotunefile(resourceHelper.gs(R.string.autotune_tunedprofile_name) + ".json", tunedProfile.getData().toString(2).replace("\\/", "/"), true);
        } catch (JSONException e) {}
    }

    public void exportEntries(AutotuneIob autotuneIob) {
        try {
            createAutotunefile(ENTRIES_PREF + formatDate(new Date(autotuneIob.startBG)) + ".json", autotuneIob.glucosetoJSON().toString(2).replace("\\/", "/"));
        } catch (JSONException e) {}
    }

    public void exportTreatments(AutotuneIob autotuneIob) {
        try {
            createAutotunefile(TREATMENTS_PREF + formatDate(new Date(autotuneIob.startBG)) + ".json", autotuneIob.nsHistorytoJSON().toString(2).replace("\\/", "/"));
        } catch (JSONException e) {}
    }

    public void exportPreppedGlucose(PreppedGlucose preppedGlucose) {
        createAutotunefile(PREPPED_PREF + formatDate(new Date(preppedGlucose.from)) + ".json", preppedGlucose.toString(2));
    }

    public void exportResult(String result) {
        createAutotunefile(RECOMMENDATIONS,result);
    }

    public void exportLogAndZip(Date lastRun, String logString) {
        log("Create " + LOG_PREF + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".log" + " file in " + AUTOTUNEFOLDER + " folder");
        zipAutotune(lastRun);
        createAutotunefile(LOG_PREF + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".log", logString);
    }

    private void createAutotunefile(String fileName, String stringFile) {
        createAutotunefile(fileName, stringFile, false);
    }

    private void createAutotunefile(String fileName, String stringFile, boolean isSettingFile) {
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
                log("Create " + fileName + " file in " + (isSettingFile ? SETTINGSFOLDER : AUTOTUNEFOLDER) + " folder" );
            } catch (FileNotFoundException e) {
                //log.error("Unhandled exception", e);
            } catch (IOException e) {
                //log.error("Unhandled exception", e);
            }
        }
    }

    private String profilName(Date daterun) {
        String strdate = "";
        String prefixe = "aaps-";
        if (daterun != null) {
            prefixe = "aaps-new";
            strdate= "." + formatDate(daterun);
        }
        return prefixe + PROFIL + strdate + ".json";
    }

    /**********************************************************************************
     * create a zip file with all autotune files and settings in autotune folder at the end of run
     *********************************************************************************/

    public void zipAutotune(Date lastRun) {
        if (lastRun!=null) {
            try {
                String zipFileName = ZIP_PREF + DateUtil.toISOString(lastRun, "yyyy-MM-dd_HH-mm-ss", null) + ".zip";
                File zipFile = new File(logDirectory, zipFileName);
                ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
                if (autotune_path != null)
                    zipDirectory(autotune_path, autotune_path.getName(), out);
                if (autotune_settings != null)
                    zipDirectory(autotune_settings, autotune_settings.getName(), out);
                out.flush();
                out.close();
                log("Create " + zipFileName + " file in " + logDirectory + " folder" );
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

    private void log(String message) {
        autotunePlugin.atLog("[FS] " + message);
    }
}

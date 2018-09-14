package info.nightscout.utils;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;

import static android.content.Context.CONNECTIVITY_SERVICE;

public class VersionChecker {
    private static Logger log = LoggerFactory.getLogger(L.CORE);

    public static void check() {
        if (isConnected())
            new Thread(() -> {
                HttpClient client = new DefaultHttpClient();
                HttpGet request = new HttpGet("https://raw.githubusercontent.com/MilosKozak/AndroidAPS/master/app/build.gradle");
                HttpResponse response;

                try {
                    response = client.execute(request);
                    InputStream inputStream = response.getEntity().getContent();

                    if (inputStream != null) {
                        String result = findLine(inputStream);
                        if (result != null) {
                            result = result.replace("version", "").replace("\"", "").replace("\\s+", "").trim();
                            int compare = result.compareTo(BuildConfig.VERSION_NAME.replace("\"", ""));
                            if (compare == 0) {
                                log.debug("Version equal to master");
                                return;
                            } else if (compare > 0) {
                                log.debug("Version outdated. Found " + result);
                                Notification notification = new Notification(Notification.NEWVERSIONDETECTED, String.format(MainApp.gs(R.string.versionavailable), result), Notification.LOW);
                                MainApp.bus().post(new EventNewNotification(notification));
                                return;
                            } else {
                                log.debug("Version newer than master. Are you developer?");
                                return;
                            }
                        }
                    }

                    log.debug("Github master version not found");

                } catch (IOException e) {
                    e.printStackTrace();
                    log.debug("Github master version check error");
                }
            }).start();
        else
            log.debug("Github master version no checked. No connectivity");
    }

    // convert inputstream to String
    private static String findLine(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        String regex = "(.*)version(.*)\"(\\d+)\\.(\\d+)\"(.*)";
        Pattern p = Pattern.compile(regex);

        while ((line = bufferedReader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                log.debug("+++ " + line);
                return line;
            } else {
                log.debug("--- " + line);
            }
        }
        inputStream.close();
        return null;
    }

    // check network connection
    public static boolean isConnected() {
        ConnectivityManager connMgr = (ConnectivityManager) MainApp.instance().getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

}

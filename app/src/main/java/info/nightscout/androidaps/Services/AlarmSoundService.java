package info.nightscout.androidaps.Services;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.IBinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class AlarmSoundService extends Service {
    private static Logger log = LoggerFactory.getLogger(AlarmSoundService.class);

    MediaPlayer player;
    int resourceId = R.raw.error;

    public AlarmSoundService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        log.debug("onCreate");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (player != null && player.isPlaying())
            player.stop();
        log.debug("onStartCommand");
        if (intent != null && intent.hasExtra("soundid"))
            resourceId = intent.getIntExtra("soundid", R.raw.error);

        player = new MediaPlayer();
        AssetFileDescriptor afd = MainApp.sResources.openRawResourceFd(resourceId);
        if (afd == null)
            return START_STICKY;
        try {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        } catch (IOException e) {
            log.error("Unhandled exception", e);
        }
        player.setLooping(true); // Set looping
        player.setVolume(100, 100);

        try {
            player.prepare();
            player.start();
        } catch (IOException e) {
            log.error("Unhandled exception", e);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        player.stop();
        player.release();
    }
}

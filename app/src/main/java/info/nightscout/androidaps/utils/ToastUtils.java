package info.nightscout.androidaps.utils;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;


public class ToastUtils {
    public static void showToastInUiThread(final Context ctx,
                                           final String string) {

        Handler mainThread = new Handler(Looper.getMainLooper());
        mainThread.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, string, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void showToastInUiThread(final Context ctx,
                                           final String string, int soundID) {

        showToastInUiThread(ctx, string);
        playSound(ctx, soundID);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Notification notification = new Notification(Notification.TOAST_ALARM, string, Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notification));
            }
        }).start();
    }

    private static void playSound(final Context ctx, final int soundID) {
        final MediaPlayer soundMP = MediaPlayer.create(ctx, soundID);
        soundMP.start();
        soundMP.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
            }
        });
    }
}
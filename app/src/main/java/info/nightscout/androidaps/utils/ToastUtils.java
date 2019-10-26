package info.nightscout.androidaps.utils;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;


public class ToastUtils {
    public static void showToastInUiThread(final Context ctx, final int stringId) {
        showToastInUiThread(ctx, MainApp.gs(stringId));
    }

    public static void showToastInUiThread(final Context ctx, final String string) {
        Handler mainThread = new Handler(Looper.getMainLooper());
        mainThread.post(() -> Toast.makeText(ctx, string, Toast.LENGTH_SHORT).show());
    }

    public static void showToastInUiThread(final Context ctx,
                                           final String string, int soundID) {

        showToastInUiThread(ctx, string);
        playSound(ctx, soundID);
        Notification notification = new Notification(Notification.TOAST_ALARM, string, Notification.URGENT);
        RxBus.INSTANCE.send(new EventNewNotification(notification));
    }

    private static void playSound(final Context ctx, final int soundID) {
        final MediaPlayer soundMP = MediaPlayer.create(ctx, soundID);
        soundMP.start();
        soundMP.setOnCompletionListener(MediaPlayer::release);
    }
}
package info.nightscout.utils;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;


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
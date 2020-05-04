package info.nightscout.androidaps.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.appcompat.view.ContextThemeWrapper;

import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;


public class ToastUtils {

    public static class Long {

        public static void warnToast(final Context ctx, final String string) {
            graphicalToast(ctx, string, R.drawable.ic_toast_warn, false);
        }

        public static void infoToast(final Context ctx, final String string) {
            graphicalToast(ctx, string, R.drawable.ic_toast_info, false);
        }

        public static void okToast(final Context ctx, final String string) {
            graphicalToast(ctx, string, R.drawable.ic_toast_check, false);
        }

        public static void errorToast(final Context ctx, final String string) {
            graphicalToast(ctx, string, R.drawable.ic_toast_error, false);
        }
    }

    public static void showToastInUiThread(final Context ctx, final int stringId) {
        showToastInUiThread(ctx, ctx.getString(stringId));
    }

    public static void warnToast(final Context ctx, final String string) {
        graphicalToast(ctx, string, R.drawable.ic_toast_warn, true);
    }

    public static void infoToast(final Context ctx, final String string) {
        graphicalToast(ctx, string, R.drawable.ic_toast_info, true);
    }

    public static void okToast(final Context ctx, final String string) {
        graphicalToast(ctx, string, R.drawable.ic_toast_check, true);
    }

    public static void errorToast(final Context ctx, final String string) {
        graphicalToast(ctx, string, R.drawable.ic_toast_error, true);
    }

    public static void graphicalToast(final Context ctx, final String string, @DrawableRes int iconId) {
        graphicalToast(ctx, string, iconId, true);
    }

    @SuppressLint("InflateParams")
    public static void graphicalToast(final Context ctx, final String string, @DrawableRes int iconId, boolean isShort) {
        Handler mainThread = new Handler(Looper.getMainLooper());
        mainThread.post(() -> {
            View toastRoot = LayoutInflater.from(new ContextThemeWrapper(ctx, R.style.AppTheme)).inflate(R.layout.toast, null);
            TextView toastMessage = toastRoot.findViewById(android.R.id.message);
            toastMessage.setText(string);

            ImageView toastIcon = toastRoot.findViewById(android.R.id.icon);
            toastIcon.setImageResource(iconId);

            Toast toast = new Toast(ctx);
            toast.setDuration(isShort ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
            toast.setView(toastRoot);
            toast.show();
        });
    }

    public static void showToastInUiThread(final Context ctx, final String string) {
        Handler mainThread = new Handler(Looper.getMainLooper());
        mainThread.post(() -> Toast.makeText(ctx, string, Toast.LENGTH_SHORT).show());
    }

    public static void showToastInUiThread(final Context ctx, final RxBusWrapper rxBus,
                                           final String string, int soundID) {

        showToastInUiThread(ctx, string);
        playSound(ctx, soundID);
        Notification notification = new Notification(Notification.TOAST_ALARM, string, Notification.URGENT);
        rxBus.send(new EventNewNotification(notification));
    }

    private static void playSound(final Context ctx, final int soundID) {
        final MediaPlayer soundMP = MediaPlayer.create(ctx, soundID);
        soundMP.start();
        soundMP.setOnCompletionListener(MediaPlayer::release);
    }
}
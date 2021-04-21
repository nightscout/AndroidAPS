package info.nightscout.androidaps.plugins.pump.common.hw.rileylink;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Platform {
    private static final Platform PLATFORM = findPlatform();
    Handler mainhandler = new Handler(Looper.getMainLooper());

    public static Platform get() {
        return PLATFORM;
    }


    private static Platform findPlatform() {
        try {
            Class.forName("android.os.Build");
            if (Build.VERSION.SDK_INT != 0) {
                return new Android();
            }
        } catch (ClassNotFoundException ignored) {
        }
        return new Platform();
    }

    public Executor defaultCallbackExecutor() {
        return Executors.newCachedThreadPool();
    }

    public void postDelayedMain(Runnable r, long t) {
        mainhandler.postDelayed(r, t);
    }

    public void removeCallbacks(Runnable r) {
        mainhandler.removeCallbacks(r);
    }

    public void execute(Runnable runnable) {
        defaultCallbackExecutor().execute(runnable);
    }


    static class Android extends Platform {
        @Override
        public Executor defaultCallbackExecutor() {
            return new MainThreadExecutor();
        }

        static class MainThreadExecutor implements Executor {
            private final Handler handler = new Handler(Looper.getMainLooper());

            @Override
            public void execute(Runnable r) {
                handler.post(r);
            }

            public void execute(Runnable r, long t) {
                handler.postDelayed(r, t);
            }
        }
    }
}

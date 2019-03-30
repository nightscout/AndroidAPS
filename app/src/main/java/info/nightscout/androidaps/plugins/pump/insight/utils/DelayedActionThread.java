package info.nightscout.androidaps.plugins.pump.insight.utils;

public class DelayedActionThread extends Thread {

    private long duration;
    private Runnable runnable;

    private DelayedActionThread(String name, long duration, Runnable runnable) {
        setName(name);
        this.duration = duration;
        this.runnable = runnable;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(duration);
            runnable.run();
        } catch (InterruptedException e) {
        }
    }

    public static DelayedActionThread runDelayed(String name, long duration, Runnable runnable) {
        DelayedActionThread delayedActionThread = new DelayedActionThread(name, duration, runnable);
        delayedActionThread.start();
        return delayedActionThread;
    }
}

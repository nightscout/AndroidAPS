package com.gxwtech.roundtrip2.RoundtripService.Tasks;

import android.util.Log;

import com.gxwtech.roundtrip2.RoundtripService.RoundtripService;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by geoff on 7/9/16.
 */
public class ServiceTaskExecutor extends ThreadPoolExecutor {
    private static final String TAG = "ServiceTaskExecutor";
    private static ServiceTaskExecutor instance;
    private static LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    static {
        instance = new ServiceTaskExecutor();
    }
    private ServiceTaskExecutor() {
        super(1,1,10000, TimeUnit.MILLISECONDS,taskQueue);
    }
    public static ServiceTask startTask(ServiceTask task) {
        instance.execute(task); // task will be run on async thread from pool.
        return task;
    }
    protected void beforeExecute(Thread t, Runnable r) {
        // This is run on either caller UI thread or Service UI thread.
        ServiceTask task = (ServiceTask) r;
        Log.v(TAG,"About to run task " + task.getClass().getSimpleName());
        RoundtripService.getInstance().setCurrentTask(task);
        task.preOp();
    }
    protected void afterExecute(Runnable r, Throwable t) {
        // This is run on either caller UI thread or Service UI thread.
        ServiceTask task = (ServiceTask) r;
        task.postOp();
        Log.v(TAG,"Finishing task " + task.getClass().getSimpleName());
        RoundtripService.getInstance().finishCurrentTask(task);
    }
}

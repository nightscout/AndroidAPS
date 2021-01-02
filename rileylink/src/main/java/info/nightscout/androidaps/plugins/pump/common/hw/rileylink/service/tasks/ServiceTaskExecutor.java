package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;

/**
 * Created by geoff on 7/9/16.
 */
@Singleton
public class ServiceTaskExecutor extends ThreadPoolExecutor {

    @Inject RileyLinkUtil rileyLinkUtil;
    @Inject AAPSLogger aapsLogger;

    private static final LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    @Inject
    public ServiceTaskExecutor() {
        super(1, 1, 10000, TimeUnit.MILLISECONDS, taskQueue);
    }

    public ServiceTask startTask(ServiceTask task) {
        execute(task); // task will be run on async thread from pool.
        return task;
    }

    // FIXME
    @Override protected void beforeExecute(Thread t, Runnable r) {
        // This is run on either caller UI thread or Service UI thread.
        ServiceTask task = (ServiceTask) r;
        aapsLogger.debug(LTag.PUMPBTCOMM, "About to run task " + task.getClass().getSimpleName());
        rileyLinkUtil.setCurrentTask(task);
        task.preOp();
    }


    // FIXME
    @Override protected void afterExecute(Runnable r, Throwable t) {
        // This is run on either caller UI thread or Service UI thread.
        ServiceTask task = (ServiceTask) r;
        task.postOp();
        aapsLogger.debug(LTag.PUMPBTCOMM, "Finishing task " + task.getClass().getSimpleName());
        rileyLinkUtil.finishCurrentTask(task);
    }
}

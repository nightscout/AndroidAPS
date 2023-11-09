package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.HashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.Patch;
import info.nightscout.androidaps.plugins.pump.eopatch.core.exception.NoActivatedPatchException;
import info.nightscout.androidaps.plugins.pump.eopatch.core.exception.PatchDisconnectedException;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BaseResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.BleConnectionState;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.IBleDevice;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;

@Singleton
public class TaskBase {
    protected IBleDevice patch;

    @Inject AAPSLogger aapsLogger;
    @Inject protected IPreferenceManager pm;
    @Inject TaskQueue taskQueue;

    TaskFunc func;

    static HashMap<TaskFunc, TaskBase> maps = new HashMap<>();

    /* enqueue 시 사용 */
    protected Disposable disposable;

    protected final Object lock = new Object();

    protected static final long TASK_ENQUEUE_TIME_OUT = 60; // SECONDS

    @Inject
    public TaskBase(TaskFunc func) {
        this.func = func;
        maps.put(func, this);
        patch = Patch.getInstance();
    }

    /* Task 들의 작업 순서 및 조건 체크 */
    protected Observable<TaskFunc> isReady() {
        return taskQueue.isReady(func).doOnNext(v -> preCondition());
    }

    protected Observable<TaskFunc> isReady2() {
        return taskQueue.isReady2(func).doOnNext(v -> preCondition());
    }

    protected void checkResponse(BaseResponse response) throws Exception {
        if (!response.isSuccess()) {
            throw new Exception("Response failed! - " + response.resultCode.name());
        }
    }

    public static void enqueue(TaskFunc func) {
        TaskBase task = maps.get(func);

        if (task != null) {
            task.enqueue();
        }
    }

    public static void enqueue(TaskFunc func, Boolean flag) {
        TaskBase task = maps.get(func);

        if (task != null) {
            task.enqueue(flag);
        }
    }

    protected synchronized void enqueue() {
    }

    protected synchronized void enqueue(Boolean flag) {
    }

    protected void preCondition() throws Exception {

    }

    protected void checkPatchConnected() throws Exception {
        if (patch.getConnectionState() == BleConnectionState.DISCONNECTED) {
            throw new PatchDisconnectedException();
        }
    }

    protected void checkPatchActivated() throws Exception {
        if (pm.getPatchConfig().isDeactivated()) {
            throw new NoActivatedPatchException();
        }
    }
}

package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.rx.AapsSchedulers;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

@Singleton
public class TaskQueue {
    @Inject AAPSLogger aapsLogger;
    @Inject AapsSchedulers aapsSchedulers;

    Queue<PatchTask> queue = new LinkedList<>();

    private int sequence = 0;
    private final BehaviorSubject<PatchTask> ticketSubject = BehaviorSubject.create();
    private final BehaviorSubject<Integer> sizeSubject = BehaviorSubject.createDefault(0);

    @Inject
    public TaskQueue() {
    }

    protected Observable<Integer> observeQueue() {
        return sizeSubject.distinctUntilChanged();
    }

    protected synchronized Observable<TaskFunc> isReady(final TaskFunc function) {
        return Observable.fromCallable(() -> publishTicket(function))
                .concatMap(v -> ticketSubject
                        .takeUntil(it -> it.number > v)
                        .filter(it -> it.number == v))
                .doOnNext(v -> aapsLogger.debug(LTag.PUMPCOMM, String.format("Task #:%s started     func:%s", v.number, v.func.name())))
                .observeOn(aapsSchedulers.getIo())
                .map(it -> it.func)
                .doFinally(this::done);
    }

    protected synchronized Observable<TaskFunc> isReady2(final TaskFunc function) {
        return observeQueue()
                .filter(size -> size == 0).concatMap(v -> isReady(function));
    }

    private synchronized int publishTicket(final TaskFunc function) {
        int turn = sequence++;
        aapsLogger.debug(LTag.PUMPCOMM, String.format("publishTicket() Task #:%s is assigned func:%s", turn, function.name()));

        PatchTask task = new PatchTask(turn, function);
        addQueue(task);
        return turn;
    }

    private synchronized void addQueue(PatchTask task) {
        queue.add(task);
        int size = queue.size();
        sizeSubject.onNext(size);

        if (size == 1) {
            ticketSubject.onNext(task);
        }
    }

    private synchronized void done() {
        if (queue.size() > 0) {
            PatchTask done = queue.remove();
            aapsLogger.debug(LTag.PUMPCOMM, String.format("done() Task #:%s completed   func:%s  task remaining:%s",
                    done.number, done.func.name(), queue.size()));
        }

        int size = queue.size();
        sizeSubject.onNext(size);

        PatchTask next = queue.peek();
        if (next != null) {
            ticketSubject.onNext(next);
        }
    }

    public synchronized boolean has(TaskFunc func) {
        if (queue.size() > 1) {
            Iterator<PatchTask> iterator = queue.iterator();

            /* remove 1st queue */
            iterator.next();

            while (iterator.hasNext()) {
                PatchTask item = iterator.next();
                if (item.func == func) {
                    return true;
                }
            }
        }

        return false;
    }

    static class PatchTask {

        int number;
        TaskFunc func;

        PatchTask(int number, TaskFunc func) {
            this.number = number;
            this.func = func;
        }
    }
}
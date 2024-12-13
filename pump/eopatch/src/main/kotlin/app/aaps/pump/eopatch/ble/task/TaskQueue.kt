package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.eopatch.ble.task.TaskQueue.PatchTask
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Callable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskQueue @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers
) {

    private var queue: Queue<PatchTask> = LinkedList<PatchTask>()

    private var sequence = 0
    private val ticketSubject = BehaviorSubject.create<PatchTask>()
    private val sizeSubject = BehaviorSubject.createDefault<Int>(0)

    fun observeQueue(): Observable<Int> {
        return sizeSubject.distinctUntilChanged()
    }

    @Synchronized fun isReady(function: TaskFunc): Observable<TaskFunc> {
        return Observable.fromCallable<Int>(Callable { publishTicket(function) })
            .concatMap<PatchTask>(Function { v: Int ->
                ticketSubject
                    .takeUntil(Predicate { it: PatchTask -> it.number > v })
                    .filter(Predicate { it: PatchTask -> it.number == v })
            })
            .doOnNext(Consumer { v: PatchTask -> aapsLogger.debug(LTag.PUMPCOMM, String.format("Task #:%s started     func:%s", v.number, v.func.name)) })
            .observeOn(aapsSchedulers.io)
            .map<TaskFunc>(Function { it: PatchTask -> it.func })
            .doFinally(Action { this.done() })
    }

    @Synchronized fun isReady2(function: TaskFunc): Observable<TaskFunc> {
        return observeQueue()
            .filter(Predicate { size: Int -> size == 0 }).concatMap<TaskFunc>(Function { isReady(function) })
    }

    @Synchronized private fun publishTicket(function: TaskFunc): Int {
        val turn = sequence++
        aapsLogger.debug(LTag.PUMPCOMM, String.format("publishTicket() Task #:%s is assigned func:%s", turn, function.name))

        val task = PatchTask(turn, function)
        addQueue(task)
        return turn
    }

    @Synchronized private fun addQueue(task: PatchTask) {
        queue.add(task)
        val size = queue.size
        sizeSubject.onNext(size)

        if (size == 1) {
            ticketSubject.onNext(task)
        }
    }

    @Synchronized private fun done() {
        if (queue.isNotEmpty()) {
            val done = queue.remove()
            aapsLogger.debug(
                LTag.PUMPCOMM, String.format(
                    "done() Task #:%s completed   func:%s  task remaining:%s",
                    done.number, done.func.name, queue.size
                )
            )
        }

        val size = queue.size
        sizeSubject.onNext(size)

        val next = queue.peek()
        if (next != null) {
            ticketSubject.onNext(next)
        }
    }

    internal class PatchTask(var number: Int, var func: TaskFunc)
}
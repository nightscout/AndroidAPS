package app.aaps.pump.eopatch.core.ble

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.LinkedList

class APIQueue : IAPIQueue {

    private val queue = LinkedList<APITask>()
    private var sequence = 0
    private val ticketSubject = BehaviorSubject.create<APITask>()

    @Synchronized private fun isReady(function: PatchFunc): Observable<APITask> =
        Observable.fromCallable { publishTicket(function) }
            .concatMap { v ->
                ticketSubject
                    .takeUntil { it.number > v }
                    .filter { it.number == v }
            }
            .observeOn(Schedulers.io())
            .doFinally { done() }

    @Synchronized private fun publishTicket(function: PatchFunc): Int {
        val turn = sequence++
        addQueue(APITask(turn, function))
        return turn
    }

    @Synchronized private fun addQueue(task: APITask) {
        queue.add(task)
        if (queue.size == 1) ticketSubject.onNext(task)
    }

    @Synchronized private fun done() {
        if (queue.isNotEmpty()) queue.remove()
        queue.peek()?.let { ticketSubject.onNext(it) }
    }

    override fun getTurn(function: PatchFunc): Observable<PatchFunc> =
        isReady(function).map { it.func }

    private data class APITask(val number: Int, val func: PatchFunc)
}

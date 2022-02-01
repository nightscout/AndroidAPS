package info.nightscout.androidaps.utils.rx

import io.reactivex.Observable
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.TestScheduler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RxExtensionsTest {

    private val testScheduler = TestScheduler()

    @get:Rule
    val schedulerRule = RxSchedulerRule(testScheduler)

    @Test
    fun `fail after 4 retries`() {
        val atomicInteger = AtomicInteger()
        val testObservable: TestObserver<Int> = succeedOnObservable(atomicInteger, 5)
            .retryWithBackoff(4, 1, TimeUnit.SECONDS)
            .test()
        assertEquals(1, atomicInteger.get()) // 1st failure
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(3, TimeUnit.SECONDS)  // 2nd, 3rd, 4th failure
        assertEquals(4, atomicInteger.get())
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)  // 5th failure on 4th retry
        assertEquals(5, atomicInteger.get())
        testObservable.assertError(RuntimeException::class.java)
        testObservable.assertNever(1)
    }

    @Test
    fun `succeed after 4 retries`() {
        val atomicInteger = AtomicInteger()
        val testObservable: TestObserver<Int> = succeedOnObservable(atomicInteger, 4)
            .retryWithBackoff(4, 1, TimeUnit.SECONDS)
            .test()
        assertEquals(1, atomicInteger.get()) // 1st failure
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(3, TimeUnit.SECONDS)  // 2nd, 3rd, 4th failure
        assertEquals(4, atomicInteger.get())
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS)  // 5th is a charm
        assertEquals(5, atomicInteger.get())
        testObservable.assertValue(1)
    }

    @Test
    fun `succeed after 4 retries with delay factor`() {
        val atomicInteger = AtomicInteger()
        val testObservable: TestObserver<Int> = succeedOnObservable(atomicInteger, 4)
            .retryWithBackoff(4, 1, TimeUnit.SECONDS, delayFactor = 1.2)
            .test()
        assertEquals(1, atomicInteger.get()) // 1st failure
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(999, TimeUnit.MILLISECONDS)
        assertEquals(1, atomicInteger.get())
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS) //1st retry after 1 second
        assertEquals(2, atomicInteger.get())
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(1199, TimeUnit.MILLISECONDS)
        assertEquals(2, atomicInteger.get())
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS) //2nd retry after 1.2 seconds more
        assertEquals(3, atomicInteger.get())
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(1439, TimeUnit.MILLISECONDS)
        assertEquals(3, atomicInteger.get())
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS) //3rd retry after 1.44 seconds more
        assertEquals(4, atomicInteger.get())
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        testScheduler.advanceTimeBy(1726, TimeUnit.MILLISECONDS)
        assertEquals(4, atomicInteger.get())
        testObservable.assertNotComplete()
        testObservable.assertNotTerminated()
        testObservable.assertNever(1)

        //4th retry = 5th try is a charm after 1.728 seconds more - rounding error by 1 millisecond!!
        testScheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS)
        assertEquals(5, atomicInteger.get())
        testObservable.assertValue(1)
    }

    private fun succeedOnObservable(atomicInteger: AtomicInteger, initialFailures: Int): Observable<Int> =
        Observable.defer {
            if (atomicInteger.incrementAndGet() == initialFailures + 1) {
                Observable.just(1)
            } else {
                Observable.error(RuntimeException())
            }
        }

}
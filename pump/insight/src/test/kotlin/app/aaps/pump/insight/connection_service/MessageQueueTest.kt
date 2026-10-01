package app.aaps.pump.insight.connection_service

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.descriptors.MessagePriority
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Covers [MessageQueue]: the order pump commands are sent in, and how waiting callers are released.
 *
 * Two orderings matter and both are pinned below. The urgent message must go first - a bolus
 * cancellation is HIGHEST and used to be sent last, because the queue sorted ascending over a
 * [MessagePriority] declared NORMAL, HIGHER, HIGHEST while `nextRequest` takes index 0. And messages
 * of equal priority must keep the order they were added in, which the open/write/close
 * configuration batch depends on.
 */
class MessageQueueTest {

    private class TestMessage(priority: MessagePriority) : AppLayerMessage(priority, false, false, null)

    private fun request(priority: MessagePriority = MessagePriority.NORMAL) =
        MessageRequest(TestMessage(priority))

    @Test
    fun aNewQueueIsEmpty() {
        val sut = MessageQueue()

        assertThat(sut.hasPendingMessages()).isFalse()
        assertThat(sut.activeRequest).isNull()
    }

    @Test
    fun enqueueRequest_makesTheQueuePending() {
        val sut = MessageQueue()

        sut.enqueueRequest(request())

        assertThat(sut.hasPendingMessages()).isTrue()
    }

    /**
     * The driver enqueues "open write session, write block, close write session" as one batch of
     * three equal-priority messages. If the sort did not keep them in that order the pump would be
     * asked to write outside an open session.
     */
    @Test
    fun equalPrioritiesKeepTheOrderTheyWereAddedIn() {
        val sut = MessageQueue()
        val open = request()
        val write = request()
        val close = request()

        sut.enqueueRequest(open)
        sut.enqueueRequest(write)
        sut.enqueueRequest(close)

        sut.nextRequest()
        assertThat(sut.activeRequest).isSameInstanceAs(open)
        sut.activeRequest = null
        sut.nextRequest()
        assertThat(sut.activeRequest).isSameInstanceAs(write)
        sut.activeRequest = null
        sut.nextRequest()
        assertThat(sut.activeRequest).isSameInstanceAs(close)
    }

    /**
     * A bolus cancellation is HIGHEST. If it queues behind routine reads the pump keeps delivering
     * for the length of those round trips, which is the whole reason the priority exists.
     */
    @Test
    fun theMostUrgentRequestIsSentFirst() {
        val sut = MessageQueue()
        val routine = request(MessagePriority.NORMAL)
        val urgent = request(MessagePriority.HIGHEST)
        val middle = request(MessagePriority.HIGHER)

        sut.enqueueRequest(routine)
        sut.enqueueRequest(urgent)
        sut.enqueueRequest(middle)

        sut.nextRequest()
        assertThat(sut.activeRequest).isSameInstanceAs(urgent)
        sut.activeRequest = null
        sut.nextRequest()
        assertThat(sut.activeRequest).isSameInstanceAs(middle)
        sut.activeRequest = null
        sut.nextRequest()
        assertThat(sut.activeRequest).isSameInstanceAs(routine)
    }

    /** An urgent message arriving while routine ones are already waiting still overtakes them. */
    @Test
    fun anUrgentRequestOvertakesTheOnesAlreadyWaiting() {
        val sut = MessageQueue()
        sut.enqueueRequest(request(MessagePriority.NORMAL))
        sut.enqueueRequest(request(MessagePriority.NORMAL))
        val urgent = request(MessagePriority.HIGHEST)

        sut.enqueueRequest(urgent)
        sut.nextRequest()

        assertThat(sut.activeRequest).isSameInstanceAs(urgent)
    }

    @Test
    fun nextRequest_takesTheRequestOutOfThePendingList() {
        val sut = MessageQueue()
        sut.enqueueRequest(request())

        sut.nextRequest()

        assertThat(sut.activeRequest).isNotNull()
        assertThat(sut.hasPendingMessages()).isFalse()
    }

    @Test
    fun nextRequest_onAnEmptyQueueLeavesNoActiveRequest() {
        val sut = MessageQueue()

        sut.nextRequest()

        assertThat(sut.activeRequest).isNull()
    }

    @Test
    fun completeActiveRequest_handsTheResponseToTheWaitingCallerAndFreesTheQueue() {
        val sut = MessageQueue()
        val pending = request()
        sut.enqueueRequest(pending)
        sut.nextRequest()
        val response = TestMessage(MessagePriority.NORMAL)

        sut.completeActiveRequest(response)

        assertThat(pending.response).isSameInstanceAs(response)
        assertThat(pending.exception).isNull()
        assertThat(sut.activeRequest).isNull()
    }

    @Test
    fun completeActiveRequest_handsTheFailureToTheWaitingCallerAndFreesTheQueue() {
        val sut = MessageQueue()
        val pending = request()
        sut.enqueueRequest(pending)
        sut.nextRequest()
        val failure = IllegalStateException("pump went away")

        sut.completeActiveRequest(failure)

        assertThat(pending.exception).isSameInstanceAs(failure)
        assertThat(pending.response).isNull()
        assertThat(sut.activeRequest).isNull()
    }

    @Test
    fun completeActiveRequest_withNothingActiveDoesNothing() {
        val sut = MessageQueue()

        sut.completeActiveRequest(TestMessage(MessagePriority.NORMAL))
        sut.completeActiveRequest(IllegalStateException("ignored"))

        assertThat(sut.activeRequest).isNull()
    }

    /** On a dropped connection every waiting caller has to be released, or the driver hangs. */
    @Test
    fun completePendingRequests_failsEveryWaitingCallerAndEmptiesTheQueue() {
        val sut = MessageQueue()
        val first = request()
        val second = request()
        sut.enqueueRequest(first)
        sut.enqueueRequest(second)
        val failure = IllegalStateException("connection lost")

        sut.completePendingRequests(failure)

        assertThat(first.exception).isSameInstanceAs(failure)
        assertThat(second.exception).isSameInstanceAs(failure)
        assertThat(sut.hasPendingMessages()).isFalse()
    }

    @Test
    fun reset_dropsTheActiveRequestAndEverythingPending() {
        val sut = MessageQueue()
        sut.enqueueRequest(request())
        sut.nextRequest()
        sut.enqueueRequest(request())

        sut.reset()

        assertThat(sut.activeRequest).isNull()
        assertThat(sut.hasPendingMessages()).isFalse()
    }
}

package app.aaps.implementation.queue

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.SmbPause
import app.aaps.core.interfaces.utils.DateUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class SmbPauseImpl(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil
) : SmbPause {

    private val lock = AapsLock()

    // Pause id -> the time it runs out.
    private val endsAt = mutableMapOf<Long, Long>()
    private var lastId = 0L

    override fun begin(): Long = lock.withLock {
        removeEnded()
        val id = ++lastId
        endsAt[id] = dateUtil.now() + SmbPause.MAX_DURATION.inWholeMilliseconds
        aapsLogger.debug(LTag.PUMPQUEUE, "SMB pause $id started")
        id
    }

    override fun end(id: Long) {
        lock.withLock {
            if (endsAt.remove(id) != null) aapsLogger.debug(LTag.PUMPQUEUE, "SMB pause $id ended")
        }
    }

    override fun handOver(id: Long) {
        lock.withLock {
            if (!isActiveLocked(id)) return
            endsAt[id] = dateUtil.now() + SmbPause.HAND_OVER.inWholeMilliseconds
            aapsLogger.debug(LTag.PUMPQUEUE, "SMB pause $id handed over to the queue")
        }
    }

    override fun isActive(id: Long): Boolean = lock.withLock { isActiveLocked(id) }

    override fun isActive(): Boolean = lock.withLock {
        removeEnded()
        endsAt.isNotEmpty()
    }

    private fun isActiveLocked(id: Long): Boolean = (endsAt[id] ?: return false) > dateUtil.now()

    private fun removeEnded() {
        val now = dateUtil.now()
        endsAt.entries.removeAll { (id, end) ->
            (end <= now).also { ranOut -> if (ranOut) aapsLogger.debug(LTag.PUMPQUEUE, "SMB pause $id ran out") }
        }
    }
}

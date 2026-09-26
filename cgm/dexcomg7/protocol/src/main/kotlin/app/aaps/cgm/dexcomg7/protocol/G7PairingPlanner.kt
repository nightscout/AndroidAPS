package app.aaps.cgm.dexcomg7.protocol

/**
 * Decides which sensor to try next during pairing, and when to stop.
 *
 * A pairing code does not identify a sensor over the air, so pairing may have to try several sensors in
 * range. The order matters: a sensor whose phone slot is held by another phone will refuse us, and four
 * refusals in a row make a sensor stop accepting connections for a while. So free sensors go first, and
 * within a group the strongest signal goes first, because the phone is usually held close to the new
 * sensor. A sensor that refuses us is dropped, not retried. Ordinary failures (a dropped link, a
 * timeout) get a few retries before moving on.
 *
 * Only bookkeeping, no Bluetooth, so the rules can be tested on their own.
 */
class G7PairingPlanner(
    /**
     * What to do when every sensor found so far has been tried. With a typed code we cannot tell the
     * right sensor from a neighbour's, so a sensor that does not match the code is only a wrong guess,
     * and we keep scanning. With a scanned barcode the serial already picked the one sensor, so a
     * mismatch means a wrong code, and we stop.
     */
    val keepScanningWhenExhausted: Boolean = false
) {

    data class Candidate(
        /** Bluetooth address. */
        val id: String,
        val name: String,
        val isPhoneSlotHeld: Boolean,
        val rssi: Int
    )

    sealed class Action {

        /** Try the current candidate again. */
        data object RetryCurrent : Action()

        /** Move on to the next candidate. */
        data object AdvanceToNext : Action()

        /** Every sensor found so far has been tried, keep looking for the right one. */
        data object KeepScanning : Action()

        /** Nothing left to try. */
        data class GiveUp(val reason: Reason, val details: List<String>) : Action()
    }

    enum class Reason { NO_SENSOR_FOUND, ALL_CANDIDATES_FAILED }

    private val _candidates = mutableListOf<Candidate>()
    val candidates: List<Candidate> get() = _candidates.toList()

    var currentIndex = 0
        private set
    private var attemptsOnCurrent = 0

    /** Why candidates were dropped, for the failure message. */
    private val abandonmentReasons = mutableListOf<String>()

    val currentCandidate: Candidate? get() = _candidates.getOrNull(currentIndex)

    /** The number the next try will have, starting at 1. */
    val nextAttemptNumber: Int get() = attemptsOnCurrent + 1

    /** Adds a newly found sensor. Returns false when it was already known. */
    fun addCandidate(id: String, name: String, isPhoneSlotHeld: Boolean, rssi: Int = UNKNOWN_RSSI): Boolean {
        if (_candidates.any { it.id == id }) return false
        _candidates.add(Candidate(id, name, isPhoneSlotHeld, rssi))
        sortUntriedTail()
        return true
    }

    /**
     * Records a new advertisement from a known candidate. A held slot is freed after ~15 minutes of
     * silence, so a sensor put back earlier can move forward. Null or [UNKNOWN_RSSI] leaves that value.
     */
    fun updateSlot(id: String, isPhoneSlotHeld: Boolean?, rssi: Int = UNKNOWN_RSSI): Boolean {
        val index = _candidates.indexOfFirst { it.id == id }
        if (index < 0) return false
        var candidate = _candidates[index]
        val before = candidate
        if (isPhoneSlotHeld != null) candidate = candidate.copy(isPhoneSlotHeld = isPhoneSlotHeld)
        if (rssi != UNKNOWN_RSSI) candidate = candidate.copy(rssi = rssi)
        if (candidate == before) return false
        _candidates[index] = candidate
        sortUntriedTail()
        return true
    }

    /**
     * Sorts what has not been tried yet: free before held, then strongest signal, then the order they
     * were found in. Never touches the current candidate, which may be in the middle of a handshake.
     */
    private fun sortUntriedTail() {
        val tailStart = currentIndex + 1
        if (tailStart >= _candidates.size) return
        val sorted = _candidates.subList(tailStart, _candidates.size)
            .withIndex()
            .sortedWith(compareBy<IndexedValue<Candidate>> { it.value.isPhoneSlotHeld }.thenByDescending { it.value.rssi }.thenBy { it.index })
            .map { it.value }
        for (i in sorted.indices) _candidates[tailStart + i] = sorted[i]
    }

    /** An ordinary failure: try again, or move on when the attempts are used up. */
    fun recordFailure(): Action {
        if (currentCandidate == null) return exhausted()
        attemptsOnCurrent++
        if (attemptsOnCurrent < ATTEMPTS_PER_CANDIDATE) return Action.RetryCurrent
        return advance()
    }

    /** The current candidate cannot succeed (it refused us, or it is not this code's sensor). */
    fun abandonCurrentCandidate(reason: String): Action {
        val candidate = currentCandidate ?: return exhausted()
        abandonmentReasons.add("${candidate.name}: $reason")
        return advance()
    }

    private fun advance(): Action {
        currentIndex++
        attemptsOnCurrent = 0
        return if (currentCandidate != null) Action.AdvanceToNext else exhausted()
    }

    private fun exhausted(): Action =
        if (keepScanningWhenExhausted) Action.KeepScanning
        else Action.GiveUp(if (_candidates.isEmpty()) Reason.NO_SENSOR_FOUND else Reason.ALL_CANDIDATES_FAILED, abandonmentReasons.toList())

    /** Why the run ended with nothing paired, when stopped from outside (the scan time ran out). */
    fun exhaustion(): Action.GiveUp =
        Action.GiveUp(if (_candidates.isEmpty()) Reason.NO_SENSOR_FOUND else Reason.ALL_CANDIDATES_FAILED, abandonmentReasons.toList())

    companion object {

        /** Stand-in for an unknown signal strength. Sorts weakest. */
        const val UNKNOWN_RSSI = Int.MIN_VALUE

        /** Ordinary failures allowed per candidate before moving on. */
        const val ATTEMPTS_PER_CANDIDATE = 3
    }
}

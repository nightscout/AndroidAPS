package info.nightscout.interfaces.constraints

import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag

class Constraint<T : Comparable<T>>(private var value: T) {

    private var originalValue: T
    private val reasons: MutableList<String> = ArrayList()
    private val mostLimiting: MutableList<String> = ArrayList()
    fun value(): T {
        return value
    }

    fun originalValue(): T {
        return originalValue
    }

    fun set(aapsLogger: AAPSLogger, value: T): Constraint<T> {
        this.value = value
        originalValue = value
        aapsLogger.debug(LTag.CONSTRAINTS, "Setting value $value")
        return this
    }

    fun set(aapsLogger: AAPSLogger, value: T, reason: String, from: Any): Constraint<T> {
        aapsLogger.debug(LTag.CONSTRAINTS, "Setting value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]")
        this.value = value
        addReason(reason, from)
        addMostLimingReason(reason, from)
        return this
    }

    fun setIfDifferent(aapsLogger: AAPSLogger, value: T, reason: String, from: Any): Constraint<T> {
        if (this.value != value) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Setting because of different value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]")
            this.value = value
            addReason(reason, from)
            addMostLimingReason(reason, from)
        }
        return this
    }

    fun setIfSmaller(aapsLogger: AAPSLogger, value: T, reason: String, from: Any): Constraint<T> {
        if (value < this.value) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Setting because of smaller value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]")
            this.value = value
            mostLimiting.clear()
            addMostLimingReason(reason, from)
        }
        if (value < originalValue) {
            addReason(reason, from)
        }
        return this
    }

    fun setIfGreater(aapsLogger: AAPSLogger, value: T, reason: String, from: Any): Constraint<T> {
        if (value > this.value) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Setting because of greater value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]")
            this.value = value
            mostLimiting.clear()
            addMostLimingReason(reason, from)
        }
        if (value > originalValue) {
            addReason(reason, from)
        }
        return this
    }

    private fun translateFrom(from: Any): String {
        return from.javaClass.simpleName.replace("Plugin", "")
    }

    fun addReason(reason: String, from: Any) {
        reasons.add(translateFrom(from) + ": " + reason)
    }

    private fun addMostLimingReason(reason: String, from: Any) {
        mostLimiting.add(translateFrom(from) + ": " + reason)
    }

    fun getReasons(aapsLogger: AAPSLogger): String {
        val sb = StringBuilder()
        for ((count, r) in reasons.withIndex()) {
            if (count != 0) sb.append("\n")
            sb.append(r)
        }
        aapsLogger.debug(LTag.CONSTRAINTS, "Limiting original value: $originalValue to $value. Reason: $sb")
        return sb.toString()
    }

    val reasonList: List<String>
        get() = reasons

    fun getMostLimitedReasons(aapsLogger: AAPSLogger): String {
        val sb = StringBuilder()
        for ((count, r) in mostLimiting.withIndex()) {
            if (count != 0) sb.append("\n")
            sb.append(r)
        }
        aapsLogger.debug(LTag.CONSTRAINTS, "Limiting original value: $originalValue to $value. Reason: $sb")
        return sb.toString()
    }

    val mostLimitedReasonList: List<String>
        get() = mostLimiting

    fun copyReasons(another: Constraint<*>) {
        reasons.addAll(another.reasonList)
    }

    init {
        originalValue = value
    }
}
package app.aaps.core.interfaces.constraints

interface Constraint<T : Comparable<T>> {

    fun value(): T
    fun originalValue(): T
    fun set(value: T): Constraint<T>
    fun set(value: T, reason: String, from: Any): Constraint<T>
    fun setIfDifferent(value: T, reason: String, from: Any): Constraint<T>
    fun setIfSmaller(value: T, reason: String, from: Any): Constraint<T>
    fun setIfGreater(value: T, reason: String, from: Any): Constraint<T>
    fun addReason(reason: String, from: Any)
    fun getReasons(): String
    val reasonList: List<String>
    fun getMostLimitedReasons(): String
    val mostLimitedReasonList: List<String>
    fun copyReasons(another: Constraint<*>)
}
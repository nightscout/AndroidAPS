package app.aaps.database

sealed class ValueWrapper<T> {
    data class Existing<T>(val value: T) : ValueWrapper<T>()
    class Absent<T> : ValueWrapper<T>()
}
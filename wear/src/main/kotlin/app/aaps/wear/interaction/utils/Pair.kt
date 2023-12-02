package app.aaps.wear.interaction.utils

import java.util.Objects

/**
 * Same as android Pair, but clean room java class - does not require Android SDK for tests
 */
class Pair<F, S>(val first: F, val second: S) {

    override fun equals(other: Any?): Boolean =
        if (other is Pair<*, *>) other.first == first && other.second == second
        else false

    override fun toString(): String = "First: \"" + first.toString() + "\" Second: \"" + second.toString() + "\""
    override fun hashCode(): Int = Objects.hash(first, second)

    companion object {

        fun <F, S> create(f: F, s: S): Pair<F, S> = Pair(f, s)
    }
}
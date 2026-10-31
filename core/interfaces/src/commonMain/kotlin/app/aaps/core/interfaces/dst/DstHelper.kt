package app.aaps.core.interfaces.dst

/**
 * Checks for impending or recent DST / time changes and reacts (warns the user, suspends the loop
 * around the transition). Implemented by a plugin; consumed by background components such as the
 * keep-alive worker that must not depend on the plugin module directly.
 */
interface DstHelper {

    /** Warns ahead of a DST change and suspends the loop shortly after one for pumps that can't handle DST. */
    fun dstCheck()
}

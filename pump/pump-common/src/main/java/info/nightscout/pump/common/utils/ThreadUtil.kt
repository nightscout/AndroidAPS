package info.nightscout.pump.common.utils

/**
 * Created by geoff on 5/27/16.
 */
object ThreadUtil {

    @JvmStatic fun sig(): String {
        val t = Thread.currentThread()
        return t.name + "[" + t.id + "]"
    }
}
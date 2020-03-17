package info.nightscout.androidaps.plugins.constraints.versionChecker

import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.ConstraintsInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.SP
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Usually we would have a class here.
 * Instead of having a class we can use an object directly inherited from PluginBase.
 * This is a lazy loading singleton only loaded when actually used.
 * */

object VersionCheckerPlugin : PluginBase(PluginDescription()
    .mainType(PluginType.CONSTRAINTS)
    .neverVisible(true)
    .alwaysEnabled(true)
    .showInList(false)
    .pluginName(R.string.versionChecker)), ConstraintsInterface {

    private val gracePeriod: GracePeriod
        get() = if ((BuildConfig.VERSION_NAME.contains("RC", ignoreCase = true))) {
            GracePeriod.RC
        } else {
            GracePeriod.RELEASE
        }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        checkWarning()
        triggerCheckVersion()
        return if (isOldVersion(gracePeriod.veryOld.daysToMillis()))
            value.set(false, MainApp.gs(R.string.very_old_version), this)
        else
            value
    }

    private fun checkWarning() {
        val now = System.currentTimeMillis()

        if (!SP.contains(R.string.key_last_versionchecker_plugin_warning)) {
            SP.putLong(R.string.key_last_versionchecker_plugin_warning, now)
            return
        }


        if (isOldVersion(gracePeriod.warning.daysToMillis()) && shouldWarnAgain(now)) {
            // store last notification time
            SP.putLong(R.string.key_last_versionchecker_plugin_warning, now)

            //notify
            val message = MainApp.gs(R.string.new_version_warning,
                ((now - SP.getLong(R.string.key_last_time_this_version_detected, now)) / 1L.daysToMillis().toDouble()).roundToInt(),
                gracePeriod.old,
                gracePeriod.veryOld
            )
            val notification = Notification(Notification.OLDVERSION, message, Notification.NORMAL)
            RxBus.send(EventNewNotification(notification))
        }
    }

    private fun shouldWarnAgain(now: Long) =
        now > SP.getLong(R.string.key_last_versionchecker_plugin_warning, 0) + WARN_EVERY

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> =
        if (isOldVersion(gracePeriod.old.daysToMillis()))
            maxIob.set(0.toDouble(), MainApp.gs(R.string.old_version), this)
        else
            maxIob

    private fun isOldVersion(gracePeriod: Long): Boolean {
        val now = System.currentTimeMillis()
        return now > SP.getLong(R.string.key_last_time_this_version_detected, 0) + gracePeriod
    }

    private val WARN_EVERY = TimeUnit.DAYS.toMillis(1)

}

enum class GracePeriod(val warning: Long, val old: Long, val veryOld: Long) {
    RELEASE(30, 60, 90),
    RC(1, 7, 14)
}

private fun Long.daysToMillis() = TimeUnit.DAYS.toMillis(this)

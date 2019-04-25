package info.nightscout.androidaps.plugins.general.versionChecker

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.SP
import java.util.concurrent.TimeUnit

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

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        checkWarning()
        triggerCheckVersion()
        return if (isOldVersion(GRACE_PERIOD_VERY_OLD))
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


        if (isOldVersion(GRACE_PERIOD_WARNING) && shouldWarnAgain(now)) {
            // store last notification time
            SP.putLong(R.string.key_last_versionchecker_plugin_warning, now)

            //notify
            val message = MainApp.gs(R.string.new_version_warning, Math.round((now - SP.getLong(R.string.key_last_time_this_version_detected, now)) / TimeUnit.DAYS.toMillis(1).toDouble()))
            val notification = Notification(Notification.OLDVERSION, message, Notification.NORMAL)
            MainApp.bus().post(EventNewNotification(notification))
        }
    }

    private fun shouldWarnAgain(now: Long) =
            now > SP.getLong(R.string.key_last_versionchecker_plugin_warning, 0) + WARN_EVERY

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> =
            if (isOldVersion(GRACE_PERIOD_OLD))
                maxIob.set(0.toDouble(), MainApp.gs(R.string.old_version), this)
            else
                maxIob

    private fun isOldVersion(gracePeriod: Long): Boolean {
        val now = System.currentTimeMillis()
        return      now > SP.getLong(R.string.key_last_time_this_version_detected, 0) + gracePeriod
    }

    val WARN_EVERY = TimeUnit.DAYS.toMillis(1)
    val GRACE_PERIOD_WARNING = TimeUnit.DAYS.toMillis(30)
    val GRACE_PERIOD_OLD = TimeUnit.DAYS.toMillis(60)
    val GRACE_PERIOD_VERY_OLD = TimeUnit.DAYS.toMillis(90)

}

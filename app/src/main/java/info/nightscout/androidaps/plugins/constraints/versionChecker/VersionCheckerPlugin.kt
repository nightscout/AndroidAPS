package info.nightscout.androidaps.plugins.constraints.versionChecker

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.extensions.daysToMillis
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class VersionCheckerPlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val sp: SP,
    rh: ResourceHelper,
    private val versionCheckerUtils: VersionCheckerUtils,
    val rxBus: RxBus,
    aapsLogger: AAPSLogger,
    private val config: Config,
    private val dateUtil: DateUtil
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList(false)
        .pluginName(R.string.versionChecker),
    aapsLogger, rh, injector
), Constraints {

    enum class GracePeriod(val warning: Long, val old: Long, val veryOld: Long) {
        RELEASE(30, 60, 90),
        RC(1, 7, 14)
    }

    private val gracePeriod: GracePeriod
        get() = if ((BuildConfig.VERSION_NAME.contains("RC", ignoreCase = true))) {
            GracePeriod.RC
        } else {
            GracePeriod.RELEASE
        }

    companion object {

        private val WARN_EVERY: Long
            get() = TimeUnit.DAYS.toMillis(1)
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        checkWarning()
        versionCheckerUtils.triggerCheckVersion()
        if (isOldVersion(gracePeriod.veryOld.daysToMillis()))
            value.set(aapsLogger, false, rh.gs(R.string.very_old_version), this)
        val endDate = sp.getLong(rh.gs(R.string.key_app_expiration) + "_" + config.VERSION_NAME, 0)
        if (endDate != 0L && dateUtil.now() > endDate)
            value.set(aapsLogger, false, rh.gs(R.string.application_expired), this)
        return value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> =
        if (isOldVersion(gracePeriod.old.daysToMillis()))
            maxIob.set(aapsLogger, 0.0, rh.gs(R.string.old_version), this)
        else
            maxIob

    private fun checkWarning() {
        val now = dateUtil.now()

        if (!sp.contains(R.string.key_last_versionchecker_plugin_warning)) {
            sp.putLong(R.string.key_last_versionchecker_plugin_warning, now)
            return
        }


        if (isOldVersion(gracePeriod.warning.daysToMillis()) && shouldWarnAgain()) {
            // store last notification time
            sp.putLong(R.string.key_last_versionchecker_plugin_warning, now)

            //notify
            val message = rh.gs(
                R.string.new_version_warning,
                ((now - sp.getLong(R.string.key_last_time_this_version_detected_as_ok, now)) / 1L.daysToMillis().toDouble()).roundToInt(),
                gracePeriod.old,
                gracePeriod.veryOld
            )
            rxBus.send(EventNewNotification(Notification(Notification.OLD_VERSION, message, Notification.NORMAL)))
        }

        val endDate = sp.getLong(rh.gs(R.string.key_app_expiration) + "_" + config.VERSION_NAME, 0)
        if (endDate != 0L && dateUtil.now() > endDate && shouldWarnAgain()) {
            // store last notification time
            sp.putLong(R.string.key_last_versionchecker_plugin_warning, now)

            //notify
            rxBus.send(EventNewNotification(Notification(Notification.VERSION_EXPIRE, rh.gs(R.string.application_expired), Notification.URGENT)))
        }
    }

    private fun shouldWarnAgain() =
        dateUtil.now() > sp.getLong(R.string.key_last_versionchecker_plugin_warning, 0) + WARN_EVERY

    private fun isOldVersion(gracePeriod: Long): Boolean =
        dateUtil.now() > sp.getLong(R.string.key_last_time_this_version_detected_as_ok, 0) + gracePeriod
}

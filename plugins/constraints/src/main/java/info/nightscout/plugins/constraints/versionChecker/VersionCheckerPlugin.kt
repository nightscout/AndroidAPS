package info.nightscout.plugins.constraints.versionChecker

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.versionChecker.VersionCheckerUtils
import info.nightscout.plugins.constraints.R
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
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
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList(false)
        .pluginName(R.string.version_checker),
    aapsLogger, rh, injector
), Constraints {

    enum class GracePeriod(val warning: Long, val old: Long, val veryOld: Long) {
        RELEASE(30, 60, 90),
        RC(1, 7, 14)
    }

    private val gracePeriod: GracePeriod
        get() = if ((config.VERSION_NAME.contains("RC", ignoreCase = true))) {
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
        val endDate = sp.getLong(rh.gs(info.nightscout.core.utils.R.string.key_app_expiration) + "_" + config.VERSION_NAME, 0)
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
            uiInteraction.addNotification(Notification.OLD_VERSION, message, Notification.NORMAL)
        }

        val endDate = sp.getLong(rh.gs(info.nightscout.core.utils.R.string.key_app_expiration) + "_" + config.VERSION_NAME, 0)
        if (endDate != 0L && dateUtil.now() > endDate && shouldWarnAgain()) {
            // store last notification time
            sp.putLong(R.string.key_last_versionchecker_plugin_warning, now)

            //notify
            uiInteraction.addNotification(Notification.VERSION_EXPIRE, rh.gs(R.string.application_expired), Notification.URGENT)
        }
    }

    private fun shouldWarnAgain() =
        dateUtil.now() > sp.getLong(R.string.key_last_versionchecker_plugin_warning, 0) + WARN_EVERY

    private fun isOldVersion(gracePeriod: Long): Boolean =
        dateUtil.now() > sp.getLong(R.string.key_last_time_this_version_detected_as_ok, 0) + gracePeriod

    private fun Long.daysToMillis() = TimeUnit.DAYS.toMillis(this)
}

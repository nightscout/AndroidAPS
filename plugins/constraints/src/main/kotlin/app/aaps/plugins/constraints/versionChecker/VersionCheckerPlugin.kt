package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.constraints.R
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
), PluginConstraints {

    enum class GracePeriod(val warning: Long, val old: Long, val veryOld: Long) {
        RELEASE(30, 60, 90),
        RC(2, 7, 14)
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
        if (lastCheckOlderThan(gracePeriod.veryOld.daysToMillis()))
            value.set(false, rh.gs(R.string.very_old_version), this)
        val endDate = sp.getLong(rh.gs(info.nightscout.core.utils.R.string.key_app_expiration) + "_" + config.VERSION_NAME, 0)
        if (endDate != 0L && dateUtil.now() > endDate)
            value.set(false, rh.gs(R.string.application_expired), this)
        return value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> =
        if (lastCheckOlderThan(gracePeriod.old.daysToMillis()))
            maxIob.set(0.0, rh.gs(R.string.old_version), this)
        else
            maxIob

    private fun checkWarning() {
        val now = dateUtil.now()

        if (!sp.contains(R.string.key_last_versionchecker_plugin_warning_timestamp)) {
            sp.putLong(R.string.key_last_versionchecker_plugin_warning_timestamp, now)
            return
        }

        if (lastCheckOlderThan(gracePeriod.warning.daysToMillis()) && shouldWarnAgain()) {
            // store last notification time
            sp.putLong(R.string.key_last_versionchecker_plugin_warning_timestamp, now)

            //notify
            val message = rh.gs(
                R.string.new_version_warning,
                ((now - sp.getLong(R.string.key_last_successful_version_check_timestamp, now)) / 1L.daysToMillis().toDouble()).roundToInt(),
                gracePeriod.old,
                gracePeriod.veryOld
            )
            uiInteraction.addNotification(Notification.OLD_VERSION, message, Notification.NORMAL)
        }

        val endDate = sp.getLong(rh.gs(info.nightscout.core.utils.R.string.key_app_expiration) + "_" + config.VERSION_NAME, 0)
        if (endDate != 0L && dateUtil.now() > endDate && shouldWarnAgain()) {
            // store last notification time
            sp.putLong(R.string.key_last_versionchecker_plugin_warning_timestamp, now)

            //notify
            uiInteraction.addNotification(Notification.VERSION_EXPIRE, rh.gs(R.string.application_expired), Notification.URGENT)
        }
    }

    private fun shouldWarnAgain() =
        dateUtil.now() > sp.getLong(R.string.key_last_versionchecker_plugin_warning_timestamp, 0) + WARN_EVERY

    private fun lastCheckOlderThan(gracePeriod: Long): Boolean =
        dateUtil.now() > sp.getLong(R.string.key_last_successful_version_check_timestamp, 0) + gracePeriod

    private fun Long.daysToMillis() = TimeUnit.DAYS.toMillis(this)
}

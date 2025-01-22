package app.aaps.plugins.constraints.versionChecker

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class VersionCheckerPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val versionCheckerUtils: VersionCheckerUtils,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(R.string.version_checker),
    ownPreferences = listOf(VersionCheckerLongKey::class.java, VersionCheckerComposedLongKey::class.java),
    aapsLogger, rh, preferences
), PluginConstraints {

    @Suppress("SpellCheckingInspection")
    enum class VersionCheckerLongKey(
        override val key: String,
        override val defaultValue: Long,
    ) : LongNonPreferenceKey {

        LastWarningTimestamp("last_versionchecker_plugin_warning_timestamp", 0L),
        LastSuccessfulVersionCheck("last_successful_version_check_timestamp", 0L),
        LastExpiredWarning("last_expired_version_checker_warning", 0L),
        LastVersionCheckWarning("last_versionchecker_warning", 0L),
    }

    enum class VersionCheckerComposedLongKey(
        override val key: String,
        override val format: String,
        override val defaultValue: Long,
    ) : LongComposedNonPreferenceKey {

        AppExpiration("app_expiration_", "%s", 0L),
    }

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
        val endDate = preferences.get(VersionCheckerComposedLongKey.AppExpiration, config.VERSION_NAME)
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

        if (preferences.getIfExists(VersionCheckerLongKey.LastWarningTimestamp) == null) {
            preferences.put(VersionCheckerLongKey.LastWarningTimestamp, now)
            return
        }

        if (lastCheckOlderThan(gracePeriod.warning.daysToMillis()) && shouldWarnAgain()) {
            // store last notification time
            preferences.put(VersionCheckerLongKey.LastWarningTimestamp, now)

            //notify
            val message = rh.gs(
                R.string.new_version_warning,
                ((now - preferences.get(VersionCheckerLongKey.LastSuccessfulVersionCheck)) / 1L.daysToMillis().toDouble()).roundToInt(),
                gracePeriod.old,
                gracePeriod.veryOld
            )
            uiInteraction.addNotification(Notification.OLD_VERSION, message, Notification.NORMAL)
        }

        val endDate = preferences.get(VersionCheckerComposedLongKey.AppExpiration, config.VERSION_NAME)
        if (endDate != 0L && dateUtil.now() > endDate && shouldWarnAgain()) {
            // store last notification time
            preferences.put(VersionCheckerLongKey.LastWarningTimestamp, now)

            //notify
            uiInteraction.addNotification(Notification.VERSION_EXPIRE, rh.gs(R.string.application_expired), Notification.URGENT)
        }
    }

    private fun shouldWarnAgain() =
        dateUtil.now() > preferences.get(VersionCheckerLongKey.LastWarningTimestamp) + WARN_EVERY

    private fun lastCheckOlderThan(gracePeriod: Long): Boolean =
        dateUtil.now() > preferences.get(VersionCheckerLongKey.LastSuccessfulVersionCheck) + gracePeriod

    private fun Long.daysToMillis() = TimeUnit.DAYS.toMillis(this)
}

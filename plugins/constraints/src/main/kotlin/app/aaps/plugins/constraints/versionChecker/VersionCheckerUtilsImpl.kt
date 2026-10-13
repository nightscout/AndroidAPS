package app.aaps.plugins.constraints.versionChecker

import android.os.Build
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.interfaces.versionChecker.VersionDefinition
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.versionChecker.keys.VersionCheckerLongKey
import dagger.Lazy
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionCheckerUtilsImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val config: Lazy<Config>,
    private val dateUtil: DateUtil,
    private val uiInteraction: UiInteraction,
    versionDefinition: VersionDefinition
) : VersionCheckerUtils {

    var definition: JSONObject = versionDefinition.invoke()

    override fun triggerCheckVersion() {
        val version: String? = AllowedVersions.findByApi(definition, Build.VERSION.SDK_INT)
        val newVersionByApi = compareWithCurrentVersion(newVersion = version, currentVersion = config.get().VERSION_NAME)

        // App expiration
        if (newVersionByApi || config.get().isDev()) {
            var endDate = preferences.get(LongComposedKey.AppExpiration, config.get().VERSION_NAME)
            AllowedVersions.findByVersion(definition, config.get().VERSION_NAME)?.let { dateAsString ->
                AllowedVersions.endDateToMilliseconds(dateAsString)?.let { ed ->
                    endDate = ed + T.days(1).msecs()
                    preferences.put(LongComposedKey.AppExpiration, config.get().VERSION_NAME, value = endDate)
                }
            }
            if (endDate != 0L) onExpireDateDetected(config.get().VERSION_NAME, endDate)
        }

    }

    @Suppress("SameParameterValue")
    /**
     * @return true if there is a newer version available
     */

    enum class VersionResult {

        NOT_DETECTABLE, NEWER_VERSION_AVAILABLE, OLDER_VERSION, SAME_VERSION
    }

    fun compareWithCurrentVersion(newVersion: String?, currentVersion: String): Boolean =
        when (evaluateVersion(newVersion, currentVersion)) {
            VersionResult.NOT_DETECTABLE          -> onVersionNotDetectable()
            VersionResult.NEWER_VERSION_AVAILABLE -> onNewVersionDetected(currentVersion, newVersion)
            VersionResult.OLDER_VERSION           -> onOlderVersionDetected()
            VersionResult.SAME_VERSION            -> onSameVersionDetected()
        }

    fun evaluateVersion(newVersion: String?, currentVersion: String): VersionResult {

        val newVersionElements = newVersion.toNumberList()
        val currentVersionElements = currentVersion.toNumberList()

        aapsLogger.debug(LTag.CORE, "Compare versions: $currentVersion $currentVersionElements, $newVersion $newVersionElements")
        if (newVersionElements.isNullOrEmpty()) {
            return VersionResult.NOT_DETECTABLE
        }

        if (currentVersionElements.isNullOrEmpty()) {
            // current version scrambled?!
            return VersionResult.NEWER_VERSION_AVAILABLE
        }

        newVersionElements.take(3).forEachIndexed { i, newElem ->
            val currElem: Int = currentVersionElements.getOrNull(i)
                ?: return VersionResult.NEWER_VERSION_AVAILABLE

            (newElem - currElem).let {
                when {
                    it > 0 -> return VersionResult.NEWER_VERSION_AVAILABLE
                    it < 0 -> return VersionResult.OLDER_VERSION
                    else   -> Unit
                }
            }
        }
        return VersionResult.SAME_VERSION
    }

    private fun onOlderVersionDetected(): Boolean {
        aapsLogger.debug(LTag.CORE, "Version newer than master. Are you developer?")
        return false
    }

    private fun onSameVersionDetected() = false

    private fun onVersionNotDetectable(): Boolean {
        aapsLogger.debug(LTag.CORE, "Fetch failed")
        return false
    }

    private fun onNewVersionDetected(currentVersion: String, newVersion: String?): Boolean {
        val now = dateUtil.now()
        if (dateUtil.isAfterNoon() && now > preferences.get(VersionCheckerLongKey.LastVersionCheckWarning) + warnEvery(0)) {
            aapsLogger.debug(LTag.CORE, "Version $currentVersion outdated. Found $newVersion")
            uiInteraction.addNotification(Notification.NEW_VERSION_DETECTED, rh.gs(R.string.versionavailable, newVersion.toString()), Notification.LOW)
            preferences.put(VersionCheckerLongKey.LastVersionCheckWarning, now)
        }
        return true
    }

    private fun onExpireDateDetected(currentVersion: String, endDate: Long) {
        val now = dateUtil.now()
        if (dateUtil.now() > endDate && shouldWarnAgain()) {
            // store last notification time
            preferences.put(VersionCheckerLongKey.LastVersionCheckWarning, now)
            //notify
            uiInteraction.addNotification(Notification.VERSION_EXPIRE, rh.gs(R.string.application_expired), Notification.URGENT)
        } else if (dateUtil.isAfterNoon() && now > preferences.get(VersionCheckerLongKey.LastVersionCheckWarning) + warnEvery(endDate)) {
            aapsLogger.debug(LTag.CORE, rh.gs(R.string.version_expire, currentVersion, dateUtil.dateString(endDate)))
            uiInteraction.addNotification(Notification.VERSION_EXPIRE, rh.gs(R.string.version_expire, currentVersion, dateUtil.dateString(endDate)), Notification.LOW)
            preferences.put(VersionCheckerLongKey.LastExpiredWarning, now)
        }
    }

    private fun shouldWarnAgain() =
        dateUtil.now() > preferences.get(VersionCheckerLongKey.LastVersionCheckWarning) + warnEvery(expiration = preferences.get(LongComposedKey.AppExpiration, config.get().VERSION_NAME))

    private fun String?.toNumberList() =
        this?.numericVersionPart().takeIf { !it.isNullOrBlank() }?.split(".")?.map { it.toInt() }

    override fun versionDigits(versionString: String?): IntArray {
        val digits = mutableListOf<Int>()
        versionString?.numericVersionPart().toNumberList()?.let {
            digits.addAll(it.take(4))
        }
        return digits.toIntArray()
    }

    private fun warnEvery(expiration: Long): Long =
        when {
            expiration - dateUtil.now() > T.days(28).msecs() -> T.days(7).msecs()
            expiration - dateUtil.now() > T.days(14).msecs() -> T.days(3).msecs()
            else                                             -> T.days(1).msecs()
        }
}

fun String.numericVersionPart(): String =
    "(((\\d+)\\.)+(\\d+))(\\D(.*))?".toRegex().matchEntire(this)?.groupValues?.getOrNull(1)
        ?: ""
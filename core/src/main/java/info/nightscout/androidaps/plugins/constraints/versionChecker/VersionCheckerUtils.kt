package info.nightscout.androidaps.plugins.constraints.versionChecker

import android.os.Build
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionCheckerUtils @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val config: Config,
    private val receiverStatusStore: ReceiverStatusStore,
    private val dateUtil: DateUtil
) {

    fun isConnected(): Boolean = receiverStatusStore.isConnected

    fun triggerCheckVersion() {

        if (!sp.contains(R.string.key_last_time_this_version_detected)) {
            // On a new installation, set it as 30 days old in order to warn that there is a new version.
            sp.putLong(
                R.string.key_last_time_this_version_detected,
                dateUtil.now() - TimeUnit.DAYS.toMillis(30)
            )
        }

        // If we are good, only check once every day.
        if (dateUtil.now() > sp.getLong(
                R.string.key_last_time_this_version_detected,
                0
            ) + CHECK_EVERY
        ) {
            checkVersion()
        }
    }

    private fun checkVersion() =
        if (isConnected()) {
            Thread {
                try {
                    val definition: String = URL("https://raw.githubusercontent.com/nightscout/AndroidAPS/versions/definition.json").readText()
                    val version: String? = AllowedVersions().findByApi(definition, Build.VERSION.SDK_INT)?.optString("supported")
                    compareWithCurrentVersion(version, config.VERSION_NAME)

                    // App expiration
                    var endDate = sp.getLong(rh.gs(R.string.key_app_expiration) + "_" + config.VERSION_NAME, 0)
                    AllowedVersions().findByVersion(definition, config.VERSION_NAME)?.let { expirationJson ->
                        AllowedVersions().endDateToMilliseconds(expirationJson.getString("endDate"))?.let { ed ->
                            endDate = ed + T.days(1).msecs()
                            sp.putLong(rh.gs(R.string.key_app_expiration) + "_" + config.VERSION_NAME, endDate)
                        }
                    }
                    if (endDate != 0L) onExpiredVersionDetected(config.VERSION_NAME, dateUtil.dateString(endDate))

                } catch (e: IOException) {
                    aapsLogger.error(LTag.CORE, "Github master version check error: $e")
                }
            }.start()
        } else
            aapsLogger.debug(LTag.CORE, "Github master version not checked. No connectivity")

    @Suppress("SameParameterValue")
    fun compareWithCurrentVersion(newVersion: String?, currentVersion: String) {

        val newVersionElements = newVersion.toNumberList()
        val currentVersionElements = currentVersion.toNumberList()

        if (newVersionElements == null || newVersionElements.isEmpty()) {
            onVersionNotDetectable()
            return
        }

        if (currentVersionElements == null || currentVersionElements.isEmpty()) {
            // current version scrambled?!
            onNewVersionDetected(currentVersion, newVersion)
            return
        }

        newVersionElements.take(3).forEachIndexed { i, newElem ->
            val currElem: Int = currentVersionElements.getOrNull(i)
                ?: return onNewVersionDetected(currentVersion, newVersion)

            (newElem - currElem).let {
                when {
                    it > 0  -> return onNewVersionDetected(currentVersion, newVersion)
                    it < 0  -> return onOlderVersionDetected()
                    it == 0 -> Unit
                }
            }
        }
        onSameVersionDetected()
    }

    private fun onOlderVersionDetected() {
        aapsLogger.debug(LTag.CORE, "Version newer than master. Are you developer?")
        sp.putLong(R.string.key_last_time_this_version_detected, dateUtil.now())
    }

    private fun onSameVersionDetected() {
        sp.putLong(R.string.key_last_time_this_version_detected, dateUtil.now())
    }

    private fun onVersionNotDetectable() {
        aapsLogger.debug(LTag.CORE, "fetch failed")
    }

    private fun onNewVersionDetected(currentVersion: String, newVersion: String?) {
        val now = dateUtil.now()
        if (now > sp.getLong(R.string.key_last_versionchecker_warning, 0) + WARN_EVERY) {
            aapsLogger.debug(LTag.CORE, "Version $currentVersion outdated. Found $newVersion")
            val notification = Notification(
                Notification.NEW_VERSION_DETECTED,
                rh.gs(R.string.versionavailable, newVersion.toString()),
                Notification.LOW
            )
            rxBus.send(EventNewNotification(notification))
            sp.putLong(R.string.key_last_versionchecker_warning, now)
        }
    }

    private fun onExpiredVersionDetected(currentVersion: String, endDate: String?) {
        val now = dateUtil.now()
        if (now > sp.getLong(R.string.key_last_expired_versionchecker_warning, 0) + WARN_EVERY) {
            aapsLogger.debug(LTag.CORE, "Version $currentVersion expired.")
            val notification = Notification(
                Notification.VERSION_EXPIRE,
                rh.gs(R.string.version_expire, currentVersion, endDate),
                Notification.LOW
            )
            rxBus.send(EventNewNotification(notification))
            sp.putLong(R.string.key_last_expired_versionchecker_warning, now)
        }
    }

    private fun String?.toNumberList() =
        this?.numericVersionPart().takeIf { !it.isNullOrBlank() }?.split(".")?.map { it.toInt() }

    fun versionDigits(versionString: String?): IntArray {
        val digits = mutableListOf<Int>()
        versionString?.numericVersionPart().toNumberList()?.let {
            digits.addAll(it.take(4))
        }
        return digits.toIntArray()
    }

    fun findVersion(file: String?): String? {
        val regex = "(.*)version(.*)\"(((\\d+)\\.)+(\\d+))\"(.*)".toRegex()
        return file?.lines()?.filter { regex.matches(it) }
            ?.mapNotNull { regex.matchEntire(it)?.groupValues?.getOrNull(3) }?.firstOrNull()
    }

    companion object {

        private val CHECK_EVERY = TimeUnit.DAYS.toMillis(1)
        private val WARN_EVERY = TimeUnit.DAYS.toMillis(1)
    }
}

fun String.numericVersionPart(): String =
    "(((\\d+)\\.)+(\\d+))(\\D(.*))?".toRegex().matchEntire(this)?.groupValues?.getOrNull(1)
        ?: ""

@Suppress("unused") fun findVersion(file: String?): String? {
    val regex = "(.*)version(.*)\"(((\\d+)\\.)+(\\d+))\"(.*)".toRegex()
    return file?.lines()?.filter { regex.matches(it) }
        ?.mapNotNull { regex.matchEntire(it)?.groupValues?.getOrNull(3) }?.firstOrNull()
}

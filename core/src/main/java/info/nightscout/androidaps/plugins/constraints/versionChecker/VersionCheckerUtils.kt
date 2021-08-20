package info.nightscout.androidaps.plugins.constraints.versionChecker

import android.content.Context
import android.net.ConnectivityManager
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.ConfigInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VersionCheckerUtils @Inject constructor(
    val aapsLogger: AAPSLogger,
    val sp: SP,
    val resourceHelper: ResourceHelper,
    val rxBus: RxBusWrapper,
    private val config: ConfigInterface,
    val context: Context
) {

    // check network connection
    fun isConnected(): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connMgr.activeNetworkInfo?.isConnected ?: false
    }

    fun triggerCheckVersion() {

        if (!sp.contains(R.string.key_last_time_this_version_detected)) {
            // On a new installation, set it as 30 days old in order to warn that there is a new version.
            sp.putLong(R.string.key_last_time_this_version_detected, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))
        }

        // If we are good, only check once every day.
        if (System.currentTimeMillis() > sp.getLong(R.string.key_last_time_this_version_detected, 0) + CHECK_EVERY) {
            checkVersion()
        }
    }

    private fun checkVersion() = if (isConnected()) {
        Thread {
            try {
                val version: String? = findVersion(URL("https://raw.githubusercontent.com/nightscout/AndroidAPS/master/app/build.gradle").readText())
                compareWithCurrentVersion(version, config.VERSION_NAME)
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
        sp.putLong(R.string.key_last_time_this_version_detected, System.currentTimeMillis())
    }

    private fun onSameVersionDetected() {
        sp.putLong(R.string.key_last_time_this_version_detected, System.currentTimeMillis())
    }

    private fun onVersionNotDetectable() {
        aapsLogger.debug(LTag.CORE, "fetch failed")
    }

    private fun onNewVersionDetected(currentVersion: String, newVersion: String?) {
        val now = System.currentTimeMillis()
        if (now > sp.getLong(R.string.key_last_versionchecker_warning, 0) + WARN_EVERY) {
            aapsLogger.debug(LTag.CORE, "Version $currentVersion outdated. Found $newVersion")
            val notification = Notification(Notification.NEWVERSIONDETECTED, resourceHelper.gs(R.string.versionavailable, newVersion.toString()), Notification.LOW)
            rxBus.send(EventNewNotification(notification))
            sp.putLong(R.string.key_last_versionchecker_warning, now)
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
        return file?.lines()?.filter { regex.matches(it) }?.mapNotNull { regex.matchEntire(it)?.groupValues?.getOrNull(3) }?.firstOrNull()
    }

    companion object {

        private val CHECK_EVERY = TimeUnit.DAYS.toMillis(1)
        private val WARN_EVERY = TimeUnit.DAYS.toMillis(1)
    }
}

fun String.numericVersionPart(): String =
    "(((\\d+)\\.)+(\\d+))(\\D(.*))?".toRegex().matchEntire(this)?.groupValues?.getOrNull(1)
        ?: ""

fun findVersion(file: String?): String? {
    val regex = "(.*)version(.*)\"(((\\d+)\\.)+(\\d+))\"(.*)".toRegex()
    return file?.lines()?.filter { regex.matches(it) }?.mapNotNull { regex.matchEntire(it)?.groupValues?.getOrNull(3) }?.firstOrNull()
}

@Deprecated(replaceWith = ReplaceWith("numericVersionPart()"), message = "Will not work if RCs have another index number in it.")
fun String.versionStrip() = this.mapNotNull {
    when (it) {
        in '0'..'9' -> it
        '.' -> it
        else        -> null
    }
}.joinToString(separator = "")
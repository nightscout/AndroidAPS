package info.nightscout.androidaps.plugins.constraints.versionChecker

import android.content.Context
import android.net.ConnectivityManager
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.SP
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

// check network connection
fun isConnected(): Boolean {
    val connMgr = MainApp.instance().applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return connMgr.activeNetworkInfo?.isConnected ?: false
}

private val log = LoggerFactory.getLogger(L.CORE)

fun triggerCheckVersion() {

    if (!SP.contains(R.string.key_last_time_this_version_detected)) {
        // On a new installation, set it as 30 days old in order to warn that there is a new version.
        SP.putLong(R.string.key_last_time_this_version_detected, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))
    }

    // If we are good, only check once every day.
    if (System.currentTimeMillis() > SP.getLong(R.string.key_last_time_this_version_detected, 0) + CHECK_EVERY) {
        checkVersion()
    }
}

private fun checkVersion() = if (isConnected()) {
    Thread {
        try {
            val version: String? = findVersion(URL("https://raw.githubusercontent.com/MilosKozak/AndroidAPS/master/app/build.gradle").readText())
            compareWithCurrentVersion(version, BuildConfig.VERSION_NAME)
        } catch (e: IOException) {
            log.debug("Github master version check error: $e")
        }
    }.start()
} else
    log.debug("Github master version no checked. No connectivity")

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
    log.debug("Version newer than master. Are you developer?")
    SP.putLong(R.string.key_last_time_this_version_detected, System.currentTimeMillis())
}

fun onSameVersionDetected() {
    SP.putLong(R.string.key_last_time_this_version_detected, System.currentTimeMillis())
}

fun onVersionNotDetectable() {
    log.debug("fetch failed")
}

fun onNewVersionDetected(currentVersion: String, newVersion: String?) {
    val now = System.currentTimeMillis()
    if (now > SP.getLong(R.string.key_last_versionchecker_warning, 0) + WARN_EVERY) {
        log.debug("Version ${currentVersion} outdated. Found $newVersion")
        val notification = Notification(Notification.NEWVERSIONDETECTED, String.format(MainApp.gs(R.string.versionavailable), newVersion.toString()), Notification.LOW)
        RxBus.send(EventNewNotification(notification))
        SP.putLong(R.string.key_last_versionchecker_warning, now)
    }
}

@Deprecated(replaceWith = ReplaceWith("numericVersionPart()"), message = "Will not work if RCs have another index number in it.")
fun String.versionStrip() = this.mapNotNull {
    when (it) {
        in '0'..'9' -> it
        '.'         -> it
        else        -> null
    }
}.joinToString(separator = "")

fun String.numericVersionPart(): String =
    "(((\\d+)\\.)+(\\d+))(\\D(.*))?".toRegex().matchEntire(this)?.groupValues?.getOrNull(1) ?: ""

fun String?.toNumberList() =
    this?.numericVersionPart().takeIf { !it.isNullOrBlank() }?.split(".")?.map { it.toInt() }

fun findVersion(file: String?): String? {
    val regex = "(.*)version(.*)\"(((\\d+)\\.)+(\\d+))\"(.*)".toRegex()
    return file?.lines()?.filter { regex.matches(it) }?.mapNotNull { regex.matchEntire(it)?.groupValues?.getOrNull(3) }?.firstOrNull()
}

val CHECK_EVERY = TimeUnit.DAYS.toMillis(1)
val WARN_EVERY = TimeUnit.DAYS.toMillis(1)

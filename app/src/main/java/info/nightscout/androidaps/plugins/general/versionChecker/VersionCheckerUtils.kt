package info.nightscout.androidaps.plugins.general.versionChecker

import android.content.Context
import android.net.ConnectivityManager
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.utils.SP
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

// check network connection
fun isConnected(): Boolean {
    val connMgr = MainApp.instance().applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return connMgr.activeNetworkInfo?.isConnected ?: false
}

// convert inputstream to String
@Throws(IOException::class)
inline fun InputStream.findVersion(): String? {
    val regex = "(.*)version(.*)\"(((\\d+)\\.)+(\\d+))\"(.*)".toRegex()
    return bufferedReader()
            .readLines()
            .filter { regex.matches(it) }
            .mapNotNull { regex.matchEntire(it)?.groupValues?.getOrNull(3) }
            .firstOrNull()
}

private val log = LoggerFactory.getLogger(L.CORE)


fun triggerCheckVersion() {

    if(!SP.contains(R.string.key_last_time_this_version_detected)) {
        // On a new installation, set it as 30 days old in order to warn that there is a new version.
        SP.putLong(R.string.key_last_time_this_version_detected, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))
    }

    // If we are good, only check once every day.
    if(System.currentTimeMillis() > SP.getLong(R.string.key_last_time_this_version_detected, 0) + CHECK_EVERY){
        checkVersion()
    }
}

@Suppress("DEPRECATION")
private fun checkVersion() = if (isConnected()) {
    Thread {
        try {
            val request = HttpGet("https://raw.githubusercontent.com/MilosKozak/AndroidAPS/master/app/build.gradle")
            val response: HttpResponse = DefaultHttpClient().execute(request)
            val version: String? = response.entity.content?.findVersion()
            compareWithCurrentVersion(version, BuildConfig.VERSION_NAME)
        } catch (e: IOException) {
            log.debug("Github master version check error: $e")
        }
    }.start()
} else
    log.debug("Github master version no checked. No connectivity")

fun compareWithCurrentVersion(newVersion: String?, currentVersion: String) {
    val comparison: Int? = newVersion?.versionStrip()?.compareTo(currentVersion.versionStrip())
    when {
        comparison == null -> onVersionNotDetectable()
        comparison == 0 -> onSameVersionDetected()
        comparison > 0 -> onNewVersionDetected(currentVersion = currentVersion, newVersion = newVersion)
        else -> onOlderVersionDetected()
    }
}

private fun onOlderVersionDetected() {
    log.debug("Version newer than master. Are you developer?")
    SP.putLong(R.string.key_last_time_this_version_detected, System.currentTimeMillis())
}

fun onSameVersionDetected() {
    SP.putLong(R.string.key_last_time_this_version_detected, System.currentTimeMillis())
}

fun onVersionNotDetectable() {
    log.debug("fetch failed, ignore and smartcast to non-null")
}

fun onNewVersionDetected(currentVersion: String, newVersion: String?) {
    val now = System.currentTimeMillis()
    if(now > SP.getLong(R.string.key_last_versionchecker_warning, 0) + WARN_EVERY) {
        log.debug("Version ${currentVersion} outdated. Found $newVersion")
        val notification = Notification(Notification.NEWVERSIONDETECTED, String.format(MainApp.gs(R.string.versionavailable), newVersion.toString()), Notification.LOW)
        MainApp.bus().post(EventNewNotification(notification))
        SP.putLong(R.string.key_last_versionchecker_warning, now)
    }
}

fun String.versionStrip() = this.mapNotNull {
    when (it) {
        in '0'..'9' -> it
        '.' -> it
        else -> null
    }
}.joinToString(separator = "")


val CHECK_EVERY = TimeUnit.DAYS.toMillis(1)
val WARN_EVERY = TimeUnit.DAYS.toMillis(1)

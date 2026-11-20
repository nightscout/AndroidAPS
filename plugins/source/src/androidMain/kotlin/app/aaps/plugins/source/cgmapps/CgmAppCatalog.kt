package app.aaps.plugins.source.cgmapps

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * The newest [CgmApps] this phone has: the bundled `cgm_apps.json`, or a newer one fetched from the `versions` branch
 * before. Read on first use, not when the graph is built.
 */
@SingleIn(AppScope::class)
@Inject
class CgmAppCatalog(
    private val context: Context,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) {

    @Volatile private var loaded: CgmApps? = null

    val apps: CgmApps
        get() = loaded ?: load().also { loaded = it }

    private fun load(): CgmApps {
        val stored = preferences.get(StringNonKey.CgmAppCatalog).takeIf { it.isNotEmpty() }?.let { parse(it, "stored") }
        val bundled = try {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }.let { parse(it, "bundled") }
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "CGM app catalog: cannot read $ASSET", e)
            null
        }
        return when {
            stored != null && (bundled == null || stored.version > bundled.version) -> stored
            bundled != null                                                         -> bundled.also { preferences.remove(StringNonKey.CgmAppCatalog) }
            else                                                                    -> CgmApps()
        }
    }

    /**
     * The first of the apps with these [ids] that is on this phone, or null when none is. An app counts when one of its
     * packages is installed; the `<queries>` entry of the manifest makes them visible. The answer is kept for
     * [INSTALLED_CACHE_MS], because a calibration mode built on it is read often.
     */
    fun firstInstalled(vararg ids: String): CgmApp? =
        ids.firstNotNullOfOrNull { id -> apps.byId(id)?.takeIf { app -> app.packages.any(::isInstalled) } }

    /**
     * The package of the installed app that serves the content provider with this [authority], or null when none does.
     * For a source that reads a vendor app's provider rather than a broadcast: the provider names the app exactly.
     * Kept for [INSTALLED_CACHE_MS] like [firstInstalled].
     */
    fun providerPackage(authority: String): String? = cached("provider:$authority") {
        @Suppress("DEPRECATION")
        context.packageManager.resolveContentProvider(authority, 0)?.packageName
    }

    private val installedCache = ConcurrentHashMap<String, Pair<Long, String?>>()

    private fun isInstalled(packageName: String): Boolean = cached(packageName) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        packageName
    } != null

    /** [lookup] answered from [installedCache] while fresh. A lookup that throws counts as not installed. */
    private fun cached(key: String, lookup: () -> String?): String? {
        val now = System.currentTimeMillis()
        installedCache[key]?.takeIf { now - it.first < INSTALLED_CACHE_MS }?.let { return it.second }
        val answer = try {
            lookup()
        } catch (_: Exception) {
            null
        }
        installedCache[key] = now to answer
        return answer
    }

    /**
     * Fetches the catalog from the `versions` branch and keeps it when it is newer than the one in use. Blocking:
     * call it off the main thread. A failure only leaves the catalog as it was.
     */
    fun updateFromRemote() {
        try {
            val text = URL(REMOTE_URL).readText()
            val remote = parse(text, "remote") ?: return
            if (remote.version > apps.version) {
                aapsLogger.info(LTag.BGSOURCE, "CGM app catalog: v${apps.version} -> v${remote.version}")
                preferences.put(StringNonKey.CgmAppCatalog, text)
                loaded = remote
            }
        } catch (e: Exception) {
            aapsLogger.debug(LTag.BGSOURCE, "CGM app catalog: no update (${e.message})")
        }
    }

    private fun parse(text: String, what: String): CgmApps? =
        try {
            CgmApps.fromJson(text)
        } catch (e: Exception) {
            aapsLogger.error(LTag.BGSOURCE, "CGM app catalog: the $what one cannot be read", e)
            null
        }

    private companion object {

        const val ASSET = "cgm_apps.json"
        const val INSTALLED_CACHE_MS = 30_000L
        const val REMOTE_URL = "https://raw.githubusercontent.com/nightscout/AndroidAPS/refs/heads/versions/cgm_apps.json"
    }
}

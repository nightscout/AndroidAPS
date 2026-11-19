package app.aaps.plugins.constraints.versionChecker

import android.os.Build
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.interfaces.versionChecker.VersionDefinition
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.plugins.constraints.ConstraintsStrings
import app.aaps.plugins.constraints.versionChecker.keys.VersionCheckerLongKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class VersionCheckerUtilsImpl(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rh: TextResolver,
    private val config: () -> Config,
    private val dateUtil: DateUtil,
    private val notificationManager: NotificationManager,
    private val versionDefinition: VersionDefinition,
    @ApplicationScope private val appScope: CoroutineScope
) : VersionCheckerUtils {

    /**
     * The bundled `definition.json`, read on first use. Lazy, because invoking [VersionDefinition] opens the file from
     * the assets: as a property initializer it made *building the object graph* do file I/O - wrong in production, and
     * fatal in a unit test, where the context is a `RETURNS_MOCKS` mock so `assets.open()` returns a mock `InputStream`
     * whose `read()` answers 0 and never -1, and the read loop in `SignatureVerifierPlugin.readInputStream` never
     * terminates.
     */
    private val bundled: JsonObject by lazy { versionDefinition.invoke() }

    /** The file on GitHub ([GITHUB_DEFINITION_URL]), once fetched. */
    @Volatile private var github: JsonObject? = null

    /** From Firebase remote config ([setRemoteDefinition]). */
    @Volatile private var remote: JsonObject? = null

    /**
     * The allowed versions: the bundled file, then the file on GitHub, then remote config, a later one winning for a key
     * that more than one has. The order holds whichever of the two remote sources answers first.
     */
    val definition: JsonObject
        get() = JsonObject(bundled + (github ?: EMPTY) + (remote ?: EMPTY))

    override fun setRemoteDefinition(definition: JsonObject) {
        remote = definition
    }

    /** How the GitHub file is read. A test replaces it. */
    internal var fetchGithubDefinition: suspend () -> String = {
        withContext(Dispatchers.IO) { URL(GITHUB_DEFINITION_URL).readText() }
    }

    private val githubLock = Any()
    private var nextGithubFetch = 0L
    private var githubFetchRunning = false

    /** At most once a day after a success, once an hour after a failure; never two at once. Does not wait for it. */
    internal fun fetchGithubDefinitionIfDue() {
        val now = dateUtil.now()
        synchronized(githubLock) {
            if (githubFetchRunning || now < nextGithubFetch) return
            githubFetchRunning = true
        }
        appScope.launch {
            val fetched = try {
                Json.parseToJsonElement(fetchGithubDefinition()).jsonObject
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Version definition from GitHub not read: $e")
                null
            }
            if (fetched != null) {
                github = fetched
                aapsLogger.debug(LTag.CORE, "Version definition from GitHub read: ${fetched.size} entries")
            }
            synchronized(githubLock) {
                nextGithubFetch = dateUtil.now() + if (fetched != null) T.days(1).msecs() else T.hours(1).msecs()
                githubFetchRunning = false
            }
        }
    }

    override fun triggerCheckVersion() {
        // Used by the next check: this one goes on with what is known now.
        fetchGithubDefinitionIfDue()
        val version: String? = AllowedVersions.findByApi(definition, Build.VERSION.SDK_INT)
        val newVersionByApi = compareWithCurrentVersion(newVersion = version, currentVersion = config().VERSION_NAME)

        // App expiration
        if (newVersionByApi || config().isDev()) {
            var endDate = preferences.get(LongComposedKey.AppExpiration, config().VERSION_NAME)
            AllowedVersions.findByVersion(definition, config().VERSION_NAME)?.let { dateAsString ->
                AllowedVersions.endDateToMilliseconds(dateAsString)?.let { ed ->
                    endDate = ed + T.days(1).msecs()
                    preferences.put(LongComposedKey.AppExpiration, config().VERSION_NAME, value = endDate)
                }
            }
            if (endDate != 0L) onExpireDateDetected(config().VERSION_NAME, endDate)
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
            notificationManager.post(NotificationId.NEW_VERSION_DETECTED, ConstraintsStrings.versionavailable.withArgs(newVersion.toString()), level = NotificationLevel.LOW)
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
            notificationManager.post(NotificationId.VERSION_EXPIRE, ConstraintsStrings.application_expired)
        } else if (dateUtil.isAfterNoon() && now > preferences.get(VersionCheckerLongKey.LastVersionCheckWarning) + warnEvery(endDate)) {
            aapsLogger.debug(LTag.CORE, rh.gs(ConstraintsStrings.version_expire, currentVersion, dateUtil.dateString(endDate)))
            notificationManager.post(NotificationId.VERSION_EXPIRE, ConstraintsStrings.version_expire.withArgs(currentVersion,dateUtil.dateString(endDate)), level = NotificationLevel.LOW)
            preferences.put(VersionCheckerLongKey.LastExpiredWarning, now)
        }
    }

    private fun shouldWarnAgain() =
        dateUtil.now() > preferences.get(VersionCheckerLongKey.LastVersionCheckWarning) + warnEvery(expiration = preferences.get(LongComposedKey.AppExpiration, config().VERSION_NAME))

    private fun String?.toNumberList() =
        this?.numericVersionPart().takeIf { !it.isNullOrBlank() }?.split(".")?.map { it.toInt() }

    override fun versionDigits(versionString: String?): IntArray {
        val digits = mutableListOf<Int>()
        versionString?.numericVersionPart().toNumberList()?.let {
            digits.addAll(it.take(3))
        }
        return digits.toIntArray()
    }

    private fun warnEvery(expiration: Long): Long =
        when {
            expiration - dateUtil.now() > T.days(28).msecs() -> T.days(7).msecs()
            expiration - dateUtil.now() > T.days(14).msecs() -> T.days(3).msecs()
            else                                             -> T.days(1).msecs()
        }

    companion object {

        /**
         * The allowed versions on GitHub, in the flat form of the bundled file and of remote config. A file of its
         * own: `definition.json` on the same branch keeps the old array form, because older versions of AAPS still read
         * it.
         */
        const val GITHUB_DEFINITION_URL = "https://raw.githubusercontent.com/nightscout/AndroidAPS/versions/version_definitions.json"

        private val EMPTY = JsonObject(emptyMap())
    }
}

fun String.numericVersionPart(): String =
    "(((\\d+)\\.)+(\\d+))(\\D(.*))?".toRegex().matchEntire(this)?.groupValues?.getOrNull(1)
        ?: ""
package app.aaps.wear.watchfaces

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.BuildConfig
import app.aaps.wear.watchfaces.WatchFacePushHelper.Companion.KEY_FACE_INSTALLED
import dev.zacsweers.metro.Inject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Installs and updates the embedded "AAPS V4" Watch Face Format face through the Watch Face Push
 * API. Only available on Wear OS 6+ (API 36) — the watches that no longer support the code-based
 * AAPS watchfaces and therefore have no other way to get an AAPS face.
 *
 * The face APK and its validation token are generated at build time (see `EmbedWatchFaceTask` in
 * the wear build script) and shipped in assets. The token is bound to the exact APK bytes, so both
 * always travel together.
 */
class WatchFacePushHelper @Inject constructor(
    private val context: Context,
    private val sp: SP,
    private val aapsLogger: AAPSLogger
) {

    companion object {

        private const val ASSET_APK = "watchfacepush/aapsv4.apk"
        private const val ASSET_TOKEN = "watchfacepush/aapsv4_token.txt"
        private const val MIN_SDK = 36

        const val KEY_FACE_INSTALLED = "wfpush_face_installed"
        private const val KEY_SYNCED_VERSION = "wfpush_synced_version"
    }

    private val facePackageName get() = "${context.packageName}.watchfacepush.aapsv4"

    /** Defensive: every flavor currently embeds the face, but a build without the asset must report unsupported */
    private val hasEmbeddedFace: Boolean by lazy {
        try {
            context.assets.list("watchfacepush")?.isNotEmpty() == true
        } catch (_: IOException) {
            false
        }
    }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= MIN_SDK && hasEmbeddedFace

    /**
     * Whether the embedded face is currently installed in this app's Watch Face Push slot.
     * Also refreshes the [KEY_FACE_INSTALLED] flag used for synchronous menu building.
     */
    suspend fun isFaceInstalled(): Boolean = withContext(Dispatchers.IO) {
        if (!isSupported()) return@withContext false
        try {
            // Slot occupancy alone is not enough: after a manual uninstall of the face package
            // the slot can stay stale, and the user needs the install entry back (the install
            // then takes the update path and revives the slot)
            val installed = installedFaceSlotId(createManager()) != null && facePackagePresent()
            sp.putBoolean(KEY_FACE_INSTALLED, installed)
            installed
        } catch (e: Exception) {
            // Broad on purpose: besides the typed Push exceptions, the manager surfaces raw
            // RuntimeExceptions (e.g. SecurityException) across its IPC — never crash on them
            aapsLogger.error(LTag.WEAR, "WatchFacePush: listing watch faces failed", e)
            false
        }
    }

    /**
     * Called on app start: makes sure the embedded face is available in the watch face picker.
     * Each app install/update pushes the face once — the same contract as the code-based
     * watchfaces, which always ship with the app. A face the user removed stays removed until a
     * reinstall from the main menu or the next app update. Never activates the face: selecting it
     * is always the user's choice (picker, or the main menu entry).
     */
    suspend fun syncOnStartup() {
        if (!isSupported()) return
        // KEY_SYNCED_VERSION matching means this exact app build already put the face in place
        // once — if the face is missing now, the user removed it: respect that
        if (sp.getString(KEY_SYNCED_VERSION, "") == BuildConfig.BUILDVERSION) return
        aapsLogger.debug(LTag.WEAR, "WatchFacePush: syncing face for app version ${BuildConfig.BUILDVERSION}")
        // Catches all its failures internally — this path runs on app start and must never take
        // the app down; on failure the version is not marked synced, so the next start retries
        installOrUpdate()
    }

    /**
     * Installs the embedded face (or updates it if already installed).
     *
     * @param activate also try to make the face the active watch face afterwards. Pass true only
     *   for user-triggered installs (menu tap = the user wants the face on the wrist, including
     *   reinstalls over a stale slot); the background version sync must never steal the active
     *   face from a user who deliberately switched away. Activation needs the
     *   `SET_PUSHED_WATCH_FACE_AS_ACTIVE` permission (or the API's one-shot allowance) and fails
     *   soft — the face stays installed and selectable in the picker.
     * @return true when the face ended up installed/updated
     */
    suspend fun installOrUpdate(activate: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!isSupported()) return@withContext false
        val apkFile = File(context.cacheDir, "wfpush_aapsv4.apk")
        try {
            val manager = createManager()
            val token = context.assets.open(ASSET_TOKEN).use { String(it.readBytes()) }.trim()
            context.assets.open(ASSET_APK).use { input ->
                apkFile.outputStream().use { input.copyTo(it) }
            }
            val existingSlotId = installedFaceSlotId(manager)
            ParcelFileDescriptor.open(apkFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                if (existingSlotId == null) {
                    manager.addWatchFace(fd, token)
                } else {
                    manager.updateWatchFace(existingSlotId, fd, token)
                }
            }
            if (activate) {
                installedFaceSlotId(manager)?.let { slotId -> setActive(manager, slotId) }
            }
            sp.putString(KEY_SYNCED_VERSION, BuildConfig.BUILDVERSION)
            sp.putBoolean(KEY_FACE_INSTALLED, true)
            aapsLogger.debug(LTag.WEAR, "WatchFacePush: face ${if (existingSlotId == null) "installed" else "updated"}")
            true
        } catch (e: Exception) {
            // Broad on purpose: covers the typed Add/Update/List exceptions, IO on the embedded
            // asset AND the raw RuntimeExceptions the manager surfaces across its IPC
            aapsLogger.error(LTag.WEAR, "WatchFacePush: install/update failed", e)
            false
        } finally {
            apkFile.delete()
        }
    }

    /** Whether the embedded face is the currently shown watch face */
    suspend fun isFaceActive(): Boolean = withContext(Dispatchers.IO) {
        if (!isSupported()) return@withContext false
        try {
            createManager().isWatchFaceActive(facePackageName)
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "WatchFacePush: active check failed", e)
            false
        }
    }

    private fun facePackagePresent(): Boolean =
        try {
            context.packageManager.getPackageInfo(facePackageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun createManager(): WatchFacePushManager =
        WatchFacePushManagerFactory.createWatchFacePushManager(context)

    private suspend fun installedFaceSlotId(manager: WatchFacePushManager): String? =
        manager.listWatchFaces().installedWatchFaceDetails
            .firstOrNull { it.packageName == facePackageName }
            ?.slotId

    private suspend fun setActive(manager: WatchFacePushManager, slotId: String) {
        try {
            manager.setWatchFaceAsActive(slotId)
        } catch (e: Exception) {
            // Expected when the one-shot activation was already used and the permission is not
            // granted — the face is still installed and selectable in the picker.
            aapsLogger.debug(LTag.WEAR, "WatchFacePush: could not set face active: ${e.message}")
        }
    }
}

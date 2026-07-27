package app.aaps.wear.watchfaces

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

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

    /** Not every flavor ships the face (pumpcontrol does not embed it) */
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
            val installed = installedFaceSlotId(createManager()) != null
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
     * Called on app start: keeps an already installed face in sync with the app version by
     * pushing the embedded APK again after an app update. Never installs uninvited — the initial
     * install is user-triggered from the main menu.
     */
    suspend fun syncOnStartup() = withContext(Dispatchers.IO) {
        if (!isSupported()) return@withContext
        try {
            val installed = installedFaceSlotId(createManager()) != null
            sp.putBoolean(KEY_FACE_INSTALLED, installed)
            if (installed && sp.getString(KEY_SYNCED_VERSION, "") != BuildConfig.BUILDVERSION) {
                aapsLogger.debug(LTag.WEAR, "WatchFacePush: app updated, refreshing installed face")
                installOrUpdate()
            }
        } catch (e: Exception) {
            // Broad on purpose: this runs on every app start and must never take the app down —
            // the manager surfaces raw RuntimeExceptions (e.g. SecurityException) across its IPC
            aapsLogger.error(LTag.WEAR, "WatchFacePush: startup sync failed", e)
        }
    }

    /**
     * Installs the embedded face (or updates it if already installed) and, on first install,
     * tries to make it the active watch face — the API allows that once without the
     * `SET_PUSHED_WATCH_FACE_AS_ACTIVE` permission.
     *
     * @return true when the face ended up installed/updated
     */
    suspend fun installOrUpdate(): Boolean = withContext(Dispatchers.IO) {
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
            if (existingSlotId == null) {
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

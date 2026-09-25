package app.aaps.wear.watchfaces

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.PushedWatchfaceId
import app.aaps.wear.watchfaces.WatchFacePushHelper.Companion.KEY_FACE_INSTALLED
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The Watch Face Format faces embedded in the wear app, one per `face` flavor of
 * `:wear:watchfacepush`. The [id] is shared with the phone through [PushedWatchfaceId]: it names
 * the embedded assets and ends the face's package name, which Watch Face Push requires to be
 * `<wear app package>.watchfacepush.<id>`.
 */
enum class PushedFace(val id: String) {

    /** The face designed in Watch Face Studio: a layout of AAPS complications */
    WFS(PushedWatchfaceId.WFS),

    /** The face that shows the wearer's own Custom watchface zip as a picture */
    CWF(PushedWatchfaceId.CWF);

    val assetApk: String get() = "watchfacepush/$id.apk"
    val assetToken: String get() = "watchfacepush/${id}_token.txt"
    fun packageName(appPackage: String): String = "$appPackage.watchfacepush.$id"

    companion object {

        /** The face for a stored id; an unknown id - never written by this app - falls back to [CWF], the default */
        fun fromId(id: String?): PushedFace = entries.firstOrNull { it.id == id } ?: CWF
    }
}

/**
 * Installs and updates the embedded Watch Face Format faces through the Watch Face Push API. Only
 * available on Wear OS 6+ (API 36) — the watches that no longer support the code-based AAPS
 * watchfaces and therefore have no other way to get an AAPS face.
 *
 * Two faces are embedded (see [PushedFace]) but Watch Face Push gives an app **one** slot, measured
 * as `slots used=1 remaining=0` on a Galaxy Watch 4. So the wearer chooses one on the phone, the
 * choice arrives with the preferences and is kept in [selectedFace], and this helper makes sure the
 * slot holds that face: a change of choice updates the slot with the other APK, which the API allows
 * ("a completely different watch face"), at the price of resetting that face's own editor settings.
 *
 * The face APKs and their validation tokens are generated at build time (see `EmbedWatchFaceTask`
 * in the wear build script) and shipped in assets. A token is bound to the exact APK bytes, so both
 * always travel together.
 *
 * One instance for the process, and that is not a detail: the install lock below lives in the
 * instance. Without the scope every injection site got its own helper with its own lock - the app,
 * the data handler, the menu and the data layer service - and two installs ran side by side after
 * all, exactly the race the lock exists to stop. Seen on a Galaxy Watch 5.
 */
@SingleIn(AppScope::class)
@Inject
class WatchFacePushHelper(
    private val context: Context,
    private val sp: SP,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger
) {

    companion object {

        private const val MIN_SDK = 36

        const val KEY_FACE_INSTALLED = "wfpush_face_installed"

        /**
         * The face this app last pushed, stored as its validation token.
         *
         * Deliberately not the app version. The face lives in `watchface.xml` and is rebuilt into
         * the app on every build, so its content changes independently of any version string:
         * between two commits the version is identical while the face is not, and the old check
         * then skipped the push and left a stale face on the watch with nothing said. That is a
         * trap for anyone building AAPS from source, and the "install watchface" menu entry cannot
         * rescue them - it only shows while the face is missing.
         *
         * The token is a hash over the exact APK bytes we embed, so comparing it means "push when
         * the face really changed, and never otherwise". A change of [selectedFace] changes the
         * token too, so the same compare also drives the swap.
         */
        private const val KEY_SYNCED_FACE = "wfpush_synced_face"

        /**
         * The [PushedFace.id] the wearer chose on the phone. Stored on every watch, supported or
         * not: a watch that gets Wear OS 6 later pushes the chosen face on its next start.
         */
        private const val KEY_SELECTED_FACE = "wfpush_selected_face"

        /**
         * How long a fresh install waits for the phone's choice before installing the default.
         *
         * The phone holds the choice, and when it is reachable it answers the resend request that
         * the data layer service sends at start - measured 0.6 s after the app came up. Installing
         * the default first only to replace it costs a second install and an editor reset for
         * nothing. Not reachable: the default goes in after this wait, as it did before.
         */
        private const val FIRST_CHOICE_GRACE_MS = 5_000L
    }

    /** The face the wearer chose on the phone; [PushedFace.CWF] until a choice arrives - the watchface AAPS users know */
    val selectedFace: PushedFace
        get() = PushedFace.fromId(sp.getString(KEY_SELECTED_FACE, PushedFace.CWF.id))

    /** Whether the phone's choice has ever reached this watch; false after a fresh install */
    private val hasStoredSelection: Boolean
        get() = sp.getString(KEY_SELECTED_FACE, "").isNotEmpty()

    /**
     * Records the wearer's choice from the phone.
     *
     * @return true when the choice differs from the stored one, so the caller knows a push is due.
     *   The push is the caller's to start: this runs inside the preferences handler, which must
     *   stay cheap and Android-free.
     */
    fun selectFace(id: String): Boolean {
        val face = PushedFace.fromId(id)
        if (face == selectedFace) return false
        sp.putString(KEY_SELECTED_FACE, face.id)
        aapsLogger.debug(LTag.WEAR, "WatchFacePush: selected face ${face.id}")
        return true
    }

    private val facePackageName get() = selectedFace.packageName(context.packageName)

    /** Defensive: every flavor currently embeds the faces, but a build without the assets must report unsupported */
    private val hasEmbeddedFace: Boolean by lazy {
        try {
            context.assets.list("watchfacepush")?.isNotEmpty() == true
        } catch (_: IOException) {
            false
        }
    }

    /**
     * The validation token shipped beside the embedded face, or null when it cannot be read.
     *
     * Doubles as the identity of the face: the build generates it as a hash over the exact APK
     * bytes, so two builds share a token only when they carry the same face.
     */
    private fun embeddedFaceToken(face: PushedFace): String? =
        try {
            context.assets.open(face.assetToken).use { String(it.readBytes()) }.trim()
        } catch (e: IOException) {
            aapsLogger.error(LTag.WEAR, "WatchFacePush: cannot read the embedded ${face.id} face token", e)
            null
        }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= MIN_SDK && hasEmbeddedFace

    /**
     * Whether the selected face is currently installed in this app's Watch Face Push slot.
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
     * Called on app start: makes sure the selected face is available in the watch face picker.
     * Each app install/update pushes the face once — the same contract as the code-based
     * watchfaces, which always ship with the app. A face the user removed stays removed until a
     * reinstall from the main menu, a change of the selected face, or the next app update. Never
     * activates the face: selecting it is always the user's choice (picker, or the main menu entry).
     */
    suspend fun syncOnStartup() {
        if (!isSupported()) {
            reportStatus()
            return
        }
        // A fresh install has no choice stored yet. Give the phone a moment to send it, so the
        // face that goes in is the chosen one rather than the default followed by a swap. If the
        // choice arrives meanwhile, the preferences path installs it and the compare below finds
        // it already in place.
        if (!hasStoredSelection) {
            aapsLogger.debug(LTag.WEAR, "WatchFacePush: no face chosen yet, waiting for the phone")
            delay(FIRST_CHOICE_GRACE_MS)
        }
        // A matching token means this exact face was already put in place once — if it is missing
        // now, the user removed it: respect that
        val token = embeddedFaceToken(selectedFace) ?: return
        if (sp.getString(KEY_SYNCED_FACE, "") == token) {
            reportStatus()
            return
        }
        aapsLogger.debug(LTag.WEAR, "WatchFacePush: embedded face changed, syncing")
        // Catches all its failures internally — this path runs on app start and must never take
        // the app down; on failure the version is not marked synced, so the next start retries.
        // Reports to the phone when done, whatever the outcome.
        installOrUpdate()
    }

    /**
     * Tells the phone what this watch can do and which face it holds.
     *
     * The phone shows the face choice only on a watch that says it has Watch Face Push, and can
     * warn when the watch still holds the other face - after a reinstall the watch starts with the
     * default and only catches up once the preferences reach it. Sent after the startup sync, after
     * every install, and in reply to the preferences, so the phone's picture is never older than
     * the last exchange. Never throws: a failed listing reports "supported, face unknown".
     */
    suspend fun reportStatus() = withContext(Dispatchers.IO) {
        val status = if (!isSupported()) {
            EventData.WatchFacePushStatus(supported = false)
        } else try {
            val packageName = listOwnFaces(createManager()).installedWatchFaceDetails.firstOrNull()?.packageName
            EventData.WatchFacePushStatus(
                supported = true,
                installedFace = PushedFace.entries.firstOrNull { it.packageName(context.packageName) == packageName }?.id
            )
        } catch (e: Exception) {
            aapsLogger.error(LTag.WEAR, "WatchFacePush: status listing failed", e)
            EventData.WatchFacePushStatus(supported = true)
        }
        rxBus.send(EventWearToMobile(status))
    }

    /**
     * One install at a time. Three callers can want one at the same moment - the startup sync, the
     * preferences arriving from the phone, and the main menu - and measured on a Galaxy Watch 5 two
     * of them did run side by side: both saw an empty slot table, both called add, the first won with
     * the face the phone had just switched away from, and the second failed with "limit of watch
     * faces reached". Serialised, the second one finds the slot and updates it instead.
     */
    private val installLock = Mutex()

    /**
     * Installs the selected face, or updates this app's slot with it if the slot exists - whether
     * it holds an older build of the same face or the other face.
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
        val installed = installLock.withLock {
            // Read under the lock: a choice that arrived while another install was running is
            // what this one must install, not what was selected when it was asked for
            val face = selectedFace
            val apkFile = File(context.cacheDir, "wfpush_${face.id}.apk")
            try {
                val manager = createManager()
                val token = embeddedFaceToken(face) ?: return@withLock false
                // Two callers wanting the same face - the startup sync and the preferences that
                // arrived during it - now run one after the other, and the second has nothing to
                // do. Not for a menu tap: that is the wearer asking for a face they removed, and
                // the token is still stored from before the removal.
                if (!activate && sp.getString(KEY_SYNCED_FACE, "") == token) {
                    aapsLogger.debug(LTag.WEAR, "WatchFacePush: face ${face.id} already in place")
                    return@withLock true
                }
                context.assets.open(face.assetApk).use { input ->
                    apkFile.outputStream().use { input.copyTo(it) }
                }
                // Any slot of ours, not only one holding this face: with a single slot per app the
                // other face has to give way, and the API updates a slot with a different package.
                // Measured on a Galaxy Watch 5: the updated slot stays the active face, so the
                // wrist follows the phone's choice by itself, without any activation permission.
                val existingSlotId = ownSlotId(manager)
                val action = ParcelFileDescriptor.open(apkFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    if (existingSlotId == null) {
                        manager.addWatchFace(fd, token)
                        "installed"
                    } else try {
                        manager.updateWatchFace(existingSlotId, fd, token)
                        "updated"
                    } catch (e: WatchFacePushManager.UpdateWatchFaceException) {
                        // A slot the runtime still lists but no longer accepts - left behind by an
                        // uninstall of the face from the watch settings. Adding is what revives it.
                        if (e.errorCode != WatchFacePushManager.UpdateWatchFaceException.ERROR_INVALID_SLOT_ID) throw e
                        aapsLogger.debug(LTag.WEAR, "WatchFacePush: slot $existingSlotId is stale, adding instead")
                        manager.addWatchFace(fd, token)
                        "installed over a stale slot"
                    }
                }
                if (activate) {
                    installedFaceSlotId(manager)?.let { slotId -> setActive(manager, slotId) }
                }
                sp.putString(KEY_SYNCED_FACE, token)
                sp.putBoolean(KEY_FACE_INSTALLED, true)
                aapsLogger.debug(LTag.WEAR, "WatchFacePush: face ${face.id} $action")
                true
            } catch (e: Exception) {
                // Broad on purpose: covers the typed Add/Update/List exceptions, IO on the embedded
                // asset AND the raw RuntimeExceptions the manager surfaces across its IPC
                aapsLogger.error(LTag.WEAR, "WatchFacePush: install/update of face ${face.id} failed", e)
                false
            } finally {
                apkFile.delete()
            }
        }
        // Whatever happened, the phone shows the truth rather than its own wish
        reportStatus()
        installed
    }

    /** Whether the selected face is the currently shown watch face */
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

    private suspend fun listOwnFaces(manager: WatchFacePushManager): WatchFacePushManager.ListWatchFacesResponse {
        val response = manager.listWatchFaces()
        // How many faces one app may push is not stated in the API documentation - only that the
        // number is limited - so it is logged here, where the response is already in hand. Measured
        // as one on a Galaxy Watch 4, which is why the two embedded faces share this slot.
        aapsLogger.debug(
            LTag.WEAR,
            "WatchFacePush: slots used=${response.installedWatchFaceDetails.size} remaining=${response.remainingSlotCount}" +
                " packages=${response.installedWatchFaceDetails.joinToString { it.packageName }}"
        )
        return response
    }

    /** The slot holding the selected face, or null */
    private suspend fun installedFaceSlotId(manager: WatchFacePushManager): String? =
        listOwnFaces(manager).installedWatchFaceDetails
            .firstOrNull { it.packageName == facePackageName }
            ?.slotId

    /** Any slot this app holds, whichever face is in it, or null. The list only ever contains our own faces. */
    private suspend fun ownSlotId(manager: WatchFacePushManager): String? =
        listOwnFaces(manager).installedWatchFaceDetails.firstOrNull()?.slotId

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

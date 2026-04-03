package app.aaps.plugins.source.notificationreader

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.plugins.source.NotificationReaderPlugin
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class NotificationCollectorService : NotificationListenerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var notificationReaderPlugin: NotificationReaderPlugin
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var profileFunction: ProfileFunction

    private var parser: NotificationParser? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastInsertTimestamp = 0L

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        parser = NotificationParser(notificationReaderPlugin.packageConfig)
        aapsLogger.debug(LTag.BGSOURCE, "NotificationCollectorService created")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
        aapsLogger.debug(LTag.BGSOURCE, "NotificationCollectorService destroyed")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (!notificationReaderPlugin.packageConfig.isSupportedPackage(packageName)) return
        if (!notificationReaderPlugin.isEnabled()) return

        aapsLogger.debug(LTag.BGSOURCE, "Notification from: $packageName")
        processNotification(sbn.notification, packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    private fun processNotification(notification: Notification?, packageName: String) {
        if (notification == null) return

        val texts = extractTexts(notification)
        if (texts.isEmpty()) {
            aapsLogger.debug(LTag.BGSOURCE, "No text in notification from $packageName")
            return
        }

        aapsLogger.debug(LTag.BGSOURCE, "Extracted texts: $texts")

        val useMgdl = profileFunction.getUnits() == GlucoseUnit.MGDL
        val result = parser?.extractGlucose(texts, packageName, useMgdl) ?: return

        aapsLogger.debug(LTag.BGSOURCE, "Glucose: ${result.glucoseMgdl} mg/dL from $packageName (${result.sourceSensor})")

        val now = System.currentTimeMillis()
        if (now - lastInsertTimestamp < MIN_INSERT_INTERVAL_MS) {
            aapsLogger.debug(LTag.BGSOURCE, "Skipping duplicate notification (${now - lastInsertTimestamp}ms since last insert)")
            return
        }
        lastInsertTimestamp = now

        val gv = GV(
            timestamp = now,
            value = result.glucoseMgdl.toDouble(),
            raw = null,
            noise = null,
            trendArrow = TrendArrow.NONE,
            sourceSensor = result.sourceSensor
        )

        scope.launch {
            try {
                persistenceLayer.insertCgmSourceData(Sources.NotificationReader, listOf(gv), emptyList(), null)
            } catch (e: Exception) {
                aapsLogger.error(LTag.BGSOURCE, "Error inserting glucose data", e)
            }
        }
    }

    /**
     * Extract visible text from notification.
     * Tries standard extras first (modern apps), falls back to RemoteViews inflation (legacy).
     */
    private fun extractTexts(notification: Notification): List<String> {
        val extras = notification.extras ?: return emptyList()

        // Modern notifications: extract from standard extras
        val texts = buildList {
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
        }
        if (texts.isNotEmpty()) return texts

        // Legacy: inflate custom RemoteViews
        @Suppress("DEPRECATION")
        notification.contentView?.let { remoteViews ->
            try {
                val applied = remoteViews.apply(this, null)
                val root = applied.rootView as? ViewGroup ?: return@let
                val inflated = root.collectVisibleText()
                if (inflated.isNotEmpty()) return inflated
            } catch (e: Exception) {
                aapsLogger.debug(LTag.BGSOURCE, "RemoteViews inflation failed: ${e.message}")
            }
        }

        return emptyList()
    }
}

private const val MIN_INSERT_INTERVAL_MS = 30_000L

/**
 * Recursively collect text from all visible TextViews in a ViewGroup hierarchy.
 */
private fun ViewGroup.collectVisibleText(): List<String> = buildList {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        if (child.visibility != View.VISIBLE) continue
        when (child) {
            is TextView  -> child.text?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
            is ViewGroup -> addAll(child.collectVisibleText())
        }
    }
}

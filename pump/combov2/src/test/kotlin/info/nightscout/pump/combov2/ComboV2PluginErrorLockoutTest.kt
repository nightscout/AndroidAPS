package info.nightscout.pump.combov2

import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import info.nightscout.comboctl.parser.AlertScreenContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * The lockout after the Combo reports an error (#5263, #4479): the card that names the error must
 * stay, the "cannot connect" hint is posted once and goes away with the lockout, and every error
 * ends its lockout after the timeout, not only the first one.
 */
class ComboV2PluginErrorLockoutTest {

    private data class Post(val id: NotificationId, val level: NotificationLevel, val validityCheck: (() -> Boolean)?)

    private class RecordingNotificationManager : NotificationManager {

        val posts = mutableListOf<Post>()
        val dismissed = mutableListOf<NotificationId>()

        override val notifications: StateFlow<List<AapsNotification>> = MutableStateFlow(emptyList())

        override fun cleanUp() {}

        override fun post(
            id: NotificationId, text: String, level: NotificationLevel, validMinutes: Int, sound: AlarmSound?,
            actions: List<NotificationAction>, validityCheck: (() -> Boolean)?
        ): NotificationHandle = record(id, level, validityCheck)

        override fun post(
            id: NotificationId, text: String, level: NotificationLevel, date: Long, validTo: Long, sound: AlarmSound?,
            actions: List<NotificationAction>, validityCheck: (() -> Boolean)?
        ): NotificationHandle = record(id, level, validityCheck)

        override fun post(
            id: NotificationId, textRef: TextRef, level: NotificationLevel, validMinutes: Int, date: Long, validTo: Long,
            sound: AlarmSound?, actions: List<NotificationAction>, validityCheck: (() -> Boolean)?
        ): NotificationHandle = record(id, level, validityCheck)

        override fun dismiss(id: NotificationId) {
            dismissed += id
        }

        override fun dismiss(handle: NotificationHandle) {}

        override fun muteAllAlarms() {}

        private fun record(id: NotificationId, level: NotificationLevel, validityCheck: (() -> Boolean)?): NotificationHandle {
            posts += Post(id, level, validityCheck)
            return NotificationHandle(posts.size)
        }
    }

    private val notificationManager = RecordingNotificationManager()
    private lateinit var plugin: ComboV2Plugin

    private val occlusion = AlertScreenContent.Error(4, AlertScreenContent.AlertScreenState.ERROR_TEXT)

    @BeforeEach
    fun setUp() {
        val rh: ResourceHelper = mock { on { gs(any<Int>()) } doReturn "text" }
        plugin = ComboV2Plugin(
            aapsLogger = mock(),
            rh = rh,
            preferences = mock(),
            commandQueue = mock(),
            context = mock(),
            rxBus = mock(),
            sp = mock(),
            pumpSync = mock(),
            dateUtil = mock(),
            notificationManager = notificationManager,
            config = mock(),
            pumpEnactResultProvider = { mock() },
            bolusProgressData = mock(),
            concentrationHelper = mock()
        )
    }

    @Test
    fun errorPostsTheNamedCardAndOneLockoutHint() {
        plugin.notifyAboutComboAlert(occlusion)

        assertThat(notificationManager.posts.map { it.id to it.level }).containsExactly(
            NotificationId.COMBO_PUMP_ALARM to NotificationLevel.IMPORTANT,
            NotificationId.COMBO_PUMP_ERROR_LOCKOUT to NotificationLevel.NORMAL
        ).inOrder()
        assertThat(plugin.pumpErrorObserved).isTrue()
    }

    /** The queue tries to connect about once a second. Each refused try used to replace the error card. */
    @Test
    fun refusedConnectsPostNothing() {
        plugin.notifyAboutComboAlert(occlusion)
        notificationManager.posts.clear()

        repeat(10) { plugin.connect("test") }

        assertThat(notificationManager.posts).isEmpty()
    }

    @Test
    fun lockoutHintGoesAwayWhenTheFlagIsCleared() {
        plugin.notifyAboutComboAlert(occlusion)
        val hint = notificationManager.posts.single { it.id == NotificationId.COMBO_PUMP_ERROR_LOCKOUT }
        assertThat(hint.validityCheck?.invoke()).isTrue()

        plugin.clearPumpErrorObservedFlag()

        assertThat(hint.validityCheck?.invoke()).isFalse()
        assertThat(notificationManager.dismissed).contains(NotificationId.COMBO_PUMP_ERROR_LOCKOUT)
    }

    @Test
    fun warningDoesNotLockThePump() {
        plugin.notifyAboutComboAlert(AlertScreenContent.Warning(1, AlertScreenContent.AlertScreenState.TO_SNOOZE))

        assertThat(notificationManager.posts.map { it.id to it.level })
            .containsExactly(NotificationId.COMBO_PUMP_ALARM to NotificationLevel.NORMAL)
        assertThat(plugin.pumpErrorObserved).isFalse()
    }

    /** The timer of the first error stayed in its field, so a second error started no timer (#4479). */
    @Test
    fun everyErrorEndsItsLockoutAfterTheTimeout() {
        plugin.pumpErrorTimeoutMillis = 50

        plugin.notifyAboutComboAlert(occlusion)
        assertThat(waitUntilUnlocked()).isTrue()

        plugin.notifyAboutComboAlert(occlusion)
        assertThat(plugin.pumpErrorObserved).isTrue()
        assertThat(waitUntilUnlocked()).isTrue()
    }

    private fun waitUntilUnlocked(): Boolean {
        val deadline = System.currentTimeMillis() + 5000
        while (plugin.pumpErrorObserved && System.currentTimeMillis() < deadline) Thread.sleep(10)
        return !plugin.pumpErrorObserved
    }
}

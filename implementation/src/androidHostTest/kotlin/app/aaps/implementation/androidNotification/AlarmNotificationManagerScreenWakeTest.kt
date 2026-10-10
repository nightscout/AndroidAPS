package app.aaps.implementation.androidNotification

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers `AlarmNotificationManager.scheduleScreenWakeAndLaunch`, the step that wakes the screen for a
 * background alarm.
 *
 * It uses `AlarmManager.setAlarmClock`, which needs SCHEDULE_EXACT_ALARM from targetSdk 31 on unless AAPS
 * is excluded from battery optimization. Android 14 and later deny the permission by default on a new
 * install, and aapsclient builds did not even declare it. The call used to be made unguarded, so the
 * `SecurityException` went up into whoever raised the alarm. The alarm had already started ringing by
 * then - only the screen wake is lost - so the right answer is to skip that step, not to fail.
 */
class AlarmNotificationManagerScreenWakeTest {

    private val alarmManager: AlarmManager = mock()

    private fun sut(): AlarmNotificationManager {
        val context: Context = mock()
        whenever(context.getSystemService(Context.ALARM_SERVICE)).thenReturn(alarmManager)
        val uiInteraction: UiInteraction = mock()
        whenever(uiInteraction.mainActivity).thenReturn(Activity::class)
        return AlarmNotificationManager(
            context = context,
            aapsLogger = mock<AAPSLogger>(),
            preferences = mock<Preferences>(),
            iconsProvider = mock<IconsProvider>(),
            uiInteractionProvider = { uiInteraction },
            alarmSoundPlayer = mock<AlarmSoundPlayer>(),
            rh = mock<ResourceHelper>()
        )
    }

    @Test
    fun withoutExactAlarmsTheScreenWakeIsSkipped() {
        whenever(alarmManager.canScheduleExactAlarms()).thenReturn(false)

        sut().scheduleScreenWakeAndLaunch(mock())

        verify(alarmManager, never()).setAlarmClock(anyOrNull(), anyOrNull())
    }

    @Test
    fun aPermissionRevokedDuringTheCallDoesNotReachTheCaller() {
        whenever(alarmManager.canScheduleExactAlarms()).thenReturn(true)
        doThrow(SecurityException("revoked")).whenever(alarmManager).setAlarmClock(anyOrNull(), anyOrNull())

        // Must return normally: the alarm is already ringing, only the screen wake is lost.
        sut().scheduleScreenWakeAndLaunch(mock())

        verify(alarmManager).setAlarmClock(anyOrNull(), anyOrNull())
    }
}

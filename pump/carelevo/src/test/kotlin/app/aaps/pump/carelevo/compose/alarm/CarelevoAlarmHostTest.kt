package app.aaps.pump.carelevo.compose.alarm

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.domain.model.alarm.CarelevoAlarmInfo
import app.aaps.pump.carelevo.domain.type.AlarmCause
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tests for the alarm host file — its cause→[CarelevoAlarmUiModel] mapping and the rendering that
 * mapping drives through [CarelevoAlarmScreen].
 *
 * ## What is NOT covered, and why
 * The [CarelevoAlarmHost] composable body itself is NOT rendered here. It resolves its ViewModel
 * with a hard-coded `hiltViewModel()` call and takes no ViewModel parameter, so there is no seam to
 * inject a test double through. That call is not defeatable from a plain Robolectric test:
 * `hiltViewModel()` (androidx.hilt 1.4.0) unconditionally builds a `HiltViewModelFactory` from
 * `LocalContext` *before* it consults the ViewModelStore, and that factory walks the context chain
 * to a `ComponentActivity` and demands an `@AndroidEntryPoint` entry point on it. The plain
 * `ComponentActivity` behind `createComposeRule()` is not one, so composing the host throws
 * regardless of what `LocalViewModelStoreOwner` provides. Rendering it would need the full Hilt test
 * graph (`HiltTestApplication` + a Hilt runner + every real collaborator of `CarelevoAlarmViewModel`)
 * — a build-level change, not a test-level one.
 *
 * So the host's effect wiring (the `alarmHostActive` `DisposableEffect`, the notifier→
 * `loadActiveAlarms` bridge, the event/snackbar/StartAlarm `LaunchedEffect`s and the dismissed-id
 * bookkeeping) is out of reach here. Its two pure helpers — `buildAlarmUiModel` and
 * `buildDescArgsFor` — carry the file's real branching, and they are reachable: private top-level
 * functions compile to static methods on `CarelevoAlarmHostKt`, so they are driven reflectively
 * against a real Robolectric [Context] with real string resources. Reflection into the host's own
 * privates matches the approach the sibling `CarelevoAlarmViewModelTest` already takes.
 *
 * The render cases feed a model built by the *real* helper into the screen, so the
 * mapping→UI contract is asserted end-to-end rather than against a hand-written model.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CarelevoAlarmHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** The host reads only [IconsProvider.getIcon]; a fake keeps the icon value assertable. */
    private class FakeIconsProvider(private val icon: Int) : IconsProvider {

        override fun getIcon(): Int = icon
        override fun getNotificationIcon(): Int = icon
    }

    private val appIcon = R.drawable.ic_carelevo_128

    private fun alarm(
        cause: AlarmCause,
        value: Int? = null,
        id: String = "alarm-1"
    ): CarelevoAlarmInfo = CarelevoAlarmInfo(
        alarmId = id,
        alarmType = cause.alarmType,
        cause = cause,
        value = value,
        createdAt = "2026-07-16T10:00:00",
        updatedAt = "2026-07-16T10:00:00",
        isAcknowledged = false
    )

    // ---- reflection into the host file's private top-level helpers -----------------------------

    private val hostFileClass: Class<*> = Class.forName("app.aaps.pump.carelevo.compose.alarm.CarelevoAlarmHostKt")

    private fun buildAlarmUiModel(alarm: CarelevoAlarmInfo): CarelevoAlarmUiModel {
        val method = hostFileClass.getDeclaredMethod(
            "buildAlarmUiModel",
            Context::class.java,
            IconsProvider::class.java,
            CarelevoAlarmInfo::class.java
        )
        method.isAccessible = true
        return method.invoke(null, context, FakeIconsProvider(appIcon), alarm) as CarelevoAlarmUiModel
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildDescArgsFor(alarm: CarelevoAlarmInfo): List<String> {
        val method = hostFileClass.getDeclaredMethod(
            "buildDescArgsFor",
            Context::class.java,
            CarelevoAlarmInfo::class.java
        )
        method.isAccessible = true
        return method.invoke(null, context, alarm) as List<String>
    }

    // ---- buildDescArgsFor: insulin causes ------------------------------------------------------

    @Test
    fun descArgs_noticeLowInsulin_passesTheRemainingUnits() {
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_NOTICE_LOW_INSULIN, value = 20)))
            .containsExactly("20")
    }

    @Test
    fun descArgs_noticeLowInsulin_nullValueFallsBackToZero() {
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_NOTICE_LOW_INSULIN, value = null)))
            .containsExactly("0")
    }

    @Test
    fun descArgs_alertOutOfInsulin_sharesTheLowInsulinBranch() {
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN, value = 5)))
            .containsExactly("5")
    }

    // ---- buildDescArgsFor: patch expired (hours -> days + hours) -------------------------------

    @Test
    fun descArgs_noticePatchExpired_splitsTotalHoursIntoDaysAndHours() {
        // 50h -> 2 days, 2 hours
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_NOTICE_PATCH_EXPIRED, value = 50)))
            .containsExactly("2", "2")
            .inOrder()
    }

    @Test
    fun descArgs_noticePatchExpired_wholeDaysReportZeroHours() {
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_NOTICE_PATCH_EXPIRED, value = 48)))
            .containsExactly("2", "0")
            .inOrder()
    }

    @Test
    fun descArgs_noticePatchExpired_nullValueFallsBackToZeros() {
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_NOTICE_PATCH_EXPIRED, value = null)))
            .containsExactly("0", "0")
            .inOrder()
    }

    // ---- buildDescArgsFor: BG check (minutes -> localized duration) ----------------------------

    @Test
    fun descArgs_noticeBgCheck_formatsHoursAndMinutes() {
        // 150min -> 2h 30m: both parts non-zero -> the combined hour+minute template.
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 150)))
            .containsExactly(context.getString(R.string.common_label_unit_value_duration_hour_and_minute, 2, 30))
    }

    @Test
    fun descArgs_noticeBgCheck_wholeHoursDropTheMinutePart() {
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 120)))
            .containsExactly(context.getString(R.string.common_label_unit_value_duration_hour, 2))
    }

    @Test
    fun descArgs_noticeBgCheck_underAnHourUsesTheMinuteTemplate() {
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 45)))
            .containsExactly(context.getString(R.string.common_label_unit_value_minute, 45))
    }

    @Test
    fun descArgs_noticeBgCheck_nullValueRendersZeroMinutes() {
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = null)))
            .containsExactly(context.getString(R.string.common_label_unit_value_minute, 0))
    }

    // ---- buildDescArgsFor: causes that take no arguments ---------------------------------------

    @Test
    fun descArgs_causeWithoutPlaceholders_returnsNoArgs() {
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_NOTICE_LGS_START))).isEmpty()
    }

    @Test
    fun descArgs_warningCause_returnsNoArgs() {
        assertThat(buildDescArgsFor(alarm(AlarmCause.ALARM_WARNING_PUMP_CLOGGED))).isEmpty()
    }

    // ---- buildAlarmUiModel ---------------------------------------------------------------------

    @Test
    fun uiModel_noticeLowInsulin_mapsEveryField() {
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_LOW_INSULIN, value = 20))

        assertThat(model.appIcon).isEqualTo(appIcon)
        assertThat(model.title).isEqualTo(context.getString(R.string.alarm_feat_title_notice_low_insulin))
        assertThat(model.content).isEqualTo(context.getString(R.string.alarm_feat_desc_notice_low_insulin, "20"))
        assertThat(model.primaryButtonText).isEqualTo(context.getString(R.string.common_btn_ok))
        assertThat(model.muteButtonText).isEqualTo(context.getString(CoreUiR.string.mute))
        assertThat(model.mute5minButtonText).isEqualTo(context.getString(CoreUiR.string.mute5min))
    }

    @Test
    fun uiModel_noticeLowInsulin_contentCarriesTheRemainingUnits() {
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_LOW_INSULIN, value = 20))

        assertThat(model.content).contains("20")
    }

    @Test
    fun uiModel_causeWithoutDescription_producesBlankContent() {
        // ALARM_NOTICE_ATTACH_PATCH_CHECK maps to a null screen description -> the `?: ""` fallback.
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK))

        assertThat(model.content).isEmpty()
        assertThat(model.title).isEqualTo(context.getString(R.string.alarm_feat_title_notice_check_patch))
    }

    @Test
    fun uiModel_warningCauseWithoutDescription_producesBlankContent() {
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_WARNING_BLE_NOT_CONNECTED))

        assertThat(model.content).isEmpty()
        assertThat(model.title).isEqualTo(context.getString(R.string.alarm_feat_title_warning_not_connected_ble))
        assertThat(model.primaryButtonText).isEqualTo(context.getString(R.string.alarm_feat_btn_patch_force_discard))
    }

    @Test
    fun uiModel_descriptionWithoutArgs_usesThePlainString() {
        // Empty descArgs -> getString(resId) with no formatting applied.
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_LGS_START))

        assertThat(model.content).isEqualTo(context.getString(R.string.alarm_feat_desc_notice_lgs_started))
    }

    @Test
    fun uiModel_noticePatchExpired_formatsDaysAndHoursIntoTheDescription() {
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_PATCH_EXPIRED, value = 50))

        assertThat(model.content)
            .isEqualTo(context.getString(R.string.alarm_feat_desc_notice_expired_patch, "2", "2"))
    }

    @Test
    fun uiModel_noticeBgCheck_formatsTheElapsedDurationIntoTheDescription() {
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 150))

        val duration = context.getString(R.string.common_label_unit_value_duration_hour_and_minute, 2, 30)
        assertThat(model.content).isEqualTo(context.getString(R.string.alarm_feat_desc_notice_check_bg, duration))
    }

    @Test
    fun uiModel_alertOutOfInsulin_usesTheAlertTierStrings() {
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_ALERT_OUT_OF_INSULIN, value = 5))

        assertThat(model.title).isEqualTo(context.getString(R.string.alarm_feat_title_alert_low_insulin))
        assertThat(model.content).isEqualTo(context.getString(R.string.alarm_feat_desc_alert_low_insulin, "5"))
        assertThat(model.primaryButtonText).isEqualTo(context.getString(R.string.common_btn_ok))
    }

    @Test
    fun uiModel_warningLowBattery_usesTheForceDiscardButton() {
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_WARNING_LOW_BATTERY))

        assertThat(model.title).isEqualTo(context.getString(R.string.alarm_feat_title_warning_low_battery))
        assertThat(model.primaryButtonText).isEqualTo(context.getString(R.string.alarm_feat_btn_patch_force_discard))
    }

    @Test
    fun uiModel_unknownCause_stillMapsToTheUnknownStrings() {
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_UNKNOWN))

        assertThat(model.title).isEqualTo(context.getString(R.string.alarm_feat_title_notice_unknown))
        assertThat(model.content).isEqualTo(context.getString(R.string.alarm_feat_desc_unknown))
    }

    // ---- the built model rendered through the screen the host drives ---------------------------

    @Test
    fun render_builtModel_showsTitleAndAllThreeButtons() {
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_LOW_INSULIN, value = 20))

        compose.setContent {
            MaterialTheme {
                CarelevoAlarmScreen(
                    alarm = model,
                    onPrimaryClick = {},
                    onMuteClick = {},
                    onMute5MinClick = {}
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.alarm_feat_title_notice_low_insulin)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_btn_ok)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(CoreUiR.string.mute)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(CoreUiR.string.mute5min)).assertIsDisplayed()
    }

    @Test
    fun render_builtModel_showsTheFormattedDescription() {
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_BG_CHECK, value = 45))

        compose.setContent {
            MaterialTheme {
                CarelevoAlarmScreen(
                    alarm = model,
                    onPrimaryClick = {},
                    onMuteClick = {},
                    onMute5MinClick = {}
                )
            }
        }

        // The screen parses the description as HTML, so match on the plain-text head of it.
        val minutes = context.getString(R.string.common_label_unit_value_minute, 45)
        compose.onNodeWithText(minutes, substring = true).assertIsDisplayed()
    }

    @Test
    fun render_nullAlarm_showsNothing() {
        compose.setContent {
            MaterialTheme {
                CarelevoAlarmScreen(
                    alarm = null,
                    onPrimaryClick = {},
                    onMuteClick = {},
                    onMute5MinClick = {}
                )
            }
        }

        compose.onNodeWithText(context.getString(CoreUiR.string.mute)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.common_btn_ok)).assertDoesNotExist()
    }

    @Test
    fun render_blankContent_omitsTheDescriptionButKeepsTitleAndButtons() {
        // ALARM_NOTICE_ATTACH_PATCH_CHECK has no screen description -> content is blank, so the
        // screen's `if (alarm.content.isNotBlank())` guard must skip the description Text entirely.
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_ATTACH_PATCH_CHECK))
        assertThat(model.content).isEmpty()

        compose.setContent {
            MaterialTheme {
                CarelevoAlarmScreen(
                    alarm = model,
                    onPrimaryClick = {},
                    onMuteClick = {},
                    onMute5MinClick = {}
                )
            }
        }

        // Only the title and the three buttons survive; no description node is emitted.
        compose.onAllNodesWithText(context.getString(R.string.alarm_feat_title_notice_check_patch))
            .assertCountEquals(1)
        compose.onNodeWithText(context.getString(R.string.alarm_feat_title_notice_check_patch)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_btn_ok)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(CoreUiR.string.mute)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(CoreUiR.string.mute5min)).assertIsDisplayed()
    }

    @Test
    fun render_primaryButton_firesTheHostsClearCallback() {
        var primaryClicks = 0
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_LOW_INSULIN, value = 20))

        compose.setContent {
            MaterialTheme {
                CarelevoAlarmScreen(
                    alarm = model,
                    onPrimaryClick = { primaryClicks++ },
                    onMuteClick = {},
                    onMute5MinClick = {}
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.common_btn_ok)).performClick()

        assertThat(primaryClicks).isEqualTo(1)
    }

    @Test
    fun render_muteButton_firesOnlyTheMuteCallback() {
        var muteClicks = 0
        var mute5MinClicks = 0
        var primaryClicks = 0
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_LOW_INSULIN, value = 20))

        compose.setContent {
            MaterialTheme {
                CarelevoAlarmScreen(
                    alarm = model,
                    onPrimaryClick = { primaryClicks++ },
                    onMuteClick = { muteClicks++ },
                    onMute5MinClick = { mute5MinClicks++ }
                )
            }
        }

        compose.onNodeWithText(context.getString(CoreUiR.string.mute)).performClick()

        assertThat(muteClicks).isEqualTo(1)
        assertThat(mute5MinClicks).isEqualTo(0)
        assertThat(primaryClicks).isEqualTo(0)
    }

    @Test
    fun render_mute5MinButton_firesOnlyTheMute5MinCallback() {
        var muteClicks = 0
        var mute5MinClicks = 0
        val model = buildAlarmUiModel(alarm(AlarmCause.ALARM_NOTICE_LOW_INSULIN, value = 20))

        compose.setContent {
            MaterialTheme {
                CarelevoAlarmScreen(
                    alarm = model,
                    onPrimaryClick = {},
                    onMuteClick = { muteClicks++ },
                    onMute5MinClick = { mute5MinClicks++ }
                )
            }
        }

        compose.onNodeWithText(context.getString(CoreUiR.string.mute5min)).performClick()

        assertThat(mute5MinClicks).isEqualTo(1)
        assertThat(muteClicks).isEqualTo(0)
    }
}

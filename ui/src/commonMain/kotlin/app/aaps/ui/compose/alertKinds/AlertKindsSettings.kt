package app.aaps.ui.compose.alertKinds

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertGroup
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.AlertPlatform
import app.aaps.core.interfaces.alerts.AlertSection
import app.aaps.core.interfaces.alerts.AlertSetting
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.AlertSound
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarmRules
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.ui.compose.bgAlarms.BgAlarmsScreen
import app.aaps.ui.compose.bgAlarms.BgUnits
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.withCompose
import app.aaps.core.ui.compose.ComposeScreenContent
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.LocalMasterReachable
import app.aaps.ui.keys.UiIntentKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.StateFlow

/** The alert kinds in the settings and in the notification sheet (`_docs/ALARMS.md`, section 6). */
interface AlertKindsSettings {

    /** The settings item that opens the list, or null where there are no alerts. */
    val settingsItem: PreferenceItem?

    /** The user's choices on this phone, by group or kind id. Those that are not Ring are counted ([silentOrOff]). */
    val choices: StateFlow<Map<String, AlertChoice>>

    /** The kinds paused on this phone, by kind id, with the end of the pause. Counted with the silent kinds. */
    val pauses: StateFlow<Map<String, Long>>

    /** Settings of the alarms that only this platform has, shown after the alert kinds. Android: display over other apps. */
    val platformItems: List<PreferenceItem> get() = emptyList()
}

/** Bound by each platform next to its [Alerts]. */
@OptIn(ExperimentalUuidApi::class)
@SingleIn(AppScope::class)
@Inject
class AlertKindsSettingsImpl(
    private val alerts: Alerts,
    // Only for whether the phone can vibrate: Vibrate is offered only where it can be kept
    private val alertPlatform: AlertPlatform,
    private val catalogues: Set<AlertKindCatalogue>,
    private val bgAlarmRules: BgAlarmRules,
    private val preferences: Preferences,
    private val profileUtil: ProfileUtil,
    private val decimalFormatter: DecimalFormatter,
    private val dateUtil: DateUtil,
    private val config: Config
) : AlertKindsSettings {

    override val choices: StateFlow<Map<String, AlertChoice>> get() = alerts.choices
    override val pauses: StateFlow<Map<String, Long>> get() = alerts.pauses

    override val settingsItem: PreferenceItem = UiIntentKey.AlertKinds.withCompose(
        ComposeScreenContent { onBack ->
            // A part opens in place: back returns to the list of parts, not to the list of settings
            var open by remember { mutableStateOf<AlertSection?>(null) }
            val listState = rememberLazyListState()
            NavigationBackHandler(
                state = rememberNavigationEventState(NavigationEventInfo.None),
                isBackEnabled = open != null,
                onBackCompleted = { open = null }
            )
            val current by alerts.choices.collectAsState()
            val pauses by alerts.pauses.collectAsState()
            val sections = alertSettingsSections(catalogues, current, alertPlatform.canVibrate)
            val section = sections.firstOrNull { it.section == open }
            if (section == null) AlertKindsScreen(
                sections = sections,
                vibrates = alertPlatform.canVibrate,
                // The same count as the line in the notification sheet
                silentOrOff = silentOrOff(current, pauses),
                sectionSilentOrOff = { sectionSilentOrOff(it, current, pauses) },
                onOpen = { open = it },
                onBack = onBack,
                listState = listState
            )
            // Each part its own list, at its top when it opens
            else key(section.section) {
                val sounds by alerts.sounds.collectAsState()
                val back = { open = null }
                if (section.section == AlertSection.BG) BgAlarmsContent(section, sounds, pauses, back)
                else AlertSectionScreen(
                    section = section,
                    onChoice = { setting, choice -> alerts.setChoice(setting, choice) },
                    onClear = { kind -> alerts.clearChoice(kind) },
                    sounds = sounds,
                    onSound = { setting, sound -> alerts.setSound(setting, sound) },
                    // As an alarm: the sound the user picks it for
                    onTest = { kind, vibrate -> alerts.test(kind, AlertSeverity.CRITICAL, vibrate) },
                    onBack = back
                )
            }
        }
    )

    /** The BG alarms: their rules and thresholds next to how this phone rings them. */
    @Composable
    private fun BgAlarmsContent(section: AlertSettingsSection, sounds: Map<String, AlertSound>, pauses: Map<String, Long>, onBack: () -> Unit) {
        val rules by bgAlarmRules.rules.collectAsState()
        // In the user's units, as every unit-double reads
        val urgentLow by preferences.observe(UnitDoubleKey.AlertUrgentLow).collectAsState()
        val staleMinutes by preferences.observe(IntKey.AlertsStaleDataThreshold).collectAsState()
        val stale = IntKey.AlertsStaleDataThreshold
        BgAlarmsScreen(
            section = section,
            rules = rules,
            // One set of rules and one urgent low for every phone (step 2f): a client edits them only as an Admin and
            // while the master can be reached, as every synced setting (the master checks the role again)
            readOnly = config.AAPSCLIENT && (!LocalMasterReachable.current || !LocalClientRole.current.allows(ClientRole.Admin)),
            units = BgUnits(
                label = profileUtil.unitLabel,
                format = { profileUtil.fromMgdlToStringWithUnits(it) },
                toUnits = { profileUtil.fromMgdlToStringInUnits(it) },
                // Some keyboards give a decimal comma
                toMgdl = { text -> text.trim().replace(',', '.').toDoubleOrNull()?.let { profileUtil.convertToMgdl(it, profileUtil.units) } },
                // A rate in mmol/l needs two decimals: 3 mg/dl per minute is 0.17 mmol/l
                rateToUnits = { rate ->
                    val value = profileUtil.fromMgdlToUnits(rate)
                    if (profileUtil.units == GlucoseUnit.MGDL) decimalFormatter.to1Decimal(value) else decimalFormatter.to2Decimal(value)
                }
            ),
            newId = { Uuid.random().toString() },
            onSave = { bgAlarmRules.save(it) },
            urgentLowMgdl = profileUtil.convertToMgdlDetect(urgentLow),
            // Stored in mg/dl: a number that does not depend on the units the user reads it in
            onUrgentLow = { preferences.put(UnitDoubleKey.AlertUrgentLow, it) },
            staleMinutes = staleMinutes,
            // In simple mode it keeps its default, as its setting did
            staleMinutesRange = (stale.min..stale.max).takeUnless { preferences.simpleMode && stale.defaultedBySM },
            onStaleMinutes = { preferences.put(stale, it) },
            pauses = pauses,
            timeText = { dateUtil.timeString(it) },
            onPause = { kinds, minutes -> alerts.pause(kinds, dateUtil.now() + minutes * 60_000L) },
            onResume = { kinds -> alerts.resume(kinds) },
            onRuleTest = { kind, severity -> alerts.test(kind, severity) },
            onChoice = { setting, choice -> alerts.setChoice(setting, choice) },
            onClear = { kind -> alerts.clearChoice(kind) },
            sounds = sounds,
            onSound = { setting, sound -> alerts.setSound(setting, sound) },
            onTest = { kind, vibrate -> alerts.test(kind, AlertSeverity.CRITICAL, vibrate) },
            onBack = onBack
        )
    }
}

/**
 * How many settings are silent or off on this phone, for the line that keeps them from being forgotten: a group, a kind
 * or an exception that is Silent or Off, and a paused kind. One that rings, also by vibration, is not counted.
 */
fun silentOrOff(choices: Map<String, AlertChoice>, pauses: Map<String, Long>): Int =
    (choices.filterValues { !it.rings }.keys + pauses.keys).size

/**
 * One row of a section: a group of kinds, or a kind that is a row of its own. [choices] are what the user can pick;
 * [testKind] plays its sound for the Test, null for a row that makes no sound. For a group, its kinds the user set
 * apart from it ([exceptions]) and those that can still be set apart ([candidates], "Add exception"); shown with the
 * group, so an exception is next to the choice it changes (decided 2026-11-26).
 */
data class AlertSettingRow(
    val setting: AlertSetting,
    val choice: AlertChoice,
    val choices: List<AlertChoice>,
    val testKind: AlertKind?,
    val exceptions: List<AlertExceptionRow> = emptyList(),
    val candidates: List<AlertKind> = emptyList()
)

/** A kind the user set apart from its group, with its own choice and the [choices] of its group. */
data class AlertExceptionRow(val kind: AlertKind, val choice: AlertChoice, val choices: List<AlertChoice>)

/**
 * One part of the settings. [devices]: the names of the pump or CGM drivers in use, shown under its heading. [ids]:
 * every group and kind in it, for what of it is silent or off ([sectionSilentOrOff]).
 */
data class AlertSettingsSection(val section: AlertSection, val devices: List<TextRef>, val rows: List<AlertSettingRow>, val ids: Set<String>)

/** As [silentOrOff], for the groups and kinds of [section] only: shown on its entry in the list of parts. */
fun sectionSilentOrOff(section: AlertSettingsSection, choices: Map<String, AlertChoice>, pauses: Map<String, Long>): Int =
    silentOrOff(choices.filterKeys { it in section.ids }, pauses.filterKeys { it in section.ids })

/**
 * The sections that have kinds in use, in [AlertSection] order (decided 2026-11-26). In a section: its protected kinds
 * of their own (urgent low), then its groups, then its other kinds of their own. A group shows only while one of its
 * kinds is in use, so a pump with no info alerts has no Info row. A protected setting always shows Ring: the user
 * cannot change it, and a choice stored for it would not be used. Vibrate is offered only when the phone [canVibrate];
 * where it cannot, a stored Vibrate shows as Ring, which is what the alert core does with it.
 */
fun alertSettingsSections(catalogues: Collection<AlertKindCatalogue>, choices: Map<String, AlertChoice>, canVibrate: Boolean): List<AlertSettingsSection> {
    fun offered(list: List<AlertChoice>) = if (canVibrate) list else list - AlertChoice.VIBRATE
    fun shown(choice: AlertChoice) = if (choice == AlertChoice.VIBRATE && !canVibrate) AlertChoice.RING else choice
    val used = catalogues.filter { it.inUse() && it.kinds.isNotEmpty() }
        // The app's own first: its kinds of their own come before Nightscout's
        .sortedWith(compareBy({ it.kinds.first().origin != AlertOrigin.APP }, { it.kinds.first().source }))
    val kinds = used.flatMap { it.kinds }.distinctBy { it.id }
    return AlertSection.entries.mapNotNull { section ->
        val inSection = kinds.filter { it.section == section }
        if (inSection.isEmpty()) return@mapNotNull null
        val own = inSection.filter { it.group == null }
        val groups = AlertGroup.entries.filter { group -> inSection.any { it.group == group } }
        val groupRows = groups.map { group ->
            // A protected kind cannot be set apart: it would only look silenced
            val apart = inSection.filter { it.group == group && !it.isProtected }
            AlertSettingRow(
                setting = group,
                choice = shown(choiceOf(group, choices)),
                choices = offered(group.choices),
                testKind = inSection.first { it.group == group }.takeIf { group.makesSound },
                exceptions = apart.mapNotNull { kind -> choices[kind.id]?.let { AlertExceptionRow(kind, shown(it), offered(group.choices)) } },
                candidates = apart.filter { it.id !in choices }
            )
        }
        val ownRows = own.map { AlertSettingRow(it, shown(choiceOf(it, choices)), offered(AlertChoice.entries), testKind = it) }
        AlertSettingsSection(
            section = section,
            devices = used.filter { catalogue -> catalogue.kinds.any { it.section == section && it.origin.isDevice() } }.map { it.title },
            rows = ownRows.filter { it.setting.isProtected } + groupRows + ownRows.filterNot { it.setting.isProtected },
            ids = inSection.map { it.id }.toSet() + groups.map { it.id }
        )
    }
}

private fun choiceOf(setting: AlertSetting, choices: Map<String, AlertChoice>): AlertChoice =
    if (setting.isProtected) AlertChoice.RING else choices[setting.id] ?: setting.defaultChoice

private fun AlertOrigin.isDevice(): Boolean = this == AlertOrigin.PUMP || this == AlertOrigin.CGM

/**
 * The choice a new exception starts with: the other side of its group's, so adding one changes something. The user
 * changes it on the row.
 */
fun firstExceptionChoice(group: AlertGroup, groupChoice: AlertChoice): AlertChoice = when {
    groupChoice != AlertChoice.RING -> AlertChoice.RING
    group.makesSound                -> AlertChoice.SILENT
    else                            -> AlertChoice.OFF
}

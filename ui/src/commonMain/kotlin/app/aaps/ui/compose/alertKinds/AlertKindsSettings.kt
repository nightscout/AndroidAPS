package app.aaps.ui.compose.alertKinds

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.BgAlarmRules
import app.aaps.core.interfaces.alerts.BgAlert
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.ui.compose.bgAlarms.BgAlarmRulesScreen
import app.aaps.ui.compose.bgAlarms.BgUnits
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.PreferenceItem
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

    /** The screen of the user's own BG level rules, or null where there are no alerts. */
    val bgAlarmRulesItem: PreferenceItem? get() = null

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
    private val profileUtil: ProfileUtil,
    private val decimalFormatter: DecimalFormatter,
    private val dateUtil: DateUtil,
    private val config: Config
) : AlertKindsSettings {

    /** The BG rules screen: its own settings item, and opened from the BG part of the alert kinds. */
    private val bgAlarmRulesContent = ComposeScreenContent { onBack ->
        val rules by bgAlarmRules.rules.collectAsState()
        val pauses by alerts.pauses.collectAsState()
        BgAlarmRulesScreen(
            rules = rules,
            // One set of rules for every phone (step 2f): a client edits them only as an Admin and while the
            // master can be reached, as every synced setting (the master checks the role again)
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
            pauses = pauses,
            timeText = { dateUtil.timeString(it) },
            onPause = { kinds, minutes -> alerts.pause(kinds, dateUtil.now() + minutes * 60_000L) },
            onResume = { alerts.resume(BgAlert.PAUSABLE) },
            onTest = { kind, severity -> alerts.test(kind, severity) },
            onBack = onBack
        )
    }

    override val bgAlarmRulesItem: PreferenceItem = UiIntentKey.BgAlarmRules.withCompose(bgAlarmRulesContent)

    override val choices: StateFlow<Map<String, AlertChoice>> get() = alerts.choices
    override val pauses: StateFlow<Map<String, Long>> get() = alerts.pauses

    override val settingsItem: PreferenceItem = UiIntentKey.AlertKinds.withCompose(
        ComposeScreenContent { onBack ->
            // The BG rules open in place: back returns here, not to the list of settings
            var showBgRules by remember { mutableStateOf(false) }
            val listState = rememberLazyListState()
            NavigationBackHandler(
                state = rememberNavigationEventState(NavigationEventInfo.None),
                isBackEnabled = showBgRules,
                onBackCompleted = { showBgRules = false }
            )
            if (showBgRules) bgAlarmRulesContent.Content(onBack = { showBgRules = false })
            else {
                val current by alerts.choices.collectAsState()
                val pauses by alerts.pauses.collectAsState()
                val sounds by alerts.sounds.collectAsState()
                AlertKindsScreen(
                    sections = alertSettingsSections(catalogues, current, alertPlatform.canVibrate),
                    vibrates = alertPlatform.canVibrate,
                    // The same count as the line in the notification sheet
                    silentOrOff = silentOrOff(current, pauses),
                    onChoice = { setting, choice -> alerts.setChoice(setting, choice) },
                    onClear = { kind -> alerts.clearChoice(kind) },
                    sounds = sounds,
                    onSound = { setting, sound -> alerts.setSound(setting, sound) },
                    // As an alarm: the sound the user picks it for
                    onTest = { kind, vibrate -> alerts.test(kind, AlertSeverity.CRITICAL, vibrate) },
                    onBgRules = { showBgRules = true },
                    onBack = onBack,
                    listState = listState
                )
            }
        }
    )
}

/**
 * How many settings are silent or off on this phone, for the line that keeps them from being forgotten: a group, a kind
 * or an exception that is Silent or Off, and a paused kind. One that rings, also by vibration, is not counted.
 */
fun silentOrOff(choices: Map<String, AlertChoice>, pauses: Map<String, Long>): Int =
    (choices.filterValues { !it.rings }.keys + pauses.keys).size

/**
 * One row of a section: a group of kinds, or a kind that is a row of its own. [choices] are what the user can pick;
 * [testKind] plays its sound for the Test, null for a row that makes no sound.
 */
data class AlertSettingRow(val setting: AlertSetting, val choice: AlertChoice, val choices: List<AlertChoice>, val testKind: AlertKind?)

/** A kind the user set apart from its group, with its own choice and the [choices] of its group. */
data class AlertExceptionRow(val kind: AlertKind, val choice: AlertChoice, val choices: List<AlertChoice>)

/**
 * One part of the settings. [devices]: the names of the pump or CGM drivers in use, shown under its heading.
 * [candidates]: the kinds that can still be set apart from their group ("Add exception").
 */
data class AlertSettingsSection(
    val section: AlertSection,
    val devices: List<TextRef>,
    val rows: List<AlertSettingRow>,
    val exceptions: List<AlertExceptionRow>,
    val candidates: List<AlertKind>
)

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
            AlertSettingRow(group, shown(choiceOf(group, choices)), offered(group.choices), testKind = inSection.first { it.group == group }.takeIf { group.makesSound })
        }
        val ownRows = own.map { AlertSettingRow(it, shown(choiceOf(it, choices)), offered(AlertChoice.entries), testKind = it) }
        val apart = inSection.filter { !it.isProtected }.mapNotNull { kind -> kind.group?.let { kind to it } }
        AlertSettingsSection(
            section = section,
            devices = used.filter { catalogue -> catalogue.kinds.any { it.section == section && it.origin.isDevice() } }.map { it.title },
            rows = ownRows.filter { it.setting.isProtected } + groupRows + ownRows.filterNot { it.setting.isProtected },
            exceptions = apart.mapNotNull { (kind, group) -> choices[kind.id]?.let { AlertExceptionRow(kind, shown(it), offered(group.choices)) } },
            candidates = apart.map { it.first }.filter { it.id !in choices }
        )
    }
}

private fun choiceOf(setting: AlertSetting, choices: Map<String, AlertChoice>): AlertChoice =
    if (setting.isProtected) AlertChoice.RING else choices[setting.id] ?: AlertChoice.RING

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

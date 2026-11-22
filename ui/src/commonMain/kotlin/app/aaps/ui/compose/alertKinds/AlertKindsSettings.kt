package app.aaps.ui.compose.alertKinds

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.interfaces.alerts.AlertKindCatalogue
import app.aaps.core.interfaces.alerts.AlertOrigin
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.withCompose
import app.aaps.core.ui.compose.ComposeScreenContent
import app.aaps.ui.keys.UiIntentKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The alert kinds in the settings and in the notification sheet (`_docs/ALARMS.md`, section 6).
 *
 * Only a platform with alerts (Android, iOS) has them. The desktop has no alert core, so it binds
 * [AbsentAlertKindsSettings] and shows neither the list nor the line.
 */
interface AlertKindsSettings {

    /** The settings item that opens the list, or null where there are no alerts. */
    val settingsItem: PreferenceItem?

    /** The user's choices on this phone, by kind id; its size is how many kinds are silent or off. */
    val choices: StateFlow<Map<String, AlertChoice>>
}

/** For a platform with no alerts: nothing to show. */
object AbsentAlertKindsSettings : AlertKindsSettings {

    override val settingsItem: PreferenceItem? = null
    override val choices: StateFlow<Map<String, AlertChoice>> = MutableStateFlow<Map<String, AlertChoice>>(emptyMap()).asStateFlow()
}

/** Bound by each platform that has alerts, next to its [Alerts]. */
@SingleIn(AppScope::class)
@Inject
class AlertKindsSettingsImpl(
    private val alerts: Alerts,
    private val catalogues: Set<AlertKindCatalogue>
) : AlertKindsSettings {

    override val choices: StateFlow<Map<String, AlertChoice>> get() = alerts.choices

    override val settingsItem: PreferenceItem = UiIntentKey.AlertKinds.withCompose(
        ComposeScreenContent { onBack ->
            val current by alerts.choices.collectAsState()
            AlertKindsScreen(
                groups = alertKindGroups(catalogues, current),
                silentOrOff = current.size,
                onChoice = { kind, choice -> alerts.setChoice(kind, choice) },
                onBack = onBack
            )
        }
    )
}

/** One kind in the list, with what this phone does with it. */
data class AlertKindRow(val kind: AlertKind, val choice: AlertChoice)

/** One raiser's kinds, under its heading. */
data class AlertKindGroup(val title: TextRef, val rows: List<AlertKindRow>)

/**
 * The groups that are in use, the app's first, each kind with its choice. A protected kind always shows Ring: the
 * user cannot change it, and a choice stored for it would not be used.
 */
fun alertKindGroups(catalogues: Collection<AlertKindCatalogue>, choices: Map<String, AlertChoice>): List<AlertKindGroup> =
    catalogues
        .filter { it.inUse() && it.kinds.isNotEmpty() }
        .sortedWith(compareBy({ it.kinds.first().origin != AlertOrigin.APP }, { it.kinds.first().source }))
        .map { catalogue ->
            AlertKindGroup(
                title = catalogue.title,
                rows = catalogue.kinds.map { kind ->
                    AlertKindRow(kind, if (kind.isProtected) AlertChoice.RING else choices[kind.id] ?: AlertChoice.RING)
                }
            )
        }

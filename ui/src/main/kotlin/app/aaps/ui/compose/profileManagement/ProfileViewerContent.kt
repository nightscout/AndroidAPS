package app.aaps.ui.compose.profileManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.graph.BasalProfileGraphCompose
import app.aaps.core.graph.IcProfileGraphCompose
import app.aaps.core.graph.IsfProfileGraphCompose
import app.aaps.core.graph.TargetBgProfileGraphCompose
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTheme

/**
 * Data class representing a single row in a profile comparison table.
 *
 * @param time Time of day in HH:MM format or "∑" for summary row
 * @param value1 Value from first profile (formatted as string with units)
 * @param value2 Value from second profile (formatted as string with units)
 */
data class ProfileCompareRow(
    val time: String,
    val value1: String,
    val value2: String
)

/**
 * Composable that displays a single profile's data in individual elevated cards.
 * Each profile parameter (Units, DIA, IC, ISF, Basal, Target) is shown in its own card with:
 * - Label and value using ProfileRow styling (bodySmall typography)
 * - Graph visualization for IC, ISF, Basal, and Target
 * - 16dp horizontal padding per card
 * - 8dp spacing between cards
 *
 * This is used in both ProfileViewerScreen (single mode) and ProfileHelperActivity tabs.
 *
 * @param profile The profile to display (ProfileSealed can be EPS, PS, or Pure)
 * @param getIcList Lambda that formats IC values as a comma-separated string
 * @param getIsfList Lambda that formats ISF values as a comma-separated string
 * @param getBasalList Lambda that formats basal values as a comma-separated string
 * @param getTargetList Lambda that formats target values as a range string
 * @param formatBasalSum Lambda that formats total basal sum with units (e.g., "24.5 U")
 */
@Composable
fun ProfileSingleContent(
    profile: Profile,
    getIcList: (Profile) -> String,
    getIsfList: (Profile) -> String,
    getBasalList: (Profile) -> String,
    getTargetList: (Profile) -> String,
    formatBasalSum: (Double) -> String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Units & ICfg Card if running (combined to save space)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.weight(1f),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProfileRow(
                        label = stringResource(R.string.units_label),
                        value = profile.units.asText
                    )
                }
            }
            profile.iCfg?.let { iCfg ->
                ElevatedCard(
                    modifier = Modifier.weight(1f),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    onClick = { /* TODO Open Insulin Management on current running insulin */ }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ProfileRow(
                            label = stringResource(R.string.insulin_label),
                            value = iCfg.insulinLabel
                        )
                    }
                }
            }
        }

        // IC Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileRow(
                    label = stringResource(R.string.ic_label),
                    value = getIcList(profile)
                )
                IcProfileGraphCompose(
                    profile1 = profile,
                    profile1Name = "Profile",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(top = 16.dp)
                )
            }
        }

        // ISF Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileRow(
                    label = stringResource(R.string.isf_label),
                    value = getIsfList(profile)
                )
                IsfProfileGraphCompose(
                    profile1 = profile,
                    profile1Name = "Profile",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(top = 16.dp)
                )
            }
        }

        // Basal Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileRow(
                    label = stringResource(R.string.basal_label),
                    value = getBasalList(profile)
                )
                // Sum displayed above graph
                Text(
                    text = "∑ " + formatBasalSum(profile.percentageBasalSum()),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
                BasalProfileGraphCompose(
                    profile1 = profile,
                    profile1Name = "Profile",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(top = 8.dp)
                )
            }
        }

        // Target Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileRow(
                    label = stringResource(R.string.target_label),
                    value = getTargetList(profile)
                )
                TargetBgProfileGraphCompose(
                    profile1 = profile,
                    profile1Name = "Profile",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(top = 16.dp)
                )
            }
        }
    }
}

/**
 * Composable that displays a comparison between two profiles in individual elevated cards.
 * Each section (Profile names, Units, DIA, IC, ISF, Basal, Target) is shown in its own card with:
 * - Optional profile names header card (if names are provided)
 * - Comparison tables showing time-based values for both profiles side-by-side
 * - Dual-line graphs with colored legends distinguishing profile1 (first color) and profile2 (second color)
 * - 16dp horizontal padding per card
 * - 8dp spacing between cards
 *
 * This is used in ProfileViewerScreen (compare mode) and ProfileHelperActivity comparison tab.
 *
 * @param profile1 First profile to compare
 * @param profile2 Second profile to compare
 * @param shortHourUnit Short form of hour unit (e.g., "h")
 * @param icsRows List of IC comparison rows with time and values for both profiles
 * @param icUnits IC units text (e.g., "g/U")
 * @param isfsRows List of ISF comparison rows with time and values for both profiles
 * @param isfUnits ISF units text (e.g., "mmol/L/U" or "mg/dL/U")
 * @param basalsRows List of basal comparison rows with time and values for both profiles (includes summary row)
 * @param basalUnits Basal units text (e.g., "U/h")
 * @param targetsRows List of target comparison rows with time and range values for both profiles
 * @param targetUnits Target units text (e.g., "mmol/L" or "mg/dL")
 * @param profileName1 Display name for first profile (shown in colored text)
 * @param profileName2 Display name for second profile (shown in colored text)
 */
@Composable
fun ProfileCompareContent(
    profile1: Profile,
    profile2: Profile,
    shortHourUnit: String,
    icsRows: List<ProfileCompareRow>,
    icUnits: String,
    isfsRows: List<ProfileCompareRow>,
    isfUnits: String,
    basalsRows: List<ProfileCompareRow>,
    basalUnits: String,
    targetsRows: List<ProfileCompareRow>,
    targetUnits: String,
    profileName1: String,
    profileName2: String
) {
    val colors = AapsTheme.profileHelperColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Profile names header Card
        if (profileName1.isNotEmpty() || profileName2.isNotEmpty()) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.profile),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = profileName1,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.profile1,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = profileName2,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.profile2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ICfg Card if running profile
        profile1.iCfg?.let { iCfg ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ProfileInlineRow(
                        label = stringResource(R.string.insulin_label),
                        value = iCfg.insulinLabel
                    )
                    ProfileInlineRow(
                        label = stringResource(R.string.concentration_label),
                        value = stringResource(ConcentrationType.fromDouble(iCfg.concentration).label)
                    )
                    ProfileInlineRow(
                        label = stringResource(R.string.peak_label),
                        value = stringResource(R.string.format_mins, iCfg.peak)
                    )
                    ProfileInlineRow(
                        label = stringResource(R.string.dia_label),
                        value = stringResource(R.string.format_hours, iCfg.dia)
                    )
                }
            }
        }

        // IC Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.ic_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ProfileCompareTable(
                    rows = icsRows,
                    units = icUnits
                )
                IcProfileGraphCompose(
                    profile1 = profile1,
                    profile2 = profile2,
                    profile1Name = profileName1,
                    profile2Name = profileName2,
                    profile1Color = colors.profile1,
                    profile2Color = colors.profile2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(top = 8.dp)
                )
            }
        }

        // ISF Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.isf_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ProfileCompareTable(
                    rows = isfsRows,
                    units = isfUnits
                )
                IsfProfileGraphCompose(
                    profile1 = profile1,
                    profile2 = profile2,
                    profile1Name = profileName1,
                    profile2Name = profileName2,
                    profile1Color = colors.profile1,
                    profile2Color = colors.profile2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(top = 8.dp)
                )
            }
        }

        // Basal Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.basal_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ProfileCompareTable(
                    rows = basalsRows,
                    units = basalUnits
                )
                BasalProfileGraphCompose(
                    profile1 = profile1,
                    profile2 = profile2,
                    profile1Name = profileName1,
                    profile2Name = profileName2,
                    profile1Color = colors.profile1,
                    profile2Color = colors.profile2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(top = 8.dp)
                )
            }
        }

        // Target Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.target_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ProfileCompareTable(
                    rows = targetsRows,
                    units = targetUnits
                )
                TargetBgProfileGraphCompose(
                    profile1 = profile1,
                    profile2 = profile2,
                    profile1Name = profileName1,
                    profile2Name = profileName2,
                    profile1Color = colors.profile1,
                    profile2Color = colors.profile2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Composable that displays a single row of profile data with label and value.
 * Uses bodySmall typography to maintain consistent text sizing across profile cards.
 *
 * Layout:
 * - Label (1/3 width): Bold text on the left
 * - Colon separator (optional): ": " between label and value
 * - Value (2/3 width): Regular text on the right
 *
 * @param label The label text (e.g., "Units", "DIA")
 * @param value The value text (e.g., "mg/dL", "5.0 h")
 * @param showColon Whether to show the colon separator (default true, set false for Units in comparison mode)
 */
@Composable
fun ProfileInlineRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun ProfileRow(label: String, value: String, showColon: Boolean = true) {
    val lines = value.split("\n").filter { it.isNotBlank() }
    val useColumns = lines.size > 3

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Label row
        Row {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            if (showColon) {
                Text(
                    text = ":",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Values - either 2 columns or single column, both centered
        if (useColumns) {
            val midPoint = (lines.size + 1) / 2
            val leftColumn = lines.take(midPoint)
            val rightColumn = lines.drop(midPoint)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier.padding(end = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    leftColumn.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    rightColumn.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Composable that displays a comparison table showing values from two profiles side-by-side.
 * Used in ProfileCompareContent for IC, ISF, Basal, and Target comparisons.
 *
 * Each row shows:
 * - Time column (left): HH:MM format or "∑" for summary
 * - Value1 column (center): First profile's value in profile1 color
 * - Value2 column (right): Second profile's value in profile2 color
 * - Units label: Displayed below the table, centered
 *
 * Values are right-aligned with minimum widths for consistent column alignment.
 * Colors from AapsTheme.profileHelperColors distinguish between profiles.
 *
 * @param rows List of ProfileCompareRow containing time and both profile values
 * @param units Units text to display below the table (e.g., "g/U", "U/h")
 */
@Composable
fun ProfileCompareTable(rows: List<ProfileCompareRow>, units: String) {
    val colors = AapsTheme.profileHelperColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = row.time,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.widthIn(min = 50.dp),
                    textAlign = TextAlign.Start
                )
                Text(
                    text = "${row.value1} $units",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.profile1,
                    modifier = Modifier.widthIn(min = 90.dp),
                    textAlign = TextAlign.End
                )
                Text(
                    text = "${row.value2} $units",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.profile2,
                    modifier = Modifier.widthIn(min = 90.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

package app.aaps.core.graph.profile

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
import androidx.compose.ui.tooling.preview.Preview
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
 */
@Composable
fun ProfileSingleContent(
    profile: Profile,
    getIcList: (Profile) -> String,
    getIsfList: (Profile) -> String,
    getBasalList: (Profile) -> String,
    getTargetList: (Profile) -> String,
    formatBasalSum: (Double) -> String,
    onInsulinManager: () -> Unit = {}
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
                    onClick = onInsulinManager
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
 */
@Composable
fun ProfileCompareContent(
    profile1: Profile,
    profile2: Profile,
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

@Preview(showBackground = true)
@Composable
private fun ProfileRowPreview() {
    MaterialTheme {
        Column {
            ProfileRow(label = "Units", value = "mg/dL")
            ProfileRow(label = "IC", value = "08:00 10.0\n12:00 8.5\n18:00 9.0")
            ProfileInlineRow(label = "Insulin", value = "Humalog")
        }
    }
}

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

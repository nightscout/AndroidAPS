package app.aaps.ui.compose.profileManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTheme

/**
 * Data class containing all information needed to display a profile viewer screen.
 *
 * @param profile The primary profile to display (for single view) or first profile (for comparison)
 * @param profile2 The second profile for comparison mode (null for single view)
 * @param profileName Display name for the primary profile
 * @param profileName2 Display name for the second profile (comparison mode only)
 * @param date Formatted date/time string to display when showDate is true
 * @param showDate Whether to show the date card
 * @param headerIcon Drawable resource ID for the header icon
 * @param isCompare Whether this is a comparison view (true) or single profile view (false)
 * @param validationError Error message to display if profile validation fails
 */
data class ProfileViewerData(
    val profile: Profile?,
    val profile2: Profile? = null,
    val profileName: String?,
    val profileName2: String? = null,
    val date: String? = null,
    val showDate: Boolean = false,
    val headerIcon: Int,
    val isCompare: Boolean = false,
    val validationError: String? = null
)

/**
 * Main composable for displaying profile information in both single and comparison modes.
 * This screen is reusable in both standalone activities and tabs within ProfileHelperActivity.
 *
 * The screen adapts its layout based on the showHeader parameter:
 * - When showHeader=true (tabs): Shows a header card with icon and profile name(s), with close button
 * - When showHeader=false (activity): No header shown, expects parent to provide TopAppBar
 *
 * Content is displayed in individual elevated cards for each section (Date, Units, DIA, IC, ISF, Basal, Target),
 * with 8dp spacing between cards and 16dp horizontal padding on each card.
 *
 * @param data ProfileViewerData containing all profile information and display settings
 * @param onClose Callback invoked when the close button is clicked (in header)
 * @param profileSingleContent Composable lambda for rendering single profile content (Units, DIA, IC, ISF, Basal, Target)
 * @param profileCompareContent Composable lambda for rendering profile comparison content with tables and graphs
 * @param profileRow Composable lambda for rendering custom profile rows (currently unused, kept for compatibility)
 * @param showHeader Whether to show the header card (true for tabs, false for standalone activity with TopAppBar)
 * @param modifier Modifier for the root Surface, typically contains Scaffold paddingValues when used in activity
 */
@Composable
fun ProfileViewerScreen(
    data: ProfileViewerData,
    onClose: () -> Unit,
    profileSingleContent: @Composable (Profile) -> Unit,
    profileCompareContent: @Composable (Profile, Profile) -> Unit,
    profileRow: @Composable (String, String) -> Unit,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = AapsTheme.profileHelperColors

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header card with icon, profile name(s), and close button
            // Only shown in tab mode (showHeader=true)
            // Hidden in activity mode where parent provides TopAppBar
            if (showHeader) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(data.headerIcon),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // In comparison mode, show both profile names stacked vertically with different colors
                        // In single mode, show one profile name
                        if (data.isCompare) {
                            Column(modifier = Modifier.weight(1f)) {
                                data.profileName?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = colors.profile1,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                data.profileName2?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = colors.profile2,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = data.profileName ?: "",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Show error message if no profile is set
            if (data.profile == null) {
                Text(
                    text = stringResource(R.string.no_profile_set),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            } else {
                // Main content column with cards
                // Spacing: 16dp top padding when in activity mode (no header), 8dp between cards
                // No horizontal padding on Column - each card has its own 16dp horizontal padding
                Column(
                    modifier = Modifier.padding(top = if (!showHeader) 16.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Display validation error if profile validation failed
                    // Shows reasons why profile is invalid (e.g., missing required values)
                    data.validationError?.let { error ->
                        Text(
                            text = error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Date card - shows when the profile was activated/created
                    // Displayed in same card style as other sections (Units, DIA, IC, etc.)
                    // Uses bodySmall typography to match ProfileRow styling
                    if (data.showDate && data.date != null) {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.date),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = ": ",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = data.date,
                                        modifier = Modifier.weight(2f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    // Profile content - renders either single profile or comparison based on mode
                    // Single mode: Shows Units, DIA, IC, ISF, Basal, Target cards with graphs
                    // Compare mode: Shows profile names header, then comparison tables and graphs for each section
                    // Both modes use individual elevated cards for each section with 8dp spacing
                    if (data.isCompare && data.profile2 != null) {
                        profileCompareContent(data.profile, data.profile2)
                    } else {
                        profileSingleContent(data.profile)
                    }
                }
            }
        }
    }
}

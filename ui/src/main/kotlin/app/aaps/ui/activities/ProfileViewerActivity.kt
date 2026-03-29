package app.aaps.ui.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.getCustomizedName
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.ui.compose.profileManagement.ProfileCompareContent
import app.aaps.ui.compose.profileManagement.ProfileSingleContent
import app.aaps.ui.compose.profileManagement.ProfileViewerData
import app.aaps.ui.compose.profileManagement.ProfileViewerScreen
import app.aaps.ui.compose.profileManagement.buildBasalRows
import app.aaps.ui.compose.profileManagement.buildIcRows
import app.aaps.ui.compose.profileManagement.buildIsfRows
import app.aaps.ui.compose.profileManagement.buildTargetRows
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.text.DecimalFormat
import javax.inject.Inject

/**
 * Activity for displaying profile information in a standalone screen with Material 3 TopAppBar.
 * Supports multiple viewing modes:
 * - RUNNING_PROFILE: Shows the currently active profile at a specific time
 * - CUSTOM_PROFILE: Displays a custom profile from JSON
 * - PROFILE_COMPARE: Side-by-side comparison of two profiles
 * - DB_PROFILE: Shows the most recent profile from database
 *
 * The activity uses Jetpack Compose for UI with:
 * - TopAppBar with back navigation
 * - Scrollable content with individual cards for each profile section
 * - Profile validation and error display
 * - Date display for time-based profiles
 *
 * Intent parameters:
 * - "time": Long - Timestamp for profile lookup (RUNNING_PROFILE, DB_PROFILE)
 * - "mode": Int - UiInteraction.Mode ordinal value
 * - "customProfile": String - JSON string of first profile (CUSTOM_PROFILE, PROFILE_COMPARE)
 * - "customProfileName": String - Display name for profile (CUSTOM_PROFILE, PROFILE_COMPARE)
 * - "customProfile2": String - JSON string of second profile (PROFILE_COMPARE only)
 */
class ProfileViewerActivity : DaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config
    @Inject lateinit var hardLimits: HardLimits
    @Inject lateinit var decimalFormatter: DecimalFormatter

    /** Timestamp for profile lookup in RUNNING_PROFILE and DB_PROFILE modes */
    private var time: Long = 0

    /** Display mode determining which profile(s) to show and how */
    private var mode: UiInteraction.Mode = UiInteraction.Mode.RUNNING_PROFILE

    /** JSON string of first/only profile for CUSTOM_PROFILE and PROFILE_COMPARE modes */
    private var customProfileJson: String = ""

    /** JSON string of second profile for PROFILE_COMPARE mode */
    private var customProfileJson2: String = ""

    /** Display name for profile(s), newline-separated for PROFILE_COMPARE mode */
    private var customProfileName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract profile viewing parameters from launch intent
        intent?.let {
            time = it.getLongExtra("time", 0)
            mode = UiInteraction.Mode.entries.toTypedArray()[it.getIntExtra("mode", UiInteraction.Mode.RUNNING_PROFILE.ordinal)]
            customProfileJson = it.getStringExtra("customProfile") ?: ""
            customProfileName = it.getStringExtra("customProfileName") ?: ""
            if (mode == UiInteraction.Mode.PROFILE_COMPARE)
                customProfileJson2 = it.getStringExtra("customProfile2") ?: ""
        }

        setContent {
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalDateUtil provides dateUtil,
                LocalConfig provides config,
                LocalProfileUtil provides profileUtil
            ) {
                AapsTheme {
                    val viewerData = prepareProfileViewerData()
                    ProfileViewerActivityScreen(
                        viewerData = viewerData,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    /**
     * Main composable screen for the activity with Material 3 TopAppBar and content.
     * Uses Scaffold pattern with:
     * - TopAppBar: Shows profile name and back button, with scroll behavior for elevation
     * - Content: ProfileViewerScreen with showHeader=false (TopAppBar replaces the header card)
     *
     * The TopAppBar uses background color matching the content for seamless appearance,
     * and connects to scroll state for Material 3 elevation behavior.
     *
     * @param viewerData Profile data prepared by prepareProfileViewerData()
     * @param onBack Callback to finish the activity when back button is pressed
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ProfileViewerActivityScreen(
        viewerData: ProfileViewerData,
        onBack: () -> Unit
    ) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                AapsTopAppBar(
                    title = { Text(text = viewerData.profileName ?: stringResource(R.string.profile)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            ProfileViewerScreen(
                data = viewerData,
                onClose = onBack,
                showHeader = false,
                modifier = Modifier.padding(paddingValues),
                profileSingleContent = { profile ->
                    ProfileSingleContent(
                        profile = profile,
                        getIcList = { it.getIcList(rh, dateUtil) },
                        getIsfList = { it.getIsfList(rh, dateUtil) },
                        getBasalList = { it.getBasalList(rh, dateUtil) },
                        getTargetList = { it.getTargetList(rh, dateUtil) },
                        formatBasalSum = { rh.gs(R.string.format_insulin_units, it) }
                    )
                },
                profileCompareContent = { profile1, profile2 ->
                    ProfileCompareContent(
                        profile1 = profile1,
                        profile2 = profile2,
                        shortHourUnit = rh.gs(app.aaps.core.interfaces.R.string.shorthour),
                        icsRows = buildIcRows(profile1, profile2, dateUtil),
                        icUnits = rh.gs(R.string.profile_carbs_per_unit),
                        isfsRows = buildIsfRows(profile1, profile2, profileUtil, dateUtil),
                        isfUnits = "${profileFunction.getUnits().asText} ${rh.gs(R.string.profile_per_unit)}",
                        basalsRows = buildBasalRows(profile1, profile2, dateUtil),
                        basalUnits = rh.gs(R.string.profile_ins_units_per_hour),
                        targetsRows = buildTargetRows(profile1, profile2, dateUtil, profileUtil),
                        targetUnits = profileFunction.getUnits().asText,
                        profileName1 = viewerData.profileName ?: "",
                        profileName2 = viewerData.profileName2 ?: ""
                    )
                },
                profileRow = { label, value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            )
        }
    }

    /**
     * Prepares ProfileViewerData based on the selected viewing mode.
     * Handles profile retrieval, validation, and formatting for different scenarios:
     *
     * RUNNING_PROFILE: Fetches effective profile switch active at specified time
     * - Shows date when profile was activated
     * - Validates profile against pump limits and configuration
     * - Finishes activity if no profile found
     *
     * CUSTOM_PROFILE: Parses profile from JSON string
     * - Used for viewing user-created profiles before saving
     * - Validates against pump limits
     *
     * PROFILE_COMPARE: Parses two profiles from JSON strings
     * - Splits customProfileName by newline for two profile names
     * - Validates first profile only
     *
     * DB_PROFILE: Fetches most recent profile from database
     * - Shows timestamp of profile switch
     * - Validates profile
     *
     * @return ProfileViewerData containing profile(s), metadata, and validation results
     */
    @Composable
    private fun prepareProfileViewerData(): ProfileViewerData {
        return when (mode) {
            // Show the effective profile that was active at the specified time
            UiInteraction.Mode.RUNNING_PROFILE -> {
                val eps = runBlocking { persistenceLayer.getEffectiveProfileSwitchActiveAt(time) }
                if (eps == null) {
                    finish()
                    ProfileViewerData(
                        profile = null,
                        profileName = null,
                        headerIcon = R.drawable.ic_home_profile
                    )
                } else {
                    val profile = ProfileSealed.EPS(eps, activePlugin)
                    val validity = profile.isValid("ProfileViewDialog", activePlugin.activePump, config, rh, notificationManager, hardLimits, false)
                    ProfileViewerData(
                        profile = profile,
                        profileName = eps.originalCustomizedName,
                        date = dateUtil.dateAndTimeString(eps.timestamp),
                        showDate = true,
                        headerIcon = R.drawable.ic_home_profile,
                        validationError = if (!validity.isValid) {
                            rh.gs(R.string.invalid_profile) + "\n" + validity.reasons.joinToString(separator = "\n")
                        } else null
                    )
                }
            }

            UiInteraction.Mode.CUSTOM_PROFILE  -> {
                val profile = pureProfileFromJson(JSONObject(customProfileJson), dateUtil)?.let { ProfileSealed.Pure(it, activePlugin) }
                val validity = profile?.isValid("ProfileViewDialog", activePlugin.activePump, config, rh, notificationManager, hardLimits, false)
                ProfileViewerData(
                    profile = profile,
                    profileName = customProfileName,
                    headerIcon = R.drawable.ic_home_profile,
                    validationError = if (validity?.isValid == false) {
                        rh.gs(R.string.invalid_profile) + "\n" + validity.reasons.joinToString(separator = "\n")
                    } else null
                )
            }

            UiInteraction.Mode.PROFILE_COMPARE -> {
                val profile1 = pureProfileFromJson(JSONObject(customProfileJson), dateUtil)?.let { ProfileSealed.Pure(it, activePlugin) }
                val profile2 = pureProfileFromJson(JSONObject(customProfileJson2), dateUtil)?.let { ProfileSealed.Pure(it, activePlugin) }
                val names = customProfileName.split("\n")
                val validity = profile1?.isValid("ProfileViewDialog", activePlugin.activePump, config, rh, notificationManager, hardLimits, false)
                ProfileViewerData(
                    profile = profile1,
                    profile2 = profile2,
                    profileName = names.getOrNull(0),
                    profileName2 = names.getOrNull(1),
                    headerIcon = app.aaps.core.objects.R.drawable.ic_compare_profiles,
                    isCompare = true,
                    validationError = if (validity?.isValid == false) {
                        rh.gs(R.string.invalid_profile) + "\n" + validity.reasons.joinToString(separator = "\n")
                    } else null
                )
            }

            UiInteraction.Mode.DB_PROFILE      -> {
                val profileList = runBlocking { persistenceLayer.getProfileSwitches() }
                val profile = if (profileList.isNotEmpty()) ProfileSealed.PS(profileList[0], activePlugin) else null
                val validity = profile?.isValid("ProfileViewDialog", activePlugin.activePump, config, rh, notificationManager, hardLimits, false)
                ProfileViewerData(
                    profile = profile,
                    profileName = if (profileList.isNotEmpty()) profileList[0].getCustomizedName(decimalFormatter) else null,
                    date = if (profileList.isNotEmpty()) dateUtil.dateAndTimeString(profileList[0].timestamp) else null,
                    showDate = true,
                    headerIcon = R.drawable.ic_home_profile,
                    validationError = if (validity?.isValid == false) {
                        rh.gs(R.string.invalid_profile) + "\n" + validity.reasons.joinToString(separator = "\n")
                    } else null
                )
            }
        }
    }
}
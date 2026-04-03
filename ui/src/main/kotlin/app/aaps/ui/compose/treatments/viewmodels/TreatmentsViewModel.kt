package app.aaps.ui.compose.treatments.viewmodels

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for TreatmentsScreen that holds all dependencies and provides child ViewModels.
 * This centralizes dependency management and simplifies the composable call site.
 */
@HiltViewModel
@Stable
class TreatmentsViewModel @Inject constructor(
    val persistenceLayer: PersistenceLayer,
    val profileUtil: ProfileUtil,
    val profileFunction: ProfileFunction,
    val activePlugin: ActivePlugin,
    val insulin: Insulin,
    val localProfileManager: LocalProfileManager,
    val rh: ResourceHelper,
    val translator: Translator,
    val dateUtil: DateUtil,
    val decimalFormatter: DecimalFormatter,
    val uiInteraction: UiInteraction,
    val userEntryPresentationHelper: UserEntryPresentationHelper,
    val importExportPrefs: ImportExportPrefs,
    val uel: UserEntryLogger,
    val aapsLogger: AAPSLogger
) : ViewModel() {

    /**
     * Whether to show the Extended Bolus tab based on pump capabilities
     */
    fun showExtendedBolusTab(): Boolean {
        return !activePlugin.activePump.isFakingTempsByExtendedBoluses &&
            activePlugin.activePump.pumpDescription.isExtendedBolusCapable
    }

    // Child ViewModels - created lazily
    val bolusCarbsViewModel: BolusCarbsViewModel by lazy {
        BolusCarbsViewModel(
            persistenceLayer = persistenceLayer,
            profileFunction = profileFunction,
            rh = rh,
            dateUtil = dateUtil,
            decimalFormatter = decimalFormatter,
            aapsLogger = aapsLogger
        )
    }

    val extendedBolusViewModel: ExtendedBolusViewModel by lazy {
        ExtendedBolusViewModel(
            persistenceLayer = persistenceLayer,
            rh = rh,
            dateUtil = dateUtil,
            aapsLogger = aapsLogger
        )
    }

    val tempBasalViewModel: TempBasalViewModel by lazy {
        TempBasalViewModel(
            persistenceLayer = persistenceLayer,
            profileFunction = profileFunction,
            activePlugin = activePlugin,
            rh = rh,
            dateUtil = dateUtil,
            decimalFormatter = decimalFormatter,
            aapsLogger = aapsLogger
        )
    }

    val tempTargetViewModel: TempTargetViewModel by lazy {
        TempTargetViewModel(
            persistenceLayer = persistenceLayer,
            profileUtil = profileUtil,
            rh = rh,
            dateUtil = dateUtil,
            aapsLogger = aapsLogger
        )
    }

    val profileSwitchViewModel: ProfileSwitchViewModel by lazy {
        ProfileSwitchViewModel(
            persistenceLayer = persistenceLayer,
            rh = rh,
            dateUtil = dateUtil,
            aapsLogger = aapsLogger
        )
    }

    val careportalViewModel: CareportalViewModel by lazy {
        CareportalViewModel(
            persistenceLayer = persistenceLayer,
            rh = rh,
            translator = translator,
            dateUtil = dateUtil,
            aapsLogger = aapsLogger
        )
    }

    val runningModeViewModel: RunningModeViewModel by lazy {
        RunningModeViewModel(
            persistenceLayer = persistenceLayer,
            rh = rh,
            dateUtil = dateUtil,
            aapsLogger = aapsLogger
        )
    }

    val userEntryViewModel: UserEntryViewModel by lazy {
        UserEntryViewModel(
            persistenceLayer = persistenceLayer,
            rh = rh,
            dateUtil = dateUtil,
            aapsLogger = aapsLogger
        )
    }
}
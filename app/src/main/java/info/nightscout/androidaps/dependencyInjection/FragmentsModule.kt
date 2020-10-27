package info.nightscout.androidaps.dependencyInjection

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.activities.MyPreferenceFragment
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.plugins.aps.loop.LoopFragment
import info.nightscout.androidaps.plugins.aps.openAPSAMA.OpenAPSAMAFragment
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBFragment
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderFragment
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesFragment
import info.nightscout.androidaps.plugins.constraints.objectives.activities.ObjectivesExamDialog
import info.nightscout.androidaps.plugins.general.actions.ActionsFragment
import info.nightscout.androidaps.plugins.general.automation.AutomationFragment
import info.nightscout.androidaps.plugins.general.automation.dialogs.ChooseActionDialog
import info.nightscout.androidaps.plugins.general.automation.dialogs.ChooseTriggerDialog
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditActionDialog
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditTriggerDialog
import info.nightscout.androidaps.plugins.general.food.FoodFragment
import info.nightscout.androidaps.plugins.general.maintenance.MaintenanceFragment
import info.nightscout.androidaps.plugins.general.nsclient.NSClientFragment
import info.nightscout.androidaps.plugins.general.openhumans.OpenHumansFragment
import info.nightscout.androidaps.plugins.general.openhumans.OpenHumansLoginActivity
import info.nightscout.androidaps.plugins.general.overview.OverviewFragment
import info.nightscout.androidaps.plugins.general.overview.dialogs.EditQuickWizardDialog
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorFragment
import info.nightscout.androidaps.plugins.general.tidepool.TidepoolFragment
import info.nightscout.androidaps.plugins.general.wear.WearFragment
import info.nightscout.androidaps.plugins.insulin.InsulinFragment
import info.nightscout.androidaps.plugins.profile.local.LocalProfileFragment
import info.nightscout.androidaps.plugins.profile.ns.NSProfileFragment
import info.nightscout.androidaps.plugins.pump.combo.ComboFragment
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusGeneralFragment
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog.RileyLinkStatusHistoryFragment
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightFragment
import info.nightscout.androidaps.plugins.pump.medtronic.MedtronicFragment
import info.nightscout.androidaps.plugins.pump.medtronic.dialog.RileyLinkStatusDeviceMedtronic
import info.nightscout.androidaps.plugins.pump.omnipod.ui.OmnipodOverviewFragment
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpFragment
import info.nightscout.androidaps.plugins.source.BGSourceFragment
import info.nightscout.androidaps.plugins.treatments.TreatmentsFragment
import info.nightscout.androidaps.plugins.treatments.fragments.*
import info.nightscout.androidaps.utils.protection.PasswordCheck

@Module
@Suppress("unused")
abstract class FragmentsModule {

    @ContributesAndroidInjector abstract fun contributesPreferencesFragment(): MyPreferenceFragment

    @ContributesAndroidInjector abstract fun contributesActionsFragment(): ActionsFragment
    @ContributesAndroidInjector abstract fun contributesAutomationFragment(): AutomationFragment
    @ContributesAndroidInjector abstract fun contributesBGSourceFragment(): BGSourceFragment
    @ContributesAndroidInjector abstract fun contributesComboFragment(): ComboFragment

    @ContributesAndroidInjector
    abstract fun contributesConfigBuilderFragment(): ConfigBuilderFragment

    @ContributesAndroidInjector abstract fun contributesFoodFragment(): FoodFragment
    @ContributesAndroidInjector abstract fun contributesInsulinFragment(): InsulinFragment
    @ContributesAndroidInjector abstract fun contributesLocalProfileFragment(): LocalProfileFragment
    @ContributesAndroidInjector abstract fun contributesObjectivesFragment(): ObjectivesFragment
    @ContributesAndroidInjector abstract fun contributesOpenAPSAMAFragment(): OpenAPSAMAFragment
    @ContributesAndroidInjector abstract fun contributesOpenAPSSMBFragment(): OpenAPSSMBFragment
    @ContributesAndroidInjector abstract fun contributesOverviewFragment(): OverviewFragment
    @ContributesAndroidInjector abstract fun contributesLocalInsightFragment(): LocalInsightFragment
    @ContributesAndroidInjector abstract fun contributesLoopFragment(): LoopFragment
    @ContributesAndroidInjector abstract fun contributesMaintenanceFragment(): MaintenanceFragment
    @ContributesAndroidInjector abstract fun contributesMedtronicFragment(): MedtronicFragment
    @ContributesAndroidInjector abstract fun contributesOmnipodFragment(): OmnipodOverviewFragment
    @ContributesAndroidInjector abstract fun contributesNSProfileFragment(): NSProfileFragment
    @ContributesAndroidInjector abstract fun contributesNSClientFragment(): NSClientFragment
    @ContributesAndroidInjector
    abstract fun contributesSmsCommunicatorFragment(): SmsCommunicatorFragment
    @ContributesAndroidInjector abstract fun contributesWearFragment(): WearFragment

    @ContributesAndroidInjector abstract fun contributesTidepoolFragment(): TidepoolFragment
    @ContributesAndroidInjector abstract fun contributesTreatmentsFragment(): TreatmentsFragment
    @ContributesAndroidInjector
    abstract fun contributesTreatmentsBolusFragment(): TreatmentsBolusFragment
    @ContributesAndroidInjector
    abstract fun contributesTreatmentsTemporaryBasalsFragment(): TreatmentsTemporaryBasalsFragment
    @ContributesAndroidInjector
    abstract fun contributesTreatmentsTempTargetFragment(): TreatmentsTempTargetFragment
    @ContributesAndroidInjector
    abstract fun contributesTreatmentsExtendedBolusesFragment(): TreatmentsExtendedBolusesFragment
    @ContributesAndroidInjector
    abstract fun contributesTreatmentsCareportalFragment(): TreatmentsCareportalFragment
    @ContributesAndroidInjector
    abstract fun contributesTreatmentsProfileSwitchFragment(): TreatmentsProfileSwitchFragment

    @ContributesAndroidInjector abstract fun contributesVirtualPumpFragment(): VirtualPumpFragment

    @ContributesAndroidInjector abstract fun contributesOpenHumansFragment(): OpenHumansFragment

    @ContributesAndroidInjector abstract fun contributesCalibrationDialog(): CalibrationDialog
    @ContributesAndroidInjector abstract fun contributesCarbsDialog(): CarbsDialog
    @ContributesAndroidInjector abstract fun contributesCareDialog(): CareDialog
    @ContributesAndroidInjector abstract fun contributesEditActionDialog(): EditActionDialog
    @ContributesAndroidInjector abstract fun contributesEditEventDialog(): EditEventDialog
    @ContributesAndroidInjector abstract fun contributesEditTriggerDialog(): EditTriggerDialog

    @ContributesAndroidInjector
    abstract fun contributesEditQuickWizardDialog(): EditQuickWizardDialog

    @ContributesAndroidInjector abstract fun contributesExtendedBolusDialog(): ExtendedBolusDialog
    @ContributesAndroidInjector abstract fun contributesFillDialog(): FillDialog
    @ContributesAndroidInjector abstract fun contributesChooseActionDialog(): ChooseActionDialog
    @ContributesAndroidInjector abstract fun contributesChooseTriggerDialog(): ChooseTriggerDialog
    @ContributesAndroidInjector abstract fun contributesInsulinDialog(): InsulinDialog
    @ContributesAndroidInjector abstract fun contributesObjectivesExamDialog(): ObjectivesExamDialog
    @ContributesAndroidInjector abstract fun contributesProfileSwitchDialog(): ProfileSwitchDialog
    @ContributesAndroidInjector abstract fun contributesTempBasalDialog(): TempBasalDialog
    @ContributesAndroidInjector abstract fun contributesTempTargetDialog(): TempTargetDialog
    @ContributesAndroidInjector abstract fun contributesTreatmentDialog(): TreatmentDialog
    @ContributesAndroidInjector abstract fun contributesWizardDialog(): WizardDialog
    @ContributesAndroidInjector abstract fun contributesWizardInfoDialog(): WizardInfoDialog

    @ContributesAndroidInjector
    abstract fun contributesExchangeAuthTokenDialot(): OpenHumansLoginActivity.ExchangeAuthTokenDialog

    @ContributesAndroidInjector abstract fun contributesPasswordCheck(): PasswordCheck

    @ContributesAndroidInjector
    abstract fun contributesRileyLinkStatusGeneral(): RileyLinkStatusGeneralFragment
    @ContributesAndroidInjector
    abstract fun contributesRileyLinkStatusHistoryFragment(): RileyLinkStatusHistoryFragment
    @ContributesAndroidInjector
    abstract fun contributesRileyLinkStatusDeviceMedtronic(): RileyLinkStatusDeviceMedtronic
}
package app.aaps.di.metro

import app.aaps.pump.dana.compose.DanaHistoryViewModel
import app.aaps.pump.dana.compose.DanaOverviewViewModel
import app.aaps.pump.dana.compose.DanaUserOptionsViewModel
import app.aaps.pump.danar.compose.DanaRPairWizardViewModel
import app.aaps.pump.danars.compose.DanaRSOverviewViewModel
import app.aaps.pump.danars.compose.DanaRSPairWizardViewModel
import app.aaps.pump.diaconn.compose.DiaconnHistoryViewModel
import app.aaps.pump.diaconn.compose.DiaconnOverviewViewModel
import app.aaps.pump.diaconn.compose.DiaconnPairWizardViewModel
import app.aaps.pump.diaconn.compose.DiaconnUserOptionsViewModel
import app.aaps.pump.equil.compose.EquilHistoryViewModel
import app.aaps.pump.equil.compose.EquilOverviewViewModel
import app.aaps.pump.equil.compose.EquilWizardViewModel
import app.aaps.pump.medtrum.compose.MedtrumOverviewViewModel
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel
import app.aaps.pump.omnipod.dash.ui.compose.DashOverviewViewModel
import app.aaps.pump.omnipod.dash.ui.compose.DashPodHistoryViewModel
import app.aaps.pump.omnipod.dash.ui.wizard.compose.DashOmnipodWizardViewModel
import app.aaps.pump.common.compose.RileyLinkPairWizardViewModel
import app.aaps.pump.common.compose.RileyLinkStatusViewModel
import app.aaps.pump.medtronic.compose.MedtronicOverviewViewModel
import app.aaps.pump.medtronic.compose.MedtronicHistoryViewModel
import app.aaps.pump.eopatch.compose.EopatchOverviewViewModel
import app.aaps.pump.eopatch.compose.EopatchPatchViewModel
import com.google.common.truth.Truth.assertThat
import info.nightscout.pump.combov2.compose.ComboV2OverviewViewModel
import info.nightscout.pump.combov2.compose.ComboV2PairWizardViewModel
import org.junit.jupiter.api.Test

/**
 * Every pump view model the migration moved onto Metro really reaches the factory.
 *
 * A pump screen is the one place where a wrong annotation can hide for a long time. Only one pump is
 * ever set up on a phone, so a broken map entry is invisible until the one user who owns that pump
 * opens that screen - and then it is a crash, on a device that is running a loop. The rest of the app
 * gets opened during any test pass; a Diaconn dialog does not.
 *
 * This test only asks whether the binding exists. It cannot build the view models: they are `full`
 * flavour classes, which is why the file is in `src/testFull` and not beside the other graph tests.
 */
class PumpViewModelsTest {

    @Test
    fun `every pump view model is contributed to the root graph`() {
        val contributed = testRoot().viewModelProviders.keys
        val expected = listOf(
            ComboV2OverviewViewModel::class,
            ComboV2PairWizardViewModel::class,
            DanaHistoryViewModel::class,
            DanaOverviewViewModel::class,
            DanaRPairWizardViewModel::class,
            DanaRSOverviewViewModel::class,
            DanaRSPairWizardViewModel::class,
            DanaUserOptionsViewModel::class,
            DashOmnipodWizardViewModel::class,
            DashOverviewViewModel::class,
            DashPodHistoryViewModel::class,
            DiaconnHistoryViewModel::class,
            DiaconnOverviewViewModel::class,
            DiaconnPairWizardViewModel::class,
            DiaconnUserOptionsViewModel::class,
            EquilHistoryViewModel::class,
            EquilOverviewViewModel::class,
            EquilWizardViewModel::class,
            MedtrumOverviewViewModel::class,
            MedtrumPatchViewModel::class,
            // The last six off Hilt. Their dependencies are Dagger-owned pump state, so PumpLeavesTest
            // is the other half of this: it checks the graph hands those over instead of rebuilding them.
            EopatchOverviewViewModel::class,
            EopatchPatchViewModel::class,
            MedtronicHistoryViewModel::class,
            MedtronicOverviewViewModel::class,
            RileyLinkPairWizardViewModel::class,
            RileyLinkStatusViewModel::class
        )
        assertThat(contributed).containsAtLeastElementsIn(expected)
    }
}

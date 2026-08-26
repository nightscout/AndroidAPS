package app.aaps.di.metro

import app.aaps.plugins.constraints.objectives.compose.ObjectivesViewModel
import app.aaps.plugins.sync.nsclientV3.clientcontrol.compose.AuthorizedClientsViewModel
import app.aaps.plugins.sync.nsclientV3.clientcontrol.compose.PairWithMasterViewModel
import app.aaps.plugins.sync.nsclientV3.compose.NSClientViewModel
import app.aaps.plugins.sync.smsCommunicator.compose.SmsCommunicatorViewModel
import app.aaps.plugins.sync.tidepool.compose.TidepoolViewModel
import app.aaps.plugins.sync.wear.compose.WearViewModel
import app.aaps.plugins.sync.xdrip.compose.XdripViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * View models that register themselves with `@ContributesIntoMap` really reach the factory.
 *
 * There is nothing to read at the call site any more - no `@Provides`, no map entry, no factory edit -
 * so a view model with a missing or wrong annotation looks exactly like a correct one. It fails at the
 * moment the screen opens, with "no binding for view model", which is the worst place to find out.
 */
class ContributedViewModelsTest {

    @Test
    fun `the objectives view model is contributed to the root graph`() {
        assertThat(testRoot().viewModelProviders.keys).contains(ObjectivesViewModel::class)
    }

    @Test
    fun `every sync view model moved off Hilt reaches the factory`() {
        // These seven were @HiltViewModel until the move to metrox's factory. The annotation swap and
        // the call-site swap are separate edits: this covers the first one. The second - a screen still
        // calling hiltViewModel() for a view model that is no longer @HiltViewModel - is NOT covered
        // here and fails only when the screen opens, so grep for hiltViewModel() when converting one.
        val registered = testRoot().viewModelProviders.keys

        assertThat(registered).containsAtLeast(
            SmsCommunicatorViewModel::class,
            TidepoolViewModel::class,
            XdripViewModel::class,
            NSClientViewModel::class,
            AuthorizedClientsViewModel::class,
            PairWithMasterViewModel::class,
            WearViewModel::class
        )
    }

    @Test
    fun `a view model is NOT scoped - each screen gets its own`() {
        // A view model holds one screen's state. Scoping it would hand the next screen the previous
        // screen's state, and the annotation that does that is a single word.
        val root = testRoot()
        val provider = root.viewModelProviders.getValue(ObjectivesViewModel::class)
        assertThat(provider()).isNotSameInstanceAs(provider())
    }
}

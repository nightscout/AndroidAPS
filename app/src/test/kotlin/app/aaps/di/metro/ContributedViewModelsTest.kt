package app.aaps.di.metro

import app.aaps.plugins.constraints.objectives.compose.ObjectivesViewModel
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
    fun `a view model is NOT scoped - each screen gets its own`() {
        // A view model holds one screen's state. Scoping it would hand the next screen the previous
        // screen's state, and the annotation that does that is a single word.
        val root = testRoot()
        val provider = root.viewModelProviders.getValue(ObjectivesViewModel::class)
        assertThat(provider()).isNotSameInstanceAs(provider())
    }
}

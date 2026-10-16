package app.aaps.plugins.automation

import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerDummy
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ComposeTriggerTest : TestBase() {

    // Triggers need only their dependency bundle now; the connector logic under test touches none of it.
    private val triggerDeps: TriggerDeps = mock()

    @Test fun testTriggerList() {
        val root = TriggerConnector(triggerDeps)

        // add some triggers
        val t0: Trigger = TriggerDummy(triggerDeps)
        root.list.add(t0)
        val t1: Trigger = TriggerDummy(triggerDeps)
        root.list.add(t1)
        val t2: Trigger = TriggerDummy(triggerDeps)
        root.list.add(t2)
        assertThat(root.list).containsExactly(t0, t1, t2).inOrder()

        // remove a trigger
        root.list.remove(t1)
        assertThat(root.list).containsExactly(t0, t2).inOrder()
    }

    @Test
    fun testChangeConnector() {
        // initialize scenario
        val root = TriggerConnector(triggerDeps, TriggerConnector.Type.AND)
        val t = arrayOfNulls<Trigger>(4)
        for (i in t.indices) {
            t[i] = TriggerDummy(triggerDeps)
            root.list.add(t[i]!!)
        }
        assertThat(root.size()).isEqualTo(4)
    }
}

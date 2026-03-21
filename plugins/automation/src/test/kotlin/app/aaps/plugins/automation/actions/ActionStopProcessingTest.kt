package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class ActionStopProcessingTest : ActionsTestBase() {

    lateinit var sut: ActionStopProcessing

    @BeforeEach
    fun setup() {

        whenever(rh.gs(R.string.stop_processing)).thenReturn("Stop processing")
        sut = ActionStopProcessing(injector)
    }

    @Test
    fun friendlyNameTest() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(R.string.stop_processing)
    }

    @Test
    fun shortDescriptionTest() = runTest {
        assertThat(sut.shortDescription()).isEqualTo("Stop processing")
    }

    @Test
    fun iconTest() = runTest {
        assertThat(sut.icon()).isEqualTo(R.drawable.ic_stop_24dp)
    }

    @Test
    fun doActionTest() = runTest {
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
    }
}

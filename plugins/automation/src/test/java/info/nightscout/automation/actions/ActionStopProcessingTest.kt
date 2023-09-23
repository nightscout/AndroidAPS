package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.interfaces.queue.Callback
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class ActionStopProcessingTest : ActionsTestBase() {

    lateinit var sut: ActionStopProcessing

    @BeforeEach
    fun setup() {

        `when`(rh.gs(R.string.stop_processing)).thenReturn("Stop processing")
        sut = ActionStopProcessing(injector)
    }

    @Test
    fun friendlyNameTest() {
        Assertions.assertEquals(R.string.stop_processing, sut.friendlyName())
    }

    @Test
    fun shortDescriptionTest() {
        Assertions.assertEquals("Stop processing", sut.shortDescription())
    }

    @Test
    fun iconTest() {
        Assertions.assertEquals(R.drawable.ic_stop_24dp, sut.icon())
    }

    @Test
    fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {
                Assertions.assertTrue(result.success)
            }
        })
    }
}
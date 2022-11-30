package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.interfaces.queue.Callback
import org.junit.Assert
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
        Assert.assertEquals(R.string.stop_processing, sut.friendlyName())
    }

    @Test
    fun shortDescriptionTest() {
        Assert.assertEquals("Stop processing", sut.shortDescription())
    }

    @Test
    fun iconTest() {
        Assert.assertEquals(R.drawable.ic_stop_24dp, sut.icon())
    }

    @Test
    fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
    }
}
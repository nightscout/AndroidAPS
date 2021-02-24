package info.nightscout.androidaps.plugins.general.automation.elements

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class LayoutBuilderTest : TestBase() {

    val injector = HasAndroidInjector { AndroidInjector { } }

    @Test fun addTest() {
        val layoutBuilder = LayoutBuilder()
        val inputInsulin = InputInsulin(injector)
        layoutBuilder.add(inputInsulin)
        Assert.assertEquals(1, layoutBuilder.mElements.size)
    }

    @Test fun addConditionalTest() {
        val layoutBuilder = LayoutBuilder()
        val inputInsulin = InputInsulin(injector)
        layoutBuilder.add(inputInsulin, true)
        Assert.assertEquals(1, layoutBuilder.mElements.size)
        layoutBuilder.add(inputInsulin, false)
        Assert.assertEquals(1, layoutBuilder.mElements.size)
    }
}
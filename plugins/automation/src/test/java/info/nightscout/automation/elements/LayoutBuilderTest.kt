package info.nightscout.automation.elements

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class LayoutBuilderTest : TestBase() {

    val injector = HasAndroidInjector { AndroidInjector { } }

    @Test fun addTest() {
        val layoutBuilder = LayoutBuilder()
        val inputInsulin = InputInsulin()
        layoutBuilder.add(inputInsulin)
        Assert.assertEquals(1, layoutBuilder.mElements.size)
    }

    @Test fun addConditionalTest() {
        val layoutBuilder = LayoutBuilder()
        val inputInsulin = InputInsulin()
        layoutBuilder.maybeAdd(inputInsulin, true)
        Assert.assertEquals(1, layoutBuilder.mElements.size)
        layoutBuilder.maybeAdd(inputInsulin, false)
        Assert.assertEquals(1, layoutBuilder.mElements.size)
    }
}
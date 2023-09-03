package info.nightscout.automation.elements

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class LayoutBuilderTest : TestBase() {

    val injector = HasAndroidInjector { AndroidInjector { } }

    @Test fun addTest() {
        val layoutBuilder = LayoutBuilder()
        val inputInsulin = InputInsulin()
        layoutBuilder.add(inputInsulin)
        Assertions.assertEquals(1, layoutBuilder.mElements.size)
    }

    @Test fun addConditionalTest() {
        val layoutBuilder = LayoutBuilder()
        val inputInsulin = InputInsulin()
        layoutBuilder.maybeAdd(inputInsulin, true)
        Assertions.assertEquals(1, layoutBuilder.mElements.size)
        layoutBuilder.maybeAdd(inputInsulin, false)
        Assertions.assertEquals(1, layoutBuilder.mElements.size)
    }
}
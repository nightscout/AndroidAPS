package app.aaps.plugins.automation.elements

import app.aaps.shared.tests.TestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LayoutBuilderTest : TestBase() {

    val injector = HasAndroidInjector { AndroidInjector { } }

    @Test fun addTest() {
        val layoutBuilder = LayoutBuilder()
        val inputInsulin = InputInsulin()
        layoutBuilder.add(inputInsulin)
        assertThat(layoutBuilder.mElements).hasSize(1)
    }

    @Test fun addConditionalTest() {
        val layoutBuilder = LayoutBuilder()
        val inputInsulin = InputInsulin()
        layoutBuilder.maybeAdd(inputInsulin, true)
        assertThat(layoutBuilder.mElements).hasSize(1)
        layoutBuilder.maybeAdd(inputInsulin, false)
        assertThat(layoutBuilder.mElements).hasSize(1)
    }
}

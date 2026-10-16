package app.aaps.wear.data

import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.R
import app.aaps.wear.WearTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

/**
 * Regression test for GitHub issue #4870: the `key_include_external` preference was dropped
 * during the V4 AndroidX/DataStore migration, so the default watchface always loaded the
 * variant *without* external/follower views. [ComplicationDataRepository.selectDefaultWatchface]
 * restores honoring that preference.
 */
class ComplicationDataRepositorySelectDefaultTest : WearTestBase() {

    private lateinit var repository: ComplicationDataRepository

    private val default = CwfData("default", mutableMapOf(), mutableMapOf())
    private val full = CwfData("full", mutableMapOf(), mutableMapOf())

    @BeforeEach
    fun setupRepository() {
        repository = ComplicationDataRepository(context, AAPSLoggerTest(), sp)
    }

    @Test fun includeExternalDisabledReturnsPlainDefault() {
        whenever(sp.getBoolean(R.string.key_include_external, false)).thenReturn(false)
        val data = ComplicationData(customWatchfaceDefault = default, customWatchfaceDefaultFull = full)

        assertThat(repository.selectDefaultWatchface(data)?.json).isEqualTo("default")
    }

    @Test fun includeExternalEnabledReturnsFullVariant() {
        whenever(sp.getBoolean(R.string.key_include_external, false)).thenReturn(true)
        val data = ComplicationData(customWatchfaceDefault = default, customWatchfaceDefaultFull = full)

        assertThat(repository.selectDefaultWatchface(data)?.json).isEqualTo("full")
    }

    @Test fun includeExternalEnabledFallsBackToPlainDefaultWhenFullMissing() {
        whenever(sp.getBoolean(R.string.key_include_external, false)).thenReturn(true)
        val data = ComplicationData(customWatchfaceDefault = default, customWatchfaceDefaultFull = null)

        assertThat(repository.selectDefaultWatchface(data)?.json).isEqualTo("default")
    }
}

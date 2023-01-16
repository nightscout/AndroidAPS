package info.nightscout.core.graph.data

import android.content.Context
import android.graphics.Color
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.shared.interfaces.ResourceHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class InMemoryGlucoseValueDataPointTest {

    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context

    @BeforeEach
    fun setup() {
        Mockito.`when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        Mockito.`when`(rh.gac(any(), any())).thenReturn(Color.GREEN)
    }
    @Test
    fun alphaShouldBeAddedForFilledGaps() {
        val gv = InMemoryGlucoseValue(1000, 100.0)
        val sut = InMemoryGlucoseValueDataPoint(gv, defaultValueHelper, profileFunction, rh)

        var alpha = sut.color(context).ushr(24)
        Assertions.assertEquals(255, alpha)
        gv.filledGap = true
        alpha = sut.color(context).ushr(24)
        Assertions.assertEquals(128, alpha)
    }
}
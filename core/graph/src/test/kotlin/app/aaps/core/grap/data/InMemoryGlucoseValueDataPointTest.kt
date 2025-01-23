package app.aaps.core.grap.data

import android.content.Context
import android.graphics.Color
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.graph.data.InMemoryGlucoseValueDataPoint
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
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

    @Mock lateinit var preferences: Preferences
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
        val gv = InMemoryGlucoseValue(1000, 100.0, sourceSensor = SourceSensor.UNKNOWN)
        val sut = InMemoryGlucoseValueDataPoint(gv, preferences, profileFunction, rh)

        var alpha = sut.color(context).ushr(24)
        assertThat(alpha).isEqualTo(255)
        gv.filledGap = true
        alpha = sut.color(context).ushr(24)
        assertThat(alpha).isEqualTo(128)
    }
}

package app.aaps.core.interfaces.iob

import android.content.Context
import android.graphics.Color
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.main.graph.data.InMemoryGlucoseValueDataPoint
import app.aaps.database.entities.GlucoseValue
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
        val gv = InMemoryGlucoseValue(1000, 100.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN)
        val sut = InMemoryGlucoseValueDataPoint(gv, defaultValueHelper, profileFunction, rh)

        var alpha = sut.color(context).ushr(24)
        assertThat(alpha).isEqualTo(255)
        gv.filledGap = true
        alpha = sut.color(context).ushr(24)
        assertThat(alpha).isEqualTo(128)
    }
}

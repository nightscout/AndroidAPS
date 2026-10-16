package app.aaps.core.objects.profile

import android.content.Context
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

/**
 * `pureProfileFromJson` now has three entry points - `String`, kotlinx `JsonObject` and the
 * `org.json` adapter - and callers are being moved off the `org.json` one a few at a time.
 *
 * These tests pin the three against each other, so a caller can be switched over without having to
 * re-argue that the profile it reads is identical. They also pin the awkward rules that decide
 * whether a profile is usable at all, because those are the ones a conversion is most likely to
 * quietly drop.
 */
class PureProfileFromJsonParityTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context

    private lateinit var dateUtil: DateUtil

    @BeforeEach fun setup() {
        dateUtil = DateUtilImpl(context)
    }

    private val validProfile =
        """{"dia":"5","carbratio":[{"time":"00:00","value":"30"}],"sens":[{"time":"00:00","value":"100"}],""" +
            """"basal":[{"time":"00:00","value":"1"}],"target_low":[{"time":"00:00","value":"4.5"}],""" +
            """"target_high":[{"time":"00:00","value":"7"}],"units":"mmol","timezone":"UTC"}"""

    private fun fromText(text: String) = pureProfileFromJson(text, dateUtil)
    private fun fromOrgJson(text: String) = pureProfileFromJson(JSONObject(text), dateUtil)

    @Test fun `the text entry point and the org json adapter read the same profile`() {
        val viaText = fromText(validProfile)
        val viaOrgJson = fromOrgJson(validProfile)

        assertThat(viaText).isNotNull()
        assertThat(viaOrgJson).isNotNull()
        assertThat(viaText!!.glucoseUnit).isEqualTo(viaOrgJson!!.glucoseUnit)
        assertThat(viaText.basalBlocks).isEqualTo(viaOrgJson.basalBlocks)
        assertThat(viaText.isfBlocks).isEqualTo(viaOrgJson.isfBlocks)
        assertThat(viaText.icBlocks).isEqualTo(viaOrgJson.icBlocks)
        assertThat(viaText.targetBlocks).isEqualTo(viaOrgJson.targetBlocks)
        assertThat(viaText.utcOffset).isEqualTo(viaOrgJson.utcOffset)
    }

    /**
     * Quoted numbers are not a theoretical case - real Nightscout documents contain them, and the
     * fixture above stores every value as a string. A reader that lost the coercion would answer
     * null here rather than a profile.
     */
    @Test fun `values quoted as strings are still read as numbers`() {
        val profile = fromText(validProfile)

        assertThat(profile).isNotNull()
        assertThat(profile!!.basalBlocks).hasSize(1)
        assertThat(profile.basalBlocks[0].amount).isWithin(0.001).of(1.0)
    }

    /**
     * The most dangerous line in the file. `GlucoseUnit.fromText` never throws - it answers MGDL for
     * anything it does not recognise - so a missing unit MUST reject the profile. If it did not, an
     * mmol/L profile would be read as mg/dL and every target, ISF and correction would be out by 18x.
     */
    @Test fun `a profile without units is rejected unless a default is supplied`() {
        val noUnits = validProfile.replace(""","units":"mmol"""", "")

        assertThat(fromText(noUnits)).isNull()
        assertThat(fromOrgJson(noUnits)).isNull()

        // ...and the caller supplied default is what rescues it, still as mmol.
        assertThat(pureProfileFromJson(noUnits, dateUtil, "mmol")?.glucoseUnit).isEqualTo(GlucoseUnit.MMOL)
    }

    @Test fun `a missing schedule makes the profile invalid rather than throwing`() {
        val noCarbratio = validProfile.replace(""""carbratio":[{"time":"00:00","value":"30"}],""", "")

        assertThat(fromText(noCarbratio)).isNull()
        assertThat(fromOrgJson(noCarbratio)).isNull()
    }

    @Test fun `text that is not JSON at all gives an invalid profile rather than throwing`() {
        assertThat(fromText("")).isNull()
        assertThat(fromText("garbage")).isNull()
        assertThat(fromText("[]")).isNull()
    }

    /**
     * An unknown zone falls back to UTC rather than throwing. `java.util.TimeZone.getTimeZone`
     * answered GMT for an id it did not know and kotlinx throws instead, so the fallback is explicit.
     */
    @Test fun `an unknown timezone falls back to UTC`() {
        val oddZone = validProfile.replace(""""timezone":"UTC"""", """"timezone":"Mars/Olympus"""")

        assertThat(fromText(oddZone)?.utcOffset).isEqualTo(0L)
    }
}

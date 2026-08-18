package app.aaps.core.objects.profile

import android.content.Context
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * The time zone a profile carries, which had no coverage at all.
 *
 * It used to be a whole `java.util.TimeZone` and the only thing read off it was `rawOffset` - the
 * zone's **standard** offset, which ignores daylight saving by definition. `Europe/Prague` therefore
 * reported +01:00 in July as well as in January.
 *
 * That mattered because the single consumer, `toPureNsJson`, turns the number back into a zone
 * *name* for the Nightscout profile document by searching for a zone sitting at that offset **now**.
 * In July nothing in Europe is at +01:00, so the document ended up naming an unrelated zone. The
 * value is now resolved for the moment the profile is read, so the two halves agree.
 *
 * `now()` is stubbed rather than taken from the clock, or these tests would only say anything during
 * one half of the year.
 */
class ProfileTimeZoneTest : TestBase() {

    @Mock lateinit var context: Context

    private lateinit var dateUtil: DateUtilImpl

    // 2026-01-15T12:00Z and 2026-07-15T12:00Z: Prague is CET (+01:00) at the first and CEST (+02:00)
    // at the second.
    private val winter = 1_768_478_400_000L
    private val summer = 1_784_116_800_000L

    private val oneHour = 3_600_000L
    private val twoHours = 7_200_000L

    @BeforeEach fun prepare() {
        dateUtil = spy(DateUtilImpl(context))
    }

    private fun profileIn(zone: String, at: Long) = run {
        whenever(dateUtil.now()).thenReturn(at)
        pureProfileFromJson(JSONObject(profileJson(zone)), dateUtil)!!
    }

    @Test
    fun `a zone on daylight saving reports the summer offset in summer`() {
        assertThat(profileIn("Europe/Prague", summer).utcOffset).isEqualTo(twoHours)
    }

    /**
     * The regression this replaced: `rawOffset` gave one hour here *and* in summer, so the two were
     * indistinguishable and summer was the wrong one.
     */
    @Test
    fun `the same zone reports the winter offset in winter`() {
        assertThat(profileIn("Europe/Prague", winter).utcOffset).isEqualTo(oneHour)
    }

    @Test
    fun `a zone without daylight saving reads the same all year`() {
        // Africa/Lagos is +01:00 the whole year round, which is exactly why it used to be picked as
        // the name for a summer Prague profile.
        assertThat(profileIn("Africa/Lagos", summer).utcOffset).isEqualTo(oneHour)
        assertThat(profileIn("Africa/Lagos", winter).utcOffset).isEqualTo(oneHour)
    }

    @Test
    fun `UTC is zero in both halves of the year`() {
        assertThat(profileIn("UTC", summer).utcOffset).isEqualTo(0L)
        assertThat(profileIn("UTC", winter).utcOffset).isEqualTo(0L)
    }

    /**
     * `java.util.TimeZone.getTimeZone` answered GMT for an id it did not recognise, so a corrupt or
     * unknown zone silently became UTC rather than failing the whole profile. kotlinx throws instead,
     * so that fallback is explicit now - and this says it still holds.
     */
    @Test
    fun `an unknown zone falls back to UTC instead of failing the profile`() {
        val profile = profileIn("Not/AZone", summer)

        assertThat(profile.utcOffset).isEqualTo(0L)
        assertThat(profile.basalBlocks).isNotEmpty()      // the rest of the profile still parsed
    }

    /** A profile with no timezone at all also has to keep parsing. */
    @Test
    fun `a missing zone falls back to UTC`() {
        whenever(dateUtil.now()).thenReturn(summer)
        val noZone = """{"dia":6,"carbratio":[{"time":"00:00","value":30}],"sens":[{"time":"00:00","value":3}],""" +
            """"basal":[{"time":"00:00","value":1}],"target_low":[{"time":"00:00","value":4.5}],""" +
            """"target_high":[{"time":"00:00","value":7}],"units":"mmol"}"""

        assertThat(pureProfileFromJson(JSONObject(noZone), dateUtil)!!.utcOffset).isEqualTo(0L)
    }

    private fun profileJson(zone: String) =
        """{"dia":6,"carbratio":[{"time":"00:00","value":30}],"sens":[{"time":"00:00","value":3}],""" +
            """"basal":[{"time":"00:00","value":1}],"target_low":[{"time":"00:00","value":4.5}],""" +
            """"target_high":[{"time":"00:00","value":7}],"units":"mmol","timezone":"$zone"}"""
}

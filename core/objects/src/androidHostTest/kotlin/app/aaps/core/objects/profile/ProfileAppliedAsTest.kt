package app.aaps.core.objects.profile

import app.aaps.core.data.model.data.Block
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * #2454: the pump can round the basal, so the running profile (EPS) holds other rates than the profile switch (PS)
 * it was made from. "Is this PS running?" must then follow the link, or AAPS sets the same profile again and again.
 */
class ProfileAppliedAsTest : TestBaseWithProfile() {

    private val day = 24.hours.inWholeMilliseconds

    private fun ps(id: Long, basal: Double = 1.0) =
        ProfileSealed.PS(profileSwitch.copy(id = id, basalBlocks = listOf(Block(day, basal)), iCfg = someICfg), activePlugin)

    private fun eps(fromPs: Long?, basal: Double = 1.0) =
        ProfileSealed.EPS(effectiveProfileSwitch.copy(id = 7, originalPsId = fromPs, basalBlocks = listOf(Block(day, basal))), activePlugin)

    @Test
    fun `an EPS made from the PS is its running profile, also with the basal rounded by the pump`() {
        assertThat(ps(id = 140, basal = 0.42).isAppliedAs(eps(fromPs = 140, basal = 0.40))).isTrue()
    }

    @Test
    fun `an EPS made from another PS is not, when the values differ`() {
        assertThat(ps(id = 140, basal = 0.42).isAppliedAs(eps(fromPs = 139, basal = 0.40))).isFalse()
    }

    @Test
    fun `an EPS made from another PS with the same values still counts, as before`() {
        assertThat(ps(id = 140).isAppliedAs(eps(fromPs = 139))).isTrue()
    }

    @Test
    fun `without a link the values decide`() {
        assertThat(ps(id = 140).isAppliedAs(eps(fromPs = null))).isTrue()
        assertThat(ps(id = 140, basal = 0.42).isAppliedAs(eps(fromPs = null, basal = 0.40))).isFalse()
    }

    @Test
    fun `a PS that is not stored yet matches no link`() {
        assertThat(ps(id = 0, basal = 0.42).isAppliedAs(eps(fromPs = 0, basal = 0.40))).isFalse()
    }

    @Test
    fun `an EPS compared with an EPS still compares the values`() {
        assertThat(eps(fromPs = 140, basal = 0.42).isAppliedAs(eps(fromPs = 140, basal = 0.40))).isFalse()
    }
}

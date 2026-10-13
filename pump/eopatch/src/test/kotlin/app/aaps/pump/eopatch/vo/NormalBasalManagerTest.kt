package app.aaps.pump.eopatch.vo

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.code.BasalStatus
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.concurrent.TimeUnit

class NormalBasalManagerTest : TestBaseWithProfile() {

    @Mock
    private lateinit var mockPreferences: Preferences

    @Mock
    private lateinit var mockProfile: Profile

    private lateinit var manager: NormalBasalManager

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        manager = NormalBasalManager()

        // Setup mock profile with basal values
        `when`(mockProfile.getBasalValues()).thenReturn(
            arrayOf(
                Profile.ProfileValue(0, 1.0)
            )
        )
        `when`(mockProfile.getBasal(org.mockito.kotlin.any())).thenReturn(1.0)
    }

    @Test
    fun `init should have normal basal with selected status`() {
        assertThat(manager.normalBasal).isNotNull()
        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.SELECTED)
        assertThat(manager.isStarted).isFalse()
    }

    @Test
    fun `isStarted should return true when status is started`() {
        manager.updateBasalStarted()

        assertThat(manager.isStarted).isTrue()
        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.STARTED)
    }

    @Test
    fun `updateBasalPaused should set status to paused`() {
        manager.updateBasalPaused()

        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.PAUSED)
    }

    @Test
    fun `updateBasalSuspended should set status to suspended`() {
        manager.updateBasalSuspended()

        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.SUSPENDED)
        assertThat(manager.isSuspended()).isTrue()
    }

    @Test
    fun `isSuspended should return false when not suspended`() {
        manager.updateBasalStarted()

        assertThat(manager.isSuspended()).isFalse()
    }

    @Test
    fun `updateBasalSelected should set status to selected`() {
        manager.updateBasalStarted()

        manager.updateBasalSelected()

        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.SELECTED)
    }

    @Test
    fun `updateForDeactivation should set status to selected`() {
        manager.updateBasalStarted()

        manager.updateForDeactivation()

        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.SELECTED)
    }

    @Test
    fun `setNormalBasal should convert profile to basal segments`() {
        val profile = mockProfile

        manager.setNormalBasal(profile)

        assertThat(manager.normalBasal.list).isNotEmpty()
        assertThat(manager.normalBasal.list.size).isEqualTo(profile.getBasalValues().size)
    }

    @Test
    fun `setNormalBasal should clear existing segments`() {
        manager.normalBasal.list.add(BasalSegment.create(0, 360, 1.0f))
        val initialSize = manager.normalBasal.list.size

        manager.setNormalBasal(mockProfile)

        // Should be replaced with profile segments
        assertThat(manager.normalBasal.list.size).isNotEqualTo(initialSize)
    }

    @Test
    fun `convertProfileToNormalBasal should create new basal without modifying manager`() {
        val profile = mockProfile
        val originalBasal = manager.normalBasal

        val converted = manager.convertProfileToNormalBasal(profile)

        assertThat(converted).isNotSameInstanceAs(originalBasal)
        assertThat(converted.list.size).isEqualTo(profile.getBasalValues().size)
    }

    @Test
    fun `convertProfileToNormalBasal should handle single segment profile`() {
        val profile = mockProfile // mockProfile from TestBaseWithProfile

        val converted = manager.convertProfileToNormalBasal(profile)

        assertThat(converted.list).isNotEmpty()
        // First segment should start at 0
        assertThat(converted.list[0].start).isEqualTo(0)
    }

    @Test
    fun `convertProfileToNormalBasal should wrap last segment to 1440 minutes`() {
        val profile = mockProfile

        val converted = manager.convertProfileToNormalBasal(profile)

        // Last segment should end at 1440 (24 hours)
        val lastSegment = converted.list[converted.list.size - 1]
        assertThat(lastSegment.end).isEqualTo(1440)
    }

    @Test
    fun `isEqual should return true for matching profile`() {
        val profile = mockProfile
        manager.setNormalBasal(profile)

        assertThat(manager.isEqual(profile)).isTrue()
    }

    @Test
    fun `isEqual should return false for different segment count`() {
        val profile = mockProfile
        manager.setNormalBasal(profile)

        // Modify the basal
        manager.normalBasal.list.add(BasalSegment.create(0, 30, 1.0f))

        assertThat(manager.isEqual(profile)).isFalse()
    }

    @Test
    fun `isEqual should return false for different segment times`() {
        val profile = mockProfile
        manager.setNormalBasal(profile)

        // Modify segment time
        if (manager.normalBasal.list.isNotEmpty()) {
            manager.normalBasal.list[0].start = 30
        }

        assertThat(manager.isEqual(profile)).isFalse()
    }

    @Test
    fun `isEqual should return false for different dose values`() {
        val profile = mockProfile
        manager.setNormalBasal(profile)

        // Modify dose
        if (manager.normalBasal.list.isNotEmpty()) {
            manager.normalBasal.list[0].doseUnitPerHour = 999.0f
        }

        assertThat(manager.isEqual(profile)).isFalse()
    }

    @Test
    fun `isEqual should return false for null profile`() {
        assertThat(manager.isEqual(null)).isFalse()
    }

    @Test
    fun `update should copy basal from other manager`() {
        val other = NormalBasalManager()
        other.setNormalBasal(mockProfile)
        other.updateBasalStarted()

        manager.update(other)

        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.STARTED)
        assertThat(manager.normalBasal.list.size).isEqualTo(other.normalBasal.list.size)
    }

    @Test
    fun `toString should contain key information`() {
        val stringRep = manager.toString()

        assertThat(stringRep).contains("NormalBasalManager")
        assertThat(stringRep).contains("normalBasal=")
    }

    @Test
    fun `observe should return observable`() {
        val observable = manager.observe()

        assertThat(observable).isNotNull()
    }

    @Test
    fun `status transitions should work correctly`() {
        // SELECTED -> STARTED
        manager.updateBasalStarted()
        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.STARTED)

        // STARTED -> PAUSED
        manager.updateBasalPaused()
        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.PAUSED)

        // PAUSED -> SUSPENDED
        manager.updateBasalSuspended()
        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.SUSPENDED)

        // SUSPENDED -> SELECTED
        manager.updateBasalSelected()
        assertThat(manager.normalBasal.status).isEqualTo(BasalStatus.SELECTED)
    }
}

package app.aaps.implementation.profile

import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.verifyNoInteractions

/**
 * Covers [ProfileRepositoryImpl.reorder] — the commit path behind the profile carousel's sort mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryImplTest : TestBaseWithProfile() {

    private fun singleBlock(value: Double): JSONArray =
        JSONArray().put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", value))

    private fun profile(name: String) = SingleProfile(
        name = name,
        mgdl = true,
        ic = singleBlock(15.0),
        isf = singleBlock(100.0),
        basal = singleBlock(0.1),
        targetLow = singleBlock(110.0),
        targetHigh = singleBlock(120.0)
    )

    private fun createSut() = ProfileRepositoryImpl(
        aapsLogger, rh, preferences, Lazy { profileFunction }, profileUtil, activePlugin,
        hardLimits, dateUtil, config, profileStoreProvider, notificationManager
    )

    /** Repository seeded with profiles named A..D, in that order. */
    private suspend fun sutWith(vararg names: String): ProfileRepositoryImpl {
        val sut = createSut()
        names.forEach { sut.add(profile(it)) }
        return sut
    }

    private fun ProfileRepositoryImpl.names(): List<String> = profiles.value.map { it.name }

    @Test
    fun `a valid permutation rearranges the list`() = runTest {
        val sut = sutWith("A", "B", "C", "D")

        // order[newPosition] == oldIndex, so this reads "C, A, D, B".
        val result = sut.reorder(listOf(2, 0, 3, 1))

        assertThat(result.isSuccess).isTrue()
        assertThat(sut.names()).containsExactly("C", "A", "D", "B").inOrder()
    }

    @Test
    fun `reordering moves the existing profile objects rather than copies`() = runTest {
        val sut = sutWith("A", "B", "C")
        val originalLast = sut.profiles.value[2]

        sut.reorder(listOf(2, 1, 0))

        // Callers hold references to these (the editor keeps one alive while editing), so a
        // reorder must not silently swap them for equal-but-different instances.
        assertThat(sut.profiles.value[0]).isSameInstanceAs(originalLast)
    }

    @Test
    fun `an identity order succeeds without touching storage`() = runTest {
        val sut = sutWith("A", "B", "C")
        clearInvocations(preferences)

        val result = sut.reorder(listOf(0, 1, 2))

        assertThat(result.isSuccess).isTrue()
        assertThat(sut.names()).containsExactly("A", "B", "C").inOrder()
        // The point of the short-circuit: a write would bump LocalProfileLastChange and provoke a
        // full profile-store upload to Nightscout and xDrip for a list that did not change.
        verifyNoInteractions(preferences)
    }

    @Test
    fun `an order of the wrong size is rejected`() = runTest {
        val sut = sutWith("A", "B", "C")

        val result = sut.reorder(listOf(1, 0))

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(sut.names()).containsExactly("A", "B", "C").inOrder()
    }

    @Test
    fun `an order with a duplicated index is rejected`() = runTest {
        val sut = sutWith("A", "B", "C")

        // Would otherwise duplicate one profile and drop another.
        val result = sut.reorder(listOf(0, 1, 1))

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(sut.names()).containsExactly("A", "B", "C").inOrder()
    }

    @Test
    fun `an order with an out of range index is rejected`() = runTest {
        val sut = sutWith("A", "B", "C")

        val result = sut.reorder(listOf(0, 1, 5))

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(sut.names()).containsExactly("A", "B", "C").inOrder()
    }

    @Test
    fun `reordering an empty list is a no-op rather than a failure`() = runTest {
        val sut = createSut()

        val result = sut.reorder(emptyList())

        assertThat(result.isSuccess).isTrue()
    }
}

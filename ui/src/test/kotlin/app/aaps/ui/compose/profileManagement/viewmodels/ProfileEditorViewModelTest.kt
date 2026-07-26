package app.aaps.ui.compose.profileManagement.viewmodels

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileErrorType
import app.aaps.core.interfaces.profile.ProfileValidationError
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the "new profile" draft flow added to the editor: a draft is appended via [add] only when
 * valid, never via [replace]; an invalid profile is never persisted from any path; and an external
 * profile-list change must not wipe an in-progress draft.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileEditorViewModelTest : TestBaseWithProfile() {

    @Mock lateinit var protectionCheck: ProtectionCheck

    private val profilesFlow = MutableStateFlow<List<SingleProfile>>(emptyList())
    private lateinit var sut: ProfileEditorViewModel

    private fun singleBlock(value: Double): JSONArray =
        JSONArray().put(JSONObject().put("time", "00:00").put("timeAsSeconds", 0).put("value", value))

    // A well-formed, parseable profile (validity is driven by the validateStructured stub, not content).
    private fun profile(name: String) = SingleProfile(
        name = name,
        mgdl = true,
        ic = singleBlock(15.0),
        isf = singleBlock(100.0),
        basal = singleBlock(0.1),
        targetLow = singleBlock(110.0),
        targetHigh = singleBlock(120.0)
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(profileRepository.profiles).thenReturn(profilesFlow)
        whenever(profileRepository.newDraft()).thenReturn(profile("LocalProfile1"))
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(protectionCheck.isLocked(any())).thenReturn(false)
        sut = ProfileEditorViewModel(aapsLogger, rh, profileRepository, profileFunction, activePlugin, insulin, hardLimits, dateUtil, protectionCheck)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun newDraftSavedAsAddNotReplace() = runTest {
        whenever(profileRepository.add(any())).thenReturn(Result.success(Unit))

        sut.startNewProfileDraft()
        sut.saveProfile()

        verify(profileRepository).add(any())
        verify(profileRepository, never()).replace(any(), any())
    }

    @Test
    fun invalidProfileIsNotPersisted() = runTest {
        whenever(profileRepository.add(any())).thenReturn(Result.success(Unit))
        // Make the draft semantically invalid -> Save must be a no-op (the guard), neither add nor replace.
        whenever(profileRepository.validateStructured(any()))
            .thenReturn(listOf(ProfileValidationError(ProfileErrorType.BASAL, "error in basal values")))

        sut.startNewProfileDraft()
        sut.saveProfile()

        verify(profileRepository, never()).add(any())
        verify(profileRepository, never()).replace(any(), any())
    }

    @Test
    fun existingProfileSavedAsReplaceNotAdd() = runTest {
        whenever(profileRepository.replace(any(), any())).thenReturn(Result.success(Unit))
        profilesFlow.value = listOf(profile("Existing"))

        sut.selectProfile(0)
        sut.saveProfile()

        verify(profileRepository).replace(eq(0), any())
        verify(profileRepository, never()).add(any())
    }

    @Test
    fun externalProfileChangeDoesNotWipeDraft() = runTest {
        whenever(profileRepository.add(any())).thenReturn(Result.success(Unit))
        sut.startNewProfileDraft()
        val draftName = sut.uiState.value.currentProfile?.name

        // An external profile-list change (e.g. an NS push) arrives while the draft is open.
        profilesFlow.value = listOf(profile("PushedFromNs"))

        // The draft survives (its index doesn't exist in the list, so it must not be re-cloned away)...
        assertThat(sut.uiState.value.currentProfile?.name).isEqualTo(draftName)
        // ...and still commits as a new profile.
        sut.saveProfile()
        verify(profileRepository).add(any())
    }
}

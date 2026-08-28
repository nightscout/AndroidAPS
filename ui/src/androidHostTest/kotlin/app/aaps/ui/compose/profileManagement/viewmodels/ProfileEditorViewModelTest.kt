package app.aaps.ui.compose.profileManagement.viewmodels

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileErrorType
import app.aaps.core.interfaces.profile.ProfileValidationError
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.objects.extensions.singleBlock
import app.aaps.core.objects.extensions.singleTargetBlock
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    private val revisionFlow = MutableStateFlow(0L)
    private lateinit var sut: ProfileEditorViewModel

    // A well-formed, parseable profile (validity is driven by the validateStructured stub, not content).
    private fun profile(name: String) = SingleProfile(
        name = name,
        mgdl = true,
        ic = singleBlock(15.0),
        isf = singleBlock(100.0),
        basal = singleBlock(0.1),
        target = singleTargetBlock(110.0, 120.0)
    )

    /**
     * Publish a profile list the way the repository does: list first, then the revision bump. The
     * editor watches the revision, so a test that only set the list would be publishing a change the
     * editor never hears about — and an identical list would not even emit.
     */
    private fun publish(vararg profiles: SingleProfile) {
        profilesFlow.value = profiles.toList()
        revisionFlow.value = revisionFlow.value + 1
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(profileRepository.profiles).thenReturn(profilesFlow)
        whenever(profileRepository.revision).thenReturn(revisionFlow)
        whenever(profileRepository.newDraft()).thenReturn(profile("LocalProfile1"))
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(protectionCheck.isLocked(any())).thenReturn(false)
        sut = ProfileEditorViewModel(aapsLogger, rh, profileRepository, profileFunction, activePlugin, profileUtil, hardLimits, dateUtil, protectionCheck)
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
        publish(profile("Existing"))

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
        publish(profile("PushedFromNs"))

        // The draft survives (its index doesn't exist in the list, so it must not be reloaded away)...
        assertThat(sut.uiState.value.currentProfile?.name).isEqualTo(draftName)
        // ...and still commits as a new profile.
        sut.saveProfile()
        verify(profileRepository).add(any())
    }

    @Test
    fun ownSaveEchoedTwiceDoesNotWipeEditsTypedMeanwhile() = runTest {
        // On a paired client one save produces TWO emits: the local write, and the master's
        // authoritative copy coming back through the sync channel a moment later. Only a foreign
        // change may re-clone the editor.
        whenever(profileRepository.replace(any(), any())).thenReturn(Result.success(Unit))
        val saved = profile("Existing")
        publish(saved)
        sut.selectProfile(0)
        sut.saveProfile()

        // First emit: the local write. The content is identical to what we saved, which is exactly
        // why this rides on the revision counter — the profile list itself does not emit here.
        publish(saved)
        // The user keeps typing while the round-trip is in flight.
        sut.updateProfileName("RenamedWhileInFlight")
        // Second emit: the same content echoed back after the master applied it.
        publish(saved)

        assertThat(sut.uiState.value.currentProfile?.name).isEqualTo("RenamedWhileInFlight")
    }

    @Test
    fun aForeignProfileChangeStillReloadsTheEditor() = runTest {
        whenever(profileRepository.replace(any(), any())).thenReturn(Result.success(Unit))
        publish(profile("Existing"))
        sut.selectProfile(0)
        sut.saveProfile()

        // Different content at our index — somebody else edited this profile.
        publish(profile("ChangedByMaster"))

        assertThat(sut.uiState.value.currentProfile?.name).isEqualTo("ChangedByMaster")
    }

    /**
     * The reason the editor watches [app.aaps.core.interfaces.profile.ProfileRepository.revision]
     * rather than the profile list.
     *
     * Reset re-reads the stored profile and republishes it. When the user's edit happened to bring
     * the profile back to what is already stored, the republished list is structurally equal to the
     * previous one, so the list StateFlow does not emit at all. Watching the list would leave the
     * editor showing the edit it was asked to discard, with Save still offered.
     */
    @Test
    fun resetToAnIdenticalListStillDiscardsTheEdit() = runTest {
        val stored = profile("Existing")
        publish(stored)
        sut.selectProfile(0)

        sut.updateProfileName("TypedButNotSaved")
        assertThat(sut.uiState.value.isEdited).isTrue()

        // reset() reloads from storage and publishes the same list it already had.
        publish(stored)

        assertThat(sut.uiState.value.currentProfile?.name).isEqualTo("Existing")
        assertThat(sut.uiState.value.isEdited).isFalse()
    }

    /** A no-op edit is not an edit: re-entering the value already on screen must not offer Save. */
    @Test
    fun reEnteringTheSameValueDoesNotMarkTheProfileEdited() = runTest {
        publish(profile("Existing"))
        sut.selectProfile(0)

        sut.updateProfileName("Existing")
        sut.updateBasalEntry(0, TimeValue(0, 0.1))

        assertThat(sut.uiState.value.isEdited).isFalse()
    }
}

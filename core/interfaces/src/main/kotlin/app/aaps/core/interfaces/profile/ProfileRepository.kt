package app.aaps.core.interfaces.profile

import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the local profile list (backed by SharedPreferences).
 *
 * Provides:
 *  - reactive observation of the profile list via [profiles] and the by-name JSON
 *    projection via [profile]
 *  - mutex-guarded atomic mutations (bounds-checked, returning [Result])
 *  - a non-suspend [validateStructured] for synchronous validator lambdas, and a
 *    non-suspend [copyFrom] factory
 *
 * All mutating methods are `suspend` — call them from `viewModelScope.launch { ... }` or
 * any coroutine. Reads via [profiles] / [profile] are lock-free.
 */
interface ProfileRepository {

    /**
     * The current profile list. Emits whenever profiles are added, removed, cloned,
     * or replaced (e.g. by an NS push via [loadFromNs]).
     *
     * **Mutability contract:** [SingleProfile] is a mutable holder (its `JSONArray` fields
     * are mutable, and the list spine is a snapshot but the elements are shared). Callers
     * that intend to edit a profile must [SingleProfile.deepClone] first; mutating an
     * element from this flow corrupts repository state and will leak across other
     * collectors. To commit an edited clone back, call [replace].
     */
    val profiles: StateFlow<List<SingleProfile>>

    /**
     * The current [ProfileStore] (by-name JSON projection of [profiles]). Emits whenever the
     * profile list changes — i.e. after every mutation that goes through this repository.
     *
     * This is the reactive replacement for the legacy `EventProfileStoreChanged` RxBus event.
     * Callers that need to react to "profile JSON changed" (e.g. NS upload workers) should
     * collect this flow with `.drop(1)` to ignore the replayed current value on subscription.
     */
    val profile: StateFlow<ProfileStore?>

    /**
     * Clone the profile at [index]. The clone is appended with " copy" suffixed to its name.
     *
     * @return [Result.success] if the clone was created, or [Result.failure] with
     *         [IndexOutOfBoundsException] if [index] is no longer valid (e.g. profiles were
     *         replaced by an NS push between the user's click and confirmation).
     */
    suspend fun clone(index: Int): Result<Unit>

    /**
     * Remove the profile at [index]. If this was the last profile, a fresh default
     * profile is created so the invariant of "at least one profile exists" is preserved.
     *
     * @return [Result.success] if the profile was removed, or [Result.failure] with
     *         [IndexOutOfBoundsException] if [index] is no longer valid.
     */
    suspend fun remove(index: Int): Result<Unit>

    /**
     * Append an existing [profile] to the store. Used by callers that already have a
     * fully constructed profile (e.g. Autotune, ProfileSwitch import, NS profile clone).
     *
     * @return [Result.success] if added, or [Result.failure] propagating any storage exception.
     */
    suspend fun add(profile: SingleProfile): Result<Unit>

    /**
     * Validate the given [profile]. Pure CPU function of the profile + pump/hard-limit DI deps.
     * Non-suspend so it can be called from synchronous validator lambdas (e.g. setup wizard).
     */
    fun validateStructured(profile: SingleProfile): List<ProfileValidationError>

    /**
     * Check whether [profile]'s basal schedule is deliverable by the **currently active** pump
     * (minimum/maximum rate and 30-min vs full-hour granularity) at the given [percentage].
     *
     * This is pump-dependent and **non-blocking**: it does not gate editing, storage or Nightscout
     * sync (those use [validateStructured] / semantic validity). It drives the "won't run on the
     * current pump" warning shown while editing/viewing, and the pre-emptive block in the activation
     * dialog. Returns the pump-specific validation errors, or an empty list when compatible.
     */
    fun validatePumpCompatibility(profile: SingleProfile, percentage: Int = 100): List<ProfileValidationError>

    /**
     * Build an empty, **unpersisted** draft profile (one zero block per field, next free name).
     *
     * Used by the editor's "new profile" flow: the draft lives only in the editor until the user has
     * made it valid and saves it via [add]. Because the editor refuses to save a semantically invalid
     * profile, an incomplete profile never enters the store (so it can't silently block sync). Pure
     * factory — does not mutate the repository.
     */
    fun newDraft(): SingleProfile

    /**
     * Build a [SingleProfile] from a [PureProfile] and desired [newName].
     * If a profile with [newName] already exists in the current store, the timestamp is appended
     * to keep names unique. Pure factory — does not mutate the repository.
     */
    fun copyFrom(pureProfile: PureProfile, newName: String): SingleProfile

    /**
     * Reload the profile list from persistent storage, discarding any in-memory edits.
     * Used by the editor's "reset" action. Mutex-guarded so the clear-then-refill window
     * is invisible to concurrent readers of [profiles].
     */
    suspend fun reset(): Result<Unit>

    /**
     * Import profiles from a remote [ProfileStore] (Nightscout push). Each incoming profile
     * is validated; empty or invalid stores are ignored. Mutex-guarded so the import is
     * serialised against concurrent local mutations and the StateFlow [profiles] reflects
     * the post-import state atomically.
     *
     * @return [Result.success] on completion (even when the store is rejected as invalid),
     *         or [Result.failure] if a storage exception is thrown.
     */
    suspend fun loadFromNs(store: ProfileStore): Result<Unit>

    /**
     * Replace the profile at [index] with [profile] and persist. Used by the editor to commit
     * a snapshot-edited profile back to the store.
     *
     * @return [Result.success] if the replacement was persisted, or [Result.failure] with
     *         [IndexOutOfBoundsException] if [index] is no longer valid (e.g. profiles were
     *         replaced by an NS push while the user was editing).
     */
    suspend fun replace(index: Int, profile: SingleProfile): Result<Unit>
}

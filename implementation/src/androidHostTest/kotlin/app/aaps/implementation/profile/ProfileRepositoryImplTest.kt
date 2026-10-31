package app.aaps.implementation.profile

import app.aaps.core.data.model.data.Block
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.ProfileComposedBooleanKey
import app.aaps.core.keys.ProfileComposedStringKey
import app.aaps.core.keys.ProfileIntKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.objects.extensions.singleBlock
import app.aaps.core.objects.extensions.singleTargetBlock
import app.aaps.core.objects.extensions.toJsonArray
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.TimeZone

/**
 * Covers [ProfileRepositoryImpl.reorder] — the commit path behind the profile carousel's sort mode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryImplTest : TestBaseWithProfile() {

    /**
     * The fixtures below build `org.json` documents, which is what the repository is fed in
     * production - a stored string. Production itself no longer holds `org.json`, so the bridge that
     * used to live in `:core:objects` is here instead, where the fixtures are.
     */
    private fun List<Block>.asJSONArray(): JSONArray = JSONArray(toJsonArray().toString())

    private fun profile(name: String) = SingleProfile(
        name = name,
        mgdl = true,
        ic = singleBlock(15.0),
        isf = singleBlock(100.0),
        basal = singleBlock(0.1),
        target = singleTargetBlock(110.0, 120.0)
    )

    // The document the repository reads on start, and the flow it watches for values arriving from
    // the sync channel. Both are stubbed before the SUT is built because it loads in its init block.
    private var storedPayload: String = ""
    private val syncedPayloads = MutableStateFlow("")

    private fun createSut(): ProfileRepositoryImpl {
        whenever(preferences.get(StringNonKey.LocalProfileData)).thenReturn(storedPayload)
        whenever(preferences.observe(StringNonKey.LocalProfileData)).thenReturn(syncedPayloads)
        return ProfileRepositoryImpl(
            aapsLogger, rh, preferences, { profileFunction }, profileUtil, activePlugin,
            hardLimits, dateUtil, config, { profileStoreProvider() }, notificationManager,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    /** The JSON document as the repository stores it. */
    private fun payload(lastChange: Long, vararg names: String): String =
        JSONObject()
            .put("lastChange", lastChange)
            .put("profiles", JSONArray().apply {
                names.forEach { name ->
                    put(
                        JSONObject()
                            .put("name", name)
                            .put("mgdl", true)
                            .put("ic", singleBlock(15.0).asJSONArray())
                            .put("isf", singleBlock(100.0).asJSONArray())
                            .put("basal", singleBlock(0.1).asJSONArray())
                            .put("targetLow", singleBlock(110.0).asJSONArray())
                            .put("targetHigh", singleBlock(120.0).asJSONArray())
                    )
                }
            })
            .toString()

    /** Every value written to [StringNonKey.LocalProfileData] as a local (sync-announcing) write. */
    private fun localWrites(): List<String> = argumentCaptor<String>().let { captor ->
        verify(preferences, atLeast(0)).put(eq(StringNonKey.LocalProfileData), captor.capture())
        captor.allValues
    }

    /** Every value written to [StringNonKey.LocalProfileData] as an adopted (silent) write. */
    private fun adoptedWrites(): List<String> = argumentCaptor<String>().let { captor ->
        verify(preferences, atLeast(0)).putRemote(eq(StringNonKey.LocalProfileData), captor.capture(), any())
        captor.allValues
    }

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

    // ---------------------------------------------------------------------------------------------
    // Storage format: one JSON document, with the pre-JSON keys kept readable for a downgrade.
    // ---------------------------------------------------------------------------------------------

    /** Stub the pre-JSON per-profile keys so the legacy reader finds [names]. */
    /** A schedule the way a pre-JSON build wrote it. ASCII digits, whole hours, both time fields. */
    private fun legacySchedule(vararg hourToValue: Pair<Int, Double>): String =
        hourToValue.joinToString(",", "[", "]") { (hour, value) ->
            val hh = if (hour < 10) "0$hour" else "$hour"
            """{"time":"$hh:00","timeAsSeconds":${hour * 3600},"value":$value}"""
        }

    /**
     * One profile in the pre-JSON keys, shaped like real data rather than a placeholder.
     *
     * The values are taken from an actual 3.4.x install: mmol/L, several blocks per schedule, and the
     * long decimals that come out of unit conversion. The existing [givenLegacyProfiles] fixture uses
     * one round-numbered block per schedule, which cannot catch a boundary or precision mistake.
     */
    private fun givenRealisticLegacyProfile(name: String) {
        whenever(preferences.get(ProfileIntKey.AmountOfProfiles)).thenReturn(1)
        givenLegacyProfileFields(
            index = 0,
            name = name,
            mgdl = false,
            ic = legacySchedule(0 to 8.1, 7 to 6.0, 10 to 8.0),
            isf = legacySchedule(0 to 9.523809523809524),
            basal = legacySchedule(0 to 1.0, 6 to 1.27, 11 to 1.6300000000000001),
            low = legacySchedule(0 to 5.5, 11 to 6.6000000000000005),
            high = legacySchedule(0 to 5.5, 11 to 7.7)
        )
    }

    /**
     * One profile present in the pre-JSON keys, stubbed the way a real store answers.
     *
     * Every field is stubbed on BOTH accessors on purpose. The loader reads each one with
     * `getIfExists`, because a profile is a group and a missing field drops it; the `get` stubs say
     * the same thing and keep the helper honest if a caller ever reads a field the other way.
     */
    @Suppress("LongParameterList")
    private fun givenLegacyProfileFields(
        index: Int, name: String, mgdl: Boolean, ic: String, isf: String, basal: String, low: String, high: String
    ) {
        whenever(preferences.get(ProfileComposedStringKey.LocalProfileNumberedName, index)).thenReturn(name)
        whenever(preferences.getIfExists(ProfileComposedStringKey.LocalProfileNumberedName, index)).thenReturn(name)
        whenever(preferences.get(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, index)).thenReturn(mgdl)
        whenever(preferences.getIfExists(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, index)).thenReturn(mgdl)
        listOf(
            ProfileComposedStringKey.LocalProfileNumberedIc to ic,
            ProfileComposedStringKey.LocalProfileNumberedIsf to isf,
            ProfileComposedStringKey.LocalProfileNumberedBasal to basal,
            ProfileComposedStringKey.LocalProfileNumberedTargetLow to low,
            ProfileComposedStringKey.LocalProfileNumberedTargetHigh to high
        ).forEach { (key, value) ->
            whenever(preferences.get(key, index)).thenReturn(value)
            whenever(preferences.getIfExists(key, index)).thenReturn(value)
        }
    }

    /**
     * The 3.4.x upgrade: legacy keys in, one JSON document out, nothing altered on the way.
     *
     * This is the highest-risk path in the profile rework - it runs once, silently, on every upgrading
     * install, and a mistake in it corrupts profiles that were fine. It was verified against a real
     * device carrying five such profiles; this pins the same guarantees so a future change cannot undo
     * it. Times, values and the unit flag must survive exactly, including the long decimals, because
     * blocks are stored as durations and rebuilt as start times.
     */
    @Test
    fun `legacy keys migrate into the document without altering any value`() = runTest {
        givenRealisticLegacyProfile("Vsedni den")
        whenever(config.APS).thenReturn(true)

        val sut = createSut()

        assertThat(sut.names()).containsExactly("Vsedni den")
        val migrated = JSONObject(localWrites().last()).getJSONArray("profiles").getJSONObject(0)
        assertThat(migrated.getString("name")).isEqualTo("Vsedni den")
        assertThat(migrated.getBoolean("mgdl")).isFalse()

        fun schedule(key: String) = migrated.getJSONArray(key).let { array ->
            (0 until array.length()).map { array.getJSONObject(it).getInt("timeAsSeconds") to array.getJSONObject(it).getDouble("value") }
        }

        assertThat(schedule("ic")).containsExactly(0 to 8.1, 25200 to 6.0, 36000 to 8.0).inOrder()
        assertThat(schedule("isf")).containsExactly(0 to 9.523809523809524).inOrder()
        assertThat(schedule("basal")).containsExactly(0 to 1.0, 21600 to 1.27, 39600 to 1.6300000000000001).inOrder()
        assertThat(schedule("targetLow")).containsExactly(0 to 5.5, 39600 to 6.6000000000000005).inOrder()
        assertThat(schedule("targetHigh")).containsExactly(0 to 5.5, 39600 to 7.7).inOrder()
    }

    /** The migrated profile must also be readable back, not merely written correctly. */
    @Test
    fun `a migrated profile is usable as a profile`() = runTest {
        givenRealisticLegacyProfile("Vsedni den")
        whenever(config.APS).thenReturn(true)

        val sut = createSut()
        val profile = sut.profiles.value.single()

        assertThat(profile.mgdl).isFalse()
        assertThat(profile.ic).hasSize(3)
        assertThat(profile.basal).hasSize(3)
        assertThat(profile.target).hasSize(2)
        // Durations, not start times: 00:00-07:00 is seven hours.
        assertThat(profile.ic.first().duration).isEqualTo(7 * 3600 * 1000L)
        assertThat(profile.basal.last().amount).isEqualTo(1.6300000000000001)
    }

    /**
     * The store a 3.3 backup leaves behind: the profile COUNT arrives, the content does not.
     *
     * `ProfileIntKey.AmountOfProfiles` is `LocalProfile_profiles`, a registered exportable key, so an
     * import writes it. The per-profile names 3.3 actually wrote - `LocalProfile_0_isf` and friends -
     * are not keys this build knows (it uses `LocalProfile_isf_0`), so `PreferenceKeyResolver` returns
     * null for them, `PreferenceImportApplier` counts them in `unresolved`, and they are never written.
     *
     * Every content read therefore falls back to the key's `defaultValue`, which is what
     * `PreferencesImpl.get` returns for an absent key. The defaults are used here rather than their
     * literal text so this stays honest if a default ever changes.
     */
    private fun givenProfileCountWithoutContent(count: Int) {
        whenever(preferences.get(ProfileIntKey.AmountOfProfiles)).thenReturn(count)
        repeat(count) { i ->
            whenever(preferences.get(ProfileComposedStringKey.LocalProfileNumberedName, i))
                .thenReturn(ProfileComposedStringKey.LocalProfileNumberedName.defaultValue)
            whenever(preferences.get(ProfileComposedBooleanKey.LocalProfileNumberedMgdl, i))
                .thenReturn(ProfileComposedBooleanKey.LocalProfileNumberedMgdl.defaultValue)
            whenever(preferences.get(ProfileComposedStringKey.LocalProfileNumberedIc, i))
                .thenReturn(ProfileComposedStringKey.LocalProfileNumberedIc.defaultValue)
            whenever(preferences.get(ProfileComposedStringKey.LocalProfileNumberedIsf, i))
                .thenReturn(ProfileComposedStringKey.LocalProfileNumberedIsf.defaultValue)
            whenever(preferences.get(ProfileComposedStringKey.LocalProfileNumberedBasal, i))
                .thenReturn(ProfileComposedStringKey.LocalProfileNumberedBasal.defaultValue)
            whenever(preferences.get(ProfileComposedStringKey.LocalProfileNumberedTargetLow, i))
                .thenReturn(ProfileComposedStringKey.LocalProfileNumberedTargetLow.defaultValue)
            whenever(preferences.get(ProfileComposedStringKey.LocalProfileNumberedTargetHigh, i))
                .thenReturn(ProfileComposedStringKey.LocalProfileNumberedTargetHigh.defaultValue)
        }
    }

    /**
     * A profile count with no content behind it must produce NO profile.
     *
     * Without this, the defaults do the talking: the name default is the CONSTANT "LocalProfile0", so
     * every index answers the same name and `loadFromLegacyKeysInternal`'s duplicate-name skip folds
     * however many profiles the file claimed into one; and the schedule defaults are all `value: 0`,
     * so that one profile carries zero ISF, zero IC, zero basal and zero targets. It does not look
     * empty to the user, it looks like a real profile.
     *
     * On a master it is worse than a local mess: `loadSettingsInternal` follows the legacy read with
     * `if (profilesList.isNotEmpty() && config.APS) storeSettingsInternal(...)`, which writes the
     * fabricated profile into [StringNonKey.LocalProfileData] as a LOCAL write - so it is published on
     * the sync channel to every paired client. The repository already carries a note that a
     * zero-seeded profile "would silently block the whole profile-store sync until edited (see #4872)".
     *
     * The trigger is not only an import. Any partial arrival of the group does it - a truncated file,
     * a hand-edited one, or the next key rename - which is why the guard belongs here and not in the
     * import.
     */
    @Test
    fun `a profile count with no content must not fabricate a profile`() = runTest {
        givenProfileCountWithoutContent(count = 2)
        whenever(config.APS).thenReturn(true)

        val sut = createSut()

        assertThat(sut.names()).isEmpty()
        assertThat(localWrites()).isEmpty()
    }

    private fun givenLegacyProfiles(vararg names: String) {
        whenever(preferences.get(ProfileIntKey.AmountOfProfiles)).thenReturn(names.size)
        names.forEachIndexed { i, name ->
            givenLegacyProfileFields(
                index = i,
                name = name,
                mgdl = true,
                ic = singleBlock(15.0).asJSONArray().toString(),
                isf = singleBlock(100.0).asJSONArray().toString(),
                basal = singleBlock(0.1).asJSONArray().toString(),
                low = singleBlock(110.0).asJSONArray().toString(),
                high = singleBlock(120.0).asJSONArray().toString()
            )
        }
    }

    /**
     * A profile missing ONE field must be dropped, not completed from defaults.
     *
     * This is the dangerous half of the same bug as the test above, and the harder one to spot. There
     * the profile was obviously junk - one entry, a default name, every schedule zero. Here the user
     * gets a profile with the name they recognise, the carb ratio they set, the basal they set, the
     * targets they set, and an ISF of zero, because `ProfileComposedStringKey`'s default is
     * `[{"time":"00:00","timeAsSeconds":0,"value":0}]`. Nothing is logged by the parser, because that
     * default parses perfectly well.
     *
     * Any partial write reaches it: an import that could rename six of a profile's seven names, a
     * truncated file, a store edited by hand. The keys have always been written together, so a missing
     * one means the group is broken.
     */
    @Test
    fun `a legacy profile missing one schedule is dropped, not zero-filled`() = runTest {
        givenLegacyProfiles("Adult")
        whenever(preferences.getIfExists(ProfileComposedStringKey.LocalProfileNumberedIsf, 0)).thenReturn(null)
        whenever(config.APS).thenReturn(true)

        val sut = createSut()

        assertThat(sut.names()).isEmpty()
        assertThat(localWrites()).isEmpty()
    }

    @Test
    fun `saving writes one JSON document and stops writing the legacy keys`() = runTest {
        val sut = createSut()

        sut.add(profile("A"))

        val stored = JSONObject(localWrites().last())
        assertThat(stored.getJSONArray("profiles").getJSONObject(0).getString("name")).isEqualTo("A")
        // The legacy keys are frozen, not updated: they exist so a downgrade still finds profiles.
        verify(preferences, never()).put(eq(ProfileIntKey.AmountOfProfiles), any<Int>())
    }

    @Test
    fun `a stored JSON document is loaded on start`() = runTest {
        storedPayload = payload(lastChange = 1_000L, "A", "B")
        whenever(preferences.get(LongNonKey.LocalProfileLastChange)).thenReturn(1_000L)

        assertThat(createSut().names()).containsExactly("A", "B").inOrder()
    }

    @Test
    fun `legacy keys are read when there is no JSON document, and converted on a master`() = runTest {
        givenLegacyProfiles("Old")
        whenever(config.APS).thenReturn(true)

        val sut = createSut()

        assertThat(sut.names()).containsExactly("Old")
        assertThat(JSONObject(localWrites().last()).getJSONArray("profiles").length()).isEqualTo(1)
    }

    @Test
    fun `a client reads legacy keys but never converts them`() = runTest {
        givenLegacyProfiles("Old")
        whenever(config.APS).thenReturn(false)

        val sut = createSut()

        assertThat(sut.names()).containsExactly("Old")
        // Converting on a client would publish its stale local list back to the master.
        assertThat(localWrites()).isEmpty()
    }

    @Test
    fun `legacy keys win when an older build edited profiles after the JSON was written`() = runTest {
        storedPayload = payload(lastChange = 1_000L, "FromJson")
        givenLegacyProfiles("FromLegacy")
        // Both formats stamp LocalProfileLastChange; a newer stamp than the document carries means
        // an older build wrote the legacy keys after this build last wrote the JSON.
        whenever(preferences.get(LongNonKey.LocalProfileLastChange)).thenReturn(2_000L)

        assertThat(createSut().names()).containsExactly("FromLegacy")
    }

    @Test
    fun `loading a stored document must not reach into ProfileFunction`() = runTest {
        storedPayload = payload(lastChange = 1_000L, "A")
        whenever(preferences.get(LongNonKey.LocalProfileLastChange)).thenReturn(1_000L)

        createSut()

        verify(profileFunction, never()).getUnits()
    }

    @Test
    fun `a document the stamp check rejects is still used when there is nothing else`() = runTest {
        storedPayload = payload(lastChange = 1_000L, "FromJson")
        // Stamp ahead of the document (an interrupted write, or an older build that bumped it) and no
        // legacy keys to fall back on — the situation of a client that only ever received its list
        // over the sync channel. Showing no profiles at all would be the worst possible answer.
        whenever(preferences.get(LongNonKey.LocalProfileLastChange)).thenReturn(2_000L)

        assertThat(createSut().names()).containsExactly("FromJson")
    }

    @Test
    fun `the document is written before the stamp that dates it`() = runTest {
        val sut = createSut()
        clearInvocations(preferences)

        sut.add(profile("A"))

        // A crash between the two writes must leave the document looking NEWER than the stamp, never
        // older — otherwise the next load distrusts the freshest data it has.
        val order = inOrder(preferences)
        order.verify(preferences).put(eq(StringNonKey.LocalProfileData), any<String>())
        order.verify(preferences).put(eq(LongNonKey.LocalProfileLastChange), any<Long>())
    }

    @Test
    fun `a damaged entry is skipped without losing the rest of the list`() = runTest {
        storedPayload = JSONObject(payload(1_000L, "Good"))
            .also { it.getJSONArray("profiles").put(JSONObject().put("mgdl", true)) } // no name
            .toString()
        whenever(preferences.get(LongNonKey.LocalProfileLastChange)).thenReturn(1_000L)

        assertThat(createSut().names()).containsExactly("Good")
    }

    /**
     * Pins a deliberate difference from the `org.json` reader this used to use.
     *
     * Android's `optString` turned a JSON `null` into the four-character string "null", so an entry
     * with a null name became a profile actually called "null" - which was then written back and
     * synced on to every client. Reading a JSON `null` as "no name" makes it a damaged entry, and
     * damaged entries are skipped.
     */
    @Test
    fun `an entry whose name is JSON null is skipped rather than named null`() = runTest {
        storedPayload = JSONObject(payload(1_000L, "Good"))
            .also { it.getJSONArray("profiles").put(JSONObject().put("name", JSONObject.NULL).put("mgdl", true)) }
            .toString()
        whenever(preferences.get(LongNonKey.LocalProfileLastChange)).thenReturn(1_000L)

        assertThat(createSut().names()).containsExactly("Good")
    }

    // ---------------------------------------------------------------------------------------------
    // Sync: the list travels as one Bidirectional preference, so writes must say where they came from.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a document arriving from the sync channel is adopted`() = runTest {
        val sut = createSut()
        sut.add(profile("Mine"))

        syncedPayloads.value = payload(lastChange = 5_000L, "FromMaster")

        assertThat(sut.names()).containsExactly("FromMaster")
        // Both Nightscout gates (import and upload) read this stamp, so an adopted list must move it.
        verify(preferences).put(LongNonKey.LocalProfileLastChange, 5_000L)
    }

    @Test
    fun `the echo of our own write is not adopted a second time`() = runTest {
        val sut = createSut()
        sut.add(profile("Mine"))
        val listAfterSave = sut.profiles.value

        // What comes back on a paired client after the master applied our edit.
        syncedPayloads.value = localWrites().last()

        assertThat(sut.profiles.value).isSameInstanceAs(listAfterSave)
    }

    @Test
    fun `a Nightscout store is adopted without announcing it to the sync channel`() = runTest {
        val sut = createSut()

        sut.loadFromNs(getValidProfileStore())

        assertThat(sut.profiles.value).isNotEmpty()
        // put() would publish the list; a store we merely took over must not travel back out.
        assertThat(adoptedWrites()).isNotEmpty()
        assertThat(localWrites()).isEmpty()
    }

    /**
     * A store Nightscout pushed but we refused changes nothing, so it must not count as a mutation.
     *
     * [ProfileRepositoryImpl.revision] means "something happened", and the profile editor reloads its
     * working copy on every bump — so bumping here would throw away edits the user was in the middle
     * of typing, for an event that did not touch a single profile. The old code got this right by
     * accident: profiles were compared by identity, so re-publishing the same list simply did not emit.
     */
    @Test
    fun `a rejected Nightscout store does not count as a mutation`() = runTest {
        val sut = createSut()
        sut.add(profile("Mine"))
        val revisionBefore = sut.revision.value
        val listBefore = sut.profiles.value

        // An empty store has no profile to accept, so loadFromStoreInternal rejects it.
        sut.loadFromNs(mock<ProfileStore>().also { whenever(it.getProfileList()).thenReturn(ArrayList()) })

        assertThat(sut.revision.value).isEqualTo(revisionBefore)
        assertThat(sut.profiles.value).isSameInstanceAs(listBefore)
    }

    /**
     * The published store always carries a numeric `date`.
     *
     * Nightscout v3 needs that field, and both sync selectors now read it straight from the store.
     * They used to patch it in when absent - a branch that could never fire, because this is the only
     * producer and it writes `date` unconditionally. Deleting an unreachable guard is only safe if the
     * invariant it guarded is pinned somewhere reachable, which is what this is.
     */
    @Test
    fun `the published store always carries a numeric date`() = runTest {
        val sut = createSut()
        assertThat(sut.profile.value?.getData()?.get("date")?.jsonPrimitive?.longOrNull).isNotNull()

        // And still after a mutation rebuilds it.
        sut.add(profile("Mine"))

        assertThat(sut.profile.value?.getData()?.get("date")?.jsonPrimitive?.longOrNull).isNotNull()
    }

    /**
     * Pins the `timezone` field of the published store.
     *
     * Nothing else asserted this field, and it is uploaded to Nightscout, so it is the one value in
     * the store that could change quietly if the zone lookup is ever swapped for another one that
     * looks equivalent. The oracle is the platform default on purpose: the field must keep saying
     * what the device thinks its zone is, whichever library reads it.
     */
    @Test
    fun `the published store carries the system time zone`() = runTest {
        val sut = createSut()
        sut.add(profile("Mine"))

        val zone = sut.profile.value?.getData()
            ?.get("store")?.jsonObject
            ?.get("Mine")?.jsonObject
            ?.get("timezone")?.jsonPrimitive?.content

        assertThat(zone).isEqualTo(TimeZone.getDefault().id)
    }

    /** The accepted case still bumps, otherwise the editor would never notice an NS push. */
    @Test
    fun `an accepted Nightscout store does count as a mutation`() = runTest {
        val sut = createSut()
        val revisionBefore = sut.revision.value

        sut.loadFromNs(getValidProfileStore())

        assertThat(sut.revision.value).isGreaterThan(revisionBefore)
    }
}

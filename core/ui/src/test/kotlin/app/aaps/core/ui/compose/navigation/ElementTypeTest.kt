package app.aaps.core.ui.compose.navigation

import app.aaps.core.interfaces.protection.ProtectionCheck
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Invariant tests on the [ElementType] enum itself. Pure data — no Compose or Android.
 *
 * The `searchableEntries` companion is `by lazy` so we also implicitly check that the lazy
 * initializer holds across repeated calls.
 */
class ElementTypeTest {

    @Test
    fun searchableEntries_matchesEntriesFilteredBySearchableFlag() {
        val expected = ElementType.entries.filter { it.searchable }
        assertThat(ElementType.searchableEntries).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun searchableEntries_isStableAcrossCalls() {
        // `by lazy` should return the same instance every time.
        assertThat(ElementType.searchableEntries).isSameInstanceAs(ElementType.searchableEntries)
    }

    @Test
    fun searchableEntries_isNotEmpty() {
        // A regression where every searchable=true is dropped would cause the global search UI to
        // silently return nothing; the only way that's correct is if we deliberately strip the
        // flag from every enum value, which would be a major intentional change.
        assertThat(ElementType.searchableEntries).isNotEmpty()
    }

    @Test
    fun searchableEntries_neverIncludeInternalCategory() {
        // INTERNAL is for non-user-facing utilities — they must not surface in search.
        val internalSearchables = ElementType.searchableEntries.filter { it.category == ElementCategory.INTERNAL }
        assertThat(internalSearchables).isEmpty()
    }

    @Test
    fun defaultCategory_isInternal() {
        // Entries declared without a category should fall through to INTERNAL. If the default
        // changes, this is a breaking shift in element classification and must be intentional.
        // COB / SENSITIVITY / USER_ENTRY / LOOP / AAPS / EXIT are declared without a category.
        assertThat(ElementType.COB.category).isEqualTo(ElementCategory.INTERNAL)
        assertThat(ElementType.SENSITIVITY.category).isEqualTo(ElementCategory.INTERNAL)
        assertThat(ElementType.USER_ENTRY.category).isEqualTo(ElementCategory.INTERNAL)
        assertThat(ElementType.LOOP.category).isEqualTo(ElementCategory.INTERNAL)
        assertThat(ElementType.AAPS.category).isEqualTo(ElementCategory.INTERNAL)
        assertThat(ElementType.EXIT.category).isEqualTo(ElementCategory.INTERNAL)
    }

    @Test
    fun defaultProtection_isNone() {
        // Entries that don't explicitly request protection must not silently inherit a stricter
        // default. Verifies it for a representative non-protected entry.
        assertThat(ElementType.CGM_DEX.protection).isEqualTo(ProtectionCheck.Protection.NONE)
        assertThat(ElementType.ANNOUNCEMENT.protection).isEqualTo(ProtectionCheck.Protection.NONE)
    }

    @Test
    fun protectedTreatmentElements_requireBolusAuth() {
        // The treatment family must stay behind the bolus auth gate so unauthorized users can't
        // deliver insulin via the dialog. Pin the contract.
        val expectedBolusProtected = setOf(
            ElementType.INSULIN,
            ElementType.CARBS,
            ElementType.BOLUS_WIZARD,
            ElementType.QUICK_WIZARD,
            ElementType.TREATMENT,
            ElementType.TEMP_BASAL,
            ElementType.EXTENDED_BOLUS,
            ElementType.CANNULA_CHANGE,
            ElementType.FILL
        )
        expectedBolusProtected.forEach { type ->
            assertThat(type.protection).isEqualTo(ProtectionCheck.Protection.BOLUS)
        }
    }

    @Test
    fun settingsLikeElements_requirePreferencesAuth() {
        val expectedPreferences = setOf(
            ElementType.SETTINGS,
            ElementType.SETUP_WIZARD,
            ElementType.MAINTENANCE,
            ElementType.CONFIGURATION,
            ElementType.SCENE_MANAGEMENT
        )
        expectedPreferences.forEach { type ->
            assertThat(type.protection).isEqualTo(ProtectionCheck.Protection.PREFERENCES)
        }
    }

    @Test
    fun navigationCategory_entries_areAllSearchable() {
        // The navigation drawer surfaces these via global search; missing one would make it
        // unreachable from search.
        val navUnreachable = ElementType.entries
            .filter { it.category == ElementCategory.NAVIGATION }
            .filterNot { it.searchable }
        assertThat(navUnreachable).isEmpty()
    }
}

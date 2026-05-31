package app.aaps.core.ui.compose.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Structural guard for the non-`@Composable` extensions in `ElementTypeStyle.kt`. Adding a new
 * [ElementType] without updating the icon/label/description `when` branches would compile-fail
 * Kotlin's exhaustiveness check, but the more interesting bugs are subtler:
 *  - a searchable element with no label/description (user-facing identifier missing)
 *  - an entry mapped to label `0` outside the documented "dynamic label" set
 * These tests catch those drifts.
 */
class ElementTypeStyleTest {

    /**
     * ElementTypes that intentionally return `0` from [labelResId] because the label is computed
     * at runtime (e.g. user-named QuickWizard / Scene, plugin-named Automation).
     */
    private val typesWithDynamicLabel = setOf(
        ElementType.QUICK_WIZARD,
        ElementType.AUTOMATION,
        ElementType.SCENE
    )

    /**
     * ElementTypes that intentionally return `0` from [descriptionResId] — system-level entries
     * that don't surface a long-form description in the UI.
     */
    private val typesWithoutDescription = setOf(
        ElementType.QUICK_WIZARD,
        ElementType.RUNNING_MODE,
        ElementType.AUTOMATION,
        ElementType.PUMP,
        ElementType.SETTINGS,
        ElementType.COB,
        ElementType.SENSITIVITY,
        ElementType.USER_ENTRY,
        ElementType.LOOP,
        ElementType.AAPS,
        ElementType.EXIT
    )

    @Test
    fun everyElementType_hasIcon() {
        // Calling icon() on every entry forces the `when` to be exhaustive at runtime, not just
        // at compile time — protects against any future refactor that loses exhaustiveness.
        ElementType.entries.forEach { type -> assertThat(type.icon()).isNotNull() }
    }

    @Test
    fun typesWithZeroLabel_matchDocumentedDynamicSet() {
        val actualZero = ElementType.entries.filter { it.labelResId() == 0 }.toSet()
        assertThat(actualZero).isEqualTo(typesWithDynamicLabel)
    }

    @Test
    fun typesWithZeroDescription_matchDocumentedSet() {
        val actualZero = ElementType.entries.filter { it.descriptionResId() == 0 }.toSet()
        assertThat(actualZero).isEqualTo(typesWithoutDescription)
    }

    @Test
    fun searchableEntries_haveDisplayableLabel() {
        // A search hit with no label and no dynamic-label fallback would show as a blank row.
        val blank = ElementType.searchableEntries.filter {
            it.labelResId() == 0 && it !in typesWithDynamicLabel
        }
        assertThat(blank).isEmpty()
    }
}

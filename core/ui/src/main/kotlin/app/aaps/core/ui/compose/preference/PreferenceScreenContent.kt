package app.aaps.core.ui.compose.preference

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.core.os.bundleOf

/**
 * Hierarchy level for preference sections.
 * TOP_LEVEL sections collapse all other top-level sections (accordion across plugins).
 * SUB_SECTION sections collapse only siblings under the same parent.
 */
enum class SectionLevel {

    TOP_LEVEL,
    SUB_SECTION
}

/**
 * State holder for collapsible preference sections.
 * Tracks which sections are expanded and persists across configuration changes.
 * Uses accordion behavior: only one section can be expanded at a time within the same hierarchy level.
 */
class PreferenceSectionState(
    private val expandedSections: SnapshotStateMap<String, Boolean> = mutableStateMapOf(),
    private val sectionLevels: MutableMap<String, SectionLevel> = mutableMapOf(),
    private val sectionParents: MutableMap<String, String> = mutableMapOf()
) {

    /**
     * Check if a section is expanded (default: false - collapsed)
     */
    fun isExpanded(sectionKey: String): Boolean = expandedSections[sectionKey] ?: false

    /**
     * Toggle the expanded state of a section.
     * Expanding a section collapses only sibling sections (same hierarchy level).
     *
     * @param sectionKey Unique key for this section
     * @param level Hierarchy level of this section
     * @param parentKey Parent section key (used for sub-section sibling detection)
     */
    fun toggle(sectionKey: String, level: SectionLevel = SectionLevel.TOP_LEVEL, parentKey: String? = null) {
        // Register level and parent
        sectionLevels[sectionKey] = level
        if (parentKey != null) sectionParents[sectionKey] = parentKey

        val newState = !isExpanded(sectionKey)

        if (newState) {
            // Collapse only sibling sections (convert to list to avoid concurrent modification)
            expandedSections.keys.toList().forEach { key ->
                if (key != sectionKey) {
                    val keyLevel = sectionLevels[key] ?: SectionLevel.TOP_LEVEL

                    val shouldCollapse = when {
                        // Top-level sections collapse all other top-level sections
                        level == SectionLevel.TOP_LEVEL && keyLevel == SectionLevel.TOP_LEVEL -> true
                        // Sub-sections collapse only siblings under the same parent
                        level == SectionLevel.SUB_SECTION && keyLevel == SectionLevel.SUB_SECTION
                            && sectionParents[key] == parentKey                               -> true

                        else                                                                  -> false
                    }

                    if (shouldCollapse) {
                        expandedSections[key] = false
                    }
                }
            }
        }

        expandedSections[sectionKey] = newState
    }

    companion object {

        val Saver: Saver<PreferenceSectionState, Bundle> = Saver(
            save = { state ->
                bundleOf(
                    *state.expandedSections.map { (k, v) -> k to v }.toTypedArray()
                )
            },
            restore = { bundle ->
                PreferenceSectionState(
                    expandedSections = mutableStateMapOf<String, Boolean>().apply {
                        bundle.keySet().forEach { key ->
                            put(key, bundle.getBoolean(key))
                        }
                    }
                )
            }
        )
    }
}

/**
 * Remember and save preference section state across configuration changes.
 * Uses accordion behavior by default.
 */
@Composable
fun rememberPreferenceSectionState(): PreferenceSectionState {
    return rememberSaveable(saver = PreferenceSectionState.Saver) {
        PreferenceSectionState()
    }
}

package app.aaps.ui.compose.scenes

import app.aaps.core.ui.compose.icons.IcActivity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercise has one icon, the runner (#5443). An exercise scene showed a runner in its banner, but the
 * Exercise treatment it wrote was a swimmer on the graph.
 */
class SceneIconsTest {

    @Test
    fun theExerciseSceneTemplateUsesTheExerciseTreatmentIcon() {
        assertEquals(IcActivity, SceneIcons.fromKey(SceneTemplate.EXERCISE.icon).icon)
    }

    /** The swimmer entry is gone from the picker. A scene saved with its key shows the runner, not the fallback star. */
    @Test
    fun aSceneSavedWithTheOldExerciseKeyShowsTheRunner() {
        assertEquals(IcActivity, SceneIcons.fromKey("aaps_activity").icon)
    }

    @Test
    fun thePickerOffersEveryIconOnce() {
        val icons = SceneIcons.categories.flatMap { it.icons }.map { it.icon }

        assertEquals(icons.size, icons.toSet().size)
    }
}

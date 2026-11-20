package app.aaps.core.interfaces.navigation

import app.aaps.core.keys.interfaces.ClientRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins which client role each element needs.
 *
 * The master checks the role on every command, so a wrong entry here does not let a client do more. It
 * does show a control the master then refuses, or hides one the client may use. A change to this table
 * is a change to what each role means, so it has to be made on purpose.
 */
class ElementTypeRoleTest {

    private val mealAssistant = setOf(
        ElementType.INSULIN, ElementType.CARBS, ElementType.BOLUS_WIZARD, ElementType.QUICK_WIZARD, ElementType.TREATMENT
    )

    private val caregiver = setOf(
        ElementType.CALIBRATION,
        ElementType.TEMP_TARGET_MANAGEMENT, ElementType.INSULIN_MANAGEMENT, ElementType.QUICK_WIZARD_MANAGEMENT, ElementType.FOOD_MANAGEMENT,
        ElementType.BG_CHECK, ElementType.NOTE, ElementType.EXERCISE, ElementType.QUESTION, ElementType.ANNOUNCEMENT,
        ElementType.SENSOR_INSERT, ElementType.BATTERY_CHANGE, ElementType.CANNULA_CHANGE, ElementType.FILL, ElementType.SITE_ROTATION,
        ElementType.TEMP_BASAL, ElementType.EXTENDED_BOLUS,
        ElementType.AUTOMATION_MANAGEMENT, ElementType.SCENE, ElementType.SCENE_MANAGEMENT, ElementType.RUNNING_MODE
    )

    @Test
    fun `each element needs the role the table says`() {
        ElementType.entries.forEach { element ->
            val expected = when (element) {
                in mealAssistant -> ClientRole.MealAssistant
                in caregiver     -> ClientRole.Caregiver
                else             -> ClientRole.ReadOnly
            }
            assertEquals(expected, element.role, element.name)
        }
    }

    // Admin is what a client can do beyond Caregiver: presets and settings. Those are screens that both use and
    // define, so they decide inside the screen; no element as a whole needs Admin.
    @Test
    fun `no element as a whole needs Admin`() {
        assertTrue(ElementType.entries.none { it.role == ClientRole.Admin })
    }

    @Test
    fun `roles are a ladder`() {
        assertTrue(ClientRole.Admin.allows(ClientRole.Caregiver))
        assertTrue(ClientRole.Caregiver.allows(ClientRole.MealAssistant))
        assertTrue(ClientRole.MealAssistant.allows(ClientRole.ReadOnly))
        assertTrue(ClientRole.MealAssistant.allows(ClientRole.MealAssistant))
        assertFalse(ClientRole.MealAssistant.allows(ClientRole.Caregiver))
        assertFalse(ClientRole.ReadOnly.allows(ClientRole.MealAssistant))
        assertFalse(ClientRole.Caregiver.allows(ClientRole.Admin))
    }

    @Test
    fun `a stored role name reads back as that role`() {
        ClientRole.entries.forEach { assertEquals(it, ClientRole.of(it.name)) }
    }

    // A client stored before roles existed has no role and keeps the full control it had.
    @Test
    fun `no stored role is Admin`() {
        assertEquals(ClientRole.Admin, ClientRole.of(null))
        assertEquals(ClientRole.Admin, ClientRole.of(""))
    }

    // A role from a newer build, read by an older one: give the least, never the most.
    @Test
    fun `an unknown role is read-only`() {
        assertEquals(ClientRole.ReadOnly, ClientRole.of("Helper"))
        assertEquals(ClientRole.ReadOnly, ClientRole.of("admin"))
    }
}

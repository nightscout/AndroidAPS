package app.aaps.plugins.source.compose

import app.aaps.core.interfaces.resources.formatTemplate
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.source.SourceStrings
import app.aaps.plugins.source.SourceStringsValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every line of the sensor start screen that takes arguments.
 *
 * A wrong **id** cannot happen - `SourceStrings` is generated from `strings.xml`, so it would not
 * compile. A wrong argument **count or order** compiles perfectly and is only seen by the person
 * reading the screen, because the resolvers catch the format error and answer with a fallback. So
 * each template is filled here with the same number of arguments the screen passes - of the same
 * types - and the result has to carry every one of them with no placeholder left over.
 *
 * `allArgumentsLand` is the assertion, and the last test in this file is the test of it: a call
 * with one argument too few must fail it.
 */
class DexcomOnePlusStartStringsTest {

    private fun template(ref: TextRef): String {
        val named = ref as TextRef.Named
        return assertNotNull(
            SourceStringsValues.textOf(named.name, null),
            "no English text for ${named.name}",
        )
    }

    /** Fills [ref] with [args] and checks every one of them is in the result and nothing is left. */
    private fun allArgumentsLand(ref: TextRef, vararg args: Any): String {
        val name = (ref as TextRef.Named).name
        val text = formatTemplate(template(ref), args.toList())
        assertTrue(!text.contains('%'), "$name left a placeholder unfilled: $text")
        var at = -1
        for (arg in args) {
            val found = text.indexOf(arg.toString(), startIndex = at + 1)
            assertTrue(found > at, "$name does not carry $arg in the expected order: $text")
            at = found
        }
        return text
    }

    @Test
    fun `the parsed applicator line carries the code and then the serial`() {
        val text = allArgumentsLand(SourceStrings.dexcom_oneplus_applicator_parsed, "1234", "ABCDEF")
        assertTrue(text.indexOf("1234") < text.indexOf("ABCDEF"), text)
    }

    @Test
    fun `the last MAC hint carries the address`() {
        allArgumentsLand(SourceStrings.dexcom_oneplus_last_mac_hint, "AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `the connect target line carries the sensor name`() {
        allArgumentsLand(SourceStrings.dexcom_oneplus_connect_target, "DX02ABCD")
    }

    @Test
    fun `the idle scan chip carries the number of sensors found`() {
        allArgumentsLand(SourceStrings.dexcom_oneplus_scan_idle, 2)
    }

    @Test
    fun `the signal reading carries a negative dBm value`() {
        allArgumentsLand(SourceStrings.dexcom_oneplus_scan_signal, -67)
    }

    @Test
    fun `the step counter carries the current step and then the total`() {
        val text = allArgumentsLand(SourceStrings.cgm_step_counter, 2, 4)
        assertTrue(text.indexOf("2") < text.indexOf("4"), text)
    }

    @Test
    fun `every other line of this screen takes no arguments at all`() {
        // The ones left out are the six above. If a plain line ever gains a placeholder this fails
        // here rather than on a phone.
        val plain = listOf(
            SourceStrings.cgm_help_collapse,
            SourceStrings.cgm_help_expand,
            SourceStrings.cgm_nav_close,
            SourceStrings.dexcom_oneplus_applicator_gs1_hint,
            SourceStrings.dexcom_oneplus_applicator_gs1_label,
            SourceStrings.dexcom_oneplus_applicator_serial_none,
            SourceStrings.dexcom_oneplus_connect_follow,
            SourceStrings.dexcom_oneplus_device_required,
            SourceStrings.dexcom_oneplus_dexcom_recovery_warning,
            SourceStrings.dexcom_oneplus_help_before_pairing,
            SourceStrings.dexcom_oneplus_help_how_to_start,
            SourceStrings.dexcom_oneplus_help_permissions,
            SourceStrings.dexcom_oneplus_help_slot,
            SourceStrings.cgm_nav_back,
            SourceStrings.dexcom_oneplus_new_sensor_code_warning,
            SourceStrings.dexcom_oneplus_pairing_code_invalid,
            SourceStrings.dexcom_oneplus_pairing_code_required,
            SourceStrings.dexcom_oneplus_permissions_hint,
            SourceStrings.dexcom_oneplus_permissions_needed,
            SourceStrings.dexcom_oneplus_request_permissions,
            SourceStrings.dexcom_oneplus_scan_empty,
            SourceStrings.dexcom_oneplus_scan_hint,
            SourceStrings.dexcom_oneplus_scan_last_used,
            SourceStrings.dexcom_oneplus_scan_pick_hint,
            SourceStrings.dexcom_oneplus_scan_rescan,
            SourceStrings.dexcom_oneplus_scan_scanning,
            SourceStrings.dexcom_oneplus_scan_start,
            SourceStrings.dexcom_oneplus_scan_stop,
            SourceStrings.dexcom_oneplus_scan_unnamed,
            SourceStrings.dexcom_oneplus_section_pairing_code,
            SourceStrings.dexcom_oneplus_section_sensors_nearby,
            SourceStrings.dexcom_oneplus_slot_conflict_dismiss,
            SourceStrings.dexcom_oneplus_slot_conflict_message,
            SourceStrings.dexcom_oneplus_slot_conflict_promote,
            SourceStrings.dexcom_oneplus_slot_conflict_restart,
            SourceStrings.dexcom_oneplus_slot_conflict_title,
            SourceStrings.dexcom_oneplus_slot_hint,
            SourceStrings.dexcom_oneplus_slot_production_short,
            SourceStrings.dexcom_oneplus_slot_staging_short,
            SourceStrings.dexcom_oneplus_staging_connect,
            SourceStrings.dexcom_oneplus_staging_is_production,
            SourceStrings.dexcom_oneplus_staging_return_note,
            SourceStrings.dexcom_oneplus_start_return_warmup,
            SourceStrings.dexcom_oneplus_start_steps,
            SourceStrings.dexcom_oneplus_start_title,
            SourceStrings.dexcom_oneplus_step_code,
            SourceStrings.dexcom_oneplus_step_connect,
            SourceStrings.dexcom_oneplus_step_prepare,
            SourceStrings.dexcom_oneplus_step_warmup,
        )
        assertEquals(49, plain.size, "the screen names 55 strings - 6 of them take arguments")
        for (ref in plain) {
            val name = (ref as TextRef.Named).name
            assertTrue(!template(ref).contains('%'), "$name has a placeholder but is used without arguments")
        }
    }

    @Test
    fun `a template filled with one argument too few keeps its placeholder`() {
        // The test of the test. Without this a passing suite would prove nothing about the check.
        val text = formatTemplate(template(SourceStrings.dexcom_oneplus_applicator_parsed), listOf("1234"))
        assertTrue(text.contains('%'), "the unfilled check has no teeth: $text")
    }
}

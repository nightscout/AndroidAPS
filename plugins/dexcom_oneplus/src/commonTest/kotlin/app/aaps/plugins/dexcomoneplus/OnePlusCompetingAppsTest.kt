package app.aaps.plugins.dexcomoneplus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which installed apps the start screen warns about. */
class OnePlusCompetingAppsTest {

    @Test
    fun `the Dexcom apps and xDrip and Juggluco are known`() {
        val packages = OnePlusCompetingApps.KNOWN.map { it.packageName }
        assertTrue("com.dexcom.g7" in packages)
        assertTrue("com.dexcom.one" in packages)
        assertTrue("com.eveningoutpost.dexdrip" in packages)
        assertTrue("tk.glucodata" in packages)
    }

    @Test
    fun `only the installed ones are listed and in the known order`() {
        val installed = setOf("tk.glucodata", "com.dexcom.g7")
        val found = OnePlusCompetingApps.installed { it in installed }
        assertEquals(listOf("Dexcom G7", "Juggluco"), found.map { it.label })
    }

    @Test
    fun `nothing installed lists nothing`() {
        assertEquals(emptyList(), OnePlusCompetingApps.installed { false })
    }

    @Test
    fun `one failing lookup does not hide the others`() {
        val found = OnePlusCompetingApps.installed { name ->
            if (name == "com.dexcom.g7") error("lookup failed")
            name == "com.eveningoutpost.dexdrip"
        }
        assertEquals(listOf("xDrip+"), found.map { it.label })
    }
}

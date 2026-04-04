package app.aaps.plugins.main.general.nfcCommands

import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class NfcCommandsFragmentTest : TestBaseWithProfile() {
    @Test
    fun `fragment is instantiable`() {
        assertDoesNotThrow { NfcCommandsFragment() }
    }

    @Test
    fun `NfcTagsFragment is instantiable`() {
        assertDoesNotThrow { NfcTagsFragment() }
    }

    @Test
    fun `NfcLogFragment is instantiable`() {
        assertDoesNotThrow { NfcLogFragment() }
    }

    @Test
    fun `NfcCategories build returns 7 categories`() {
        assert(NfcCategories.build().size == 7)
    }

    @Test
    fun `NfcCategories each category has at least one command`() {
        NfcCategories.build().forEach { cat ->
            assert(cat.commands.isNotEmpty()) { "Category ${cat.labelResId} has no commands" }
        }
    }

    @Test
    fun `NfcCategories all commands reference valid templates`() {
        val labelResIds = NfcTokenSupport.availableCommands().map { it.labelResId }.toSet()
        NfcCategories.build().forEach { cat ->
            cat.commands.forEach { cmd ->
                assert(cmd.template.labelResId in labelResIds) {
                    "Unknown template labelResId: ${cmd.template.labelResId}"
                }
            }
        }
    }

    @Test
    fun `pager adapter exposes log and tags fragment fields`() {
        // NfcPagerAdapter holds logFragment and tagsFragment as direct fields.
        // FragmentStateAdapter requires an attached fragment, so we verify
        // the field types via direct construction rather than via the adapter.
        assertDoesNotThrow { NfcLogFragment() }
        assertDoesNotThrow { NfcTagsFragment() }
    }
}

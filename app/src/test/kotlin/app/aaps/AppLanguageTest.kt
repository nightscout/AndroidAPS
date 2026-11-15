package app.aaps

import app.aaps.core.interfaces.resources.LanguageTag
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** What Android is told to show AAPS in (Android 13 and newer). */
class AppLanguageTest {

    @Test
    fun aPickedLanguageIsGivenAsItsTag() {
        assertThat(appLanguageTag("cs", simpleMode = false)).isEqualTo("cs")
    }

    /** The stored values that are not tags are fixed up, so Danish and the regional ones work. */
    @Test
    fun oldStoredValuesBecomeTags() {
        assertThat(appLanguageTag("dk", simpleMode = false)).isEqualTo("da")
        assertThat(appLanguageTag("pt_BR", simpleMode = false)).isEqualTo("pt-BR")
    }

    @Test
    fun noPickedLanguageFollowsThePhone() {
        assertThat(appLanguageTag(LanguageTag.FOLLOW_DEVICE, simpleMode = false)).isNull()
    }

    @Test
    fun simpleModeFollowsThePhone() {
        assertThat(appLanguageTag("cs", simpleMode = true)).isNull()
    }
}

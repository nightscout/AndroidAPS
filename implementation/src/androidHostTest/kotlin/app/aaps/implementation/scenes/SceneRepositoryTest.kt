package app.aaps.implementation.scenes

import app.aaps.core.data.model.Scene
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

internal class SceneRepositoryTest {

    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: SceneRepository

    // Stand-in for the pref store — captures puts so subsequent gets reflect the write.
    private var stored = "[]"
    private val flow = MutableStateFlow(stored)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(preferences.get(StringNonKey.SceneDefinitions)).thenAnswer { stored }
        whenever(preferences.put(any<StringNonPreferenceKey>(), any<String>())).thenAnswer { invocation ->
            stored = invocation.arguments[1] as String
            flow.value = stored
        }
        whenever(preferences.observe(StringNonKey.SceneDefinitions)).thenReturn(flow)
        sut = SceneRepository(preferences)
    }

    // -- saveScene -------------------------------------------------------------------------

    @Test
    fun saveScene_addsEntry() {
        sut.saveScene(Scene(id = "a", name = "Exercise"))

        val saved = sut.getScenes().single()
        assertThat(saved.id).isEqualTo("a")
        assertThat(saved.name).isEqualTo("Exercise")
    }

    @Test
    fun saveScene_updatesInPlaceForSameId() {
        sut.saveScene(Scene(id = "a", name = "x"))
        sut.saveScene(Scene(id = "a", name = "x-updated"))

        val visible = sut.getScenes()
        assertThat(visible).hasSize(1)
        assertThat(visible[0].name).isEqualTo("x-updated")
    }

    @Test
    fun saveScene_addsNewEntryWhenIdAbsent() {
        sut.saveScene(Scene(id = "a", name = "x"))
        sut.saveScene(Scene(id = "b", name = "y"))

        assertThat(sut.getScenes().map { it.id }).containsExactly("a", "b").inOrder()
    }

    // -- deleteScene -----------------------------------------------------------------------

    @Test
    fun deleteScene_removesEntry() {
        sut.saveScene(Scene(id = "a", name = "x"))
        sut.saveScene(Scene(id = "b", name = "y"))
        sut.deleteScene("a")

        assertThat(sut.getScenes().map { it.id }).containsExactly("b")
        assertThat(stored).doesNotContain("\"id\":\"a\"")
    }

    @Test
    fun deleteScene_unknownIdIsNoOp() {
        sut.saveScene(Scene(id = "a", name = "x"))
        val before = stored
        sut.deleteScene("nope")
        assertThat(stored).isEqualTo(before)
    }

    // -- getScene --------------------------------------------------------------------------

    @Test
    fun getScene_returnsNullAfterDelete() {
        sut.saveScene(Scene(id = "a", name = "x"))
        sut.deleteScene("a")

        assertThat(sut.getScene("a")).isNull()
    }
}

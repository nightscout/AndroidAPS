package info.nightscout.androidaps.plugins.general.maintenance.formats

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.io.File

@RunWith(PowerMockRunner::class)
@PrepareForTest(File::class, Translator::class)
class ClassicPrefsFormatTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var translator: Translator
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var file: MockedFile

    @Test
    fun preferenceLoadingTest() {
        val test = "key1::val1\nkeyB::valB"

        val classicFormat = ClassicPrefsFormat(resourceHelper, dateUtil, translator, profileFunction, SingleStringStorage(test))
        val prefs = classicFormat.loadPreferences(getMockedFile(), "")

        Assert.assertEquals(prefs.values.size, 2)
        Assert.assertEquals(prefs.values["key1"], "val1")
        Assert.assertEquals(prefs.values["keyB"], "valB")
        Assert.assertNull(prefs.values["key3"])
    }

    @Test
    fun preferenceSavingTest() {
        val storage = SingleStringStorage("")
        val classicFormat = ClassicPrefsFormat(resourceHelper, dateUtil, translator, profileFunction, storage)
        val prefs = Prefs(
            mapOf(
                "key1" to "A",
                "keyB" to "2"
            ),
            mapOf()
        )

        classicFormat.savePreferences(getMockedFile(), prefs)
    }

    class MockedFile(s: String) : File(s)

    private fun getMockedFile(): File {
        `when`(file.exists()).thenReturn(true)
        `when`(file.canRead()).thenReturn(true)
        `when`(file.canWrite()).thenReturn(true)
        return file
    }
}

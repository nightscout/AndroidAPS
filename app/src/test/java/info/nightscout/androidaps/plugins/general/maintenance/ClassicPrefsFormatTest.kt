package info.nightscout.androidaps.plugins.general.maintenance

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.general.maintenance.formats.ClassicPrefsFormat
import info.nightscout.androidaps.plugins.general.maintenance.formats.Prefs
import info.nightscout.androidaps.testing.utils.SingleStringStorage
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.io.File

@RunWith(PowerMockRunner::class)
@PrepareForTest(File::class)

class ClassicPrefsFormatTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var file: MockedFile

    @Test
    fun preferenceLoadingTest() {
        val test = "key1::val1\nkeyB::valB"

        val classicFormat = ClassicPrefsFormat(resourceHelper, SingleStringStorage(test))
        val prefs = classicFormat.loadPreferences(getMockedFile(), "")

        Assert.assertThat(prefs.values.size, CoreMatchers.`is`(2))
        Assert.assertThat(prefs.values["key1"], CoreMatchers.`is`("val1"))
        Assert.assertThat(prefs.values["keyB"], CoreMatchers.`is`("valB"))
        Assert.assertNull(prefs.values["key3"])
    }

    @Test
    fun preferenceSavingTest() {
        val storage = SingleStringStorage("")
        val classicFormat = ClassicPrefsFormat(resourceHelper, storage)
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

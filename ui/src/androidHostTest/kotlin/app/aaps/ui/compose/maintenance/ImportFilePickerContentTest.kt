package app.aaps.ui.compose.maintenance

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.ui.R as CoreUiR
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric composable test for [ImportFilePickerContent]: renders (empty state) + fires close. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ImportFilePickerContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val fileListProvider: FileListProvider = mock()

    private lateinit var titleLabel: String
    private lateinit var closeLabel: String
    private lateinit var noFilesLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        titleLabel = ctx.getString(CoreUiR.string.import_setting)
        closeLabel = ctx.getString(CoreUiR.string.close)
        noFilesLabel = ctx.getString(CoreUiR.string.import_no_files)
    }

    @Test
    fun rendersEmptyStateAndFiresClose() {
        var closed = false
        compose.setContent {
            MaterialTheme {
                ImportFilePickerContent(
                    state = ImportStep.FilePicker(
                        files = emptyList(),
                        hasMoreCloud = false,
                        isLoadingMore = false,
                        source = ImportSource.LOCAL
                    ),
                    prefFileList = fileListProvider,
                    onFileClick = {},
                    onLoadMore = {},
                    onClose = { closed = true }
                )
            }
        }
        compose.onNodeWithText(titleLabel).assertIsDisplayed()
        compose.onNodeWithText(noFilesLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(closeLabel).performClick()
        assertThat(closed).isTrue()
    }
}

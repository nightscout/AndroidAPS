package app.aaps.ui.compose.maintenance

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportDecryptResult
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.PrefsFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ImportSource { LOCAL, CLOUD, BOTH }

data class ImportFileItem(val prefsFile: PrefsFile, val source: ImportSource)

sealed interface ImportStep {
    data object Idle : ImportStep
    data object Loading : ImportStep
    data class FilePicker(
        val files: List<ImportFileItem>,
        val hasMoreCloud: Boolean,
        val isLoadingMore: Boolean,
        val isLoadingCloud: Boolean = false,
        val cloudLoadingProgress: String? = null,
        val source: ImportSource
    ) : ImportStep

    data class Review(
        val file: PrefsFile,
        val fileSource: ImportSource,
        val masterPassword: String = "",
        val passwordFieldError: Boolean = false,
        val decryptionPassword: String = "",
        val needsDecryptionPassword: Boolean = false,
        val decryptResult: ImportDecryptResult? = null,
        val isProcessing: Boolean = false
    ) : ImportStep

    data object RestartConfirm : ImportStep
    data class Error(val message: String) : ImportStep
}

@HiltViewModel
@Stable
class ImportViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val importExportPrefs: ImportExportPrefs,
    private val prefFileList: FileListProvider,
    private val configBuilder: ConfigBuilder,
    private val uel: UserEntryLogger
) : ViewModel() {

    val importStep: StateFlow<ImportStep>
        field = MutableStateFlow<ImportStep>(ImportStep.Idle)

    // Cache for loaded files (preserved when navigating back from Review)
    private var cachedFiles: List<ImportFileItem> = emptyList()
    private var cachedSource: ImportSource = ImportSource.LOCAL
    private var cachedHasMoreCloud: Boolean = false
    private var cloudPageToken: String? = null

    fun startImport(source: ImportSource) {
        // Don't reload if already showing files for same source
        val current = importStep.value
        if (current is ImportStep.FilePicker && cachedSource == source && cachedFiles.isNotEmpty()) return

        cachedSource = source
        cachedFiles = emptyList()
        cloudPageToken = null

        uel.log(Action.IMPORT_SETTINGS, Sources.Maintenance)

        val needsLocal = source == ImportSource.LOCAL || source == ImportSource.BOTH
        val needsCloud = source == ImportSource.CLOUD || source == ImportSource.BOTH

        if (needsLocal) {
            // Show loading briefly, then local files immediately
            importStep.value = ImportStep.Loading
            viewModelScope.launch {
                try {
                    val localFiles = importExportPrefs.getLocalImportFiles()
                    cachedFiles = localFiles.map { ImportFileItem(it, ImportSource.LOCAL) }

                    importStep.value = ImportStep.FilePicker(
                        files = cachedFiles,
                        hasMoreCloud = false,
                        isLoadingMore = false,
                        isLoadingCloud = needsCloud,
                        cloudLoadingProgress = if (needsCloud) "Connecting to cloud\u2026" else null,
                        source = source
                    )

                    // Now load cloud in background if needed
                    if (needsCloud) {
                        loadCloudFiles()
                    }
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Failed to load local files", e)
                    importStep.value = ImportStep.Error(e.message ?: "Failed to load files")
                }
            }
        } else {
            // Cloud only — show loading with progress
            importStep.value = ImportStep.FilePicker(
                files = emptyList(),
                hasMoreCloud = false,
                isLoadingMore = false,
                isLoadingCloud = true,
                cloudLoadingProgress = "Connecting to cloud\u2026",
                source = source
            )
            viewModelScope.launch { loadCloudFiles() }
        }
    }

    private suspend fun loadCloudFiles() {
        try {
            val (cloudFiles, nextToken) = importExportPrefs.getCloudImportFiles(null)
            val newItems = cloudFiles.map { ImportFileItem(it, ImportSource.CLOUD) }
            cachedFiles = cachedFiles + newItems
            cloudPageToken = nextToken
            cachedHasMoreCloud = nextToken != null

            importStep.value = ImportStep.FilePicker(
                files = cachedFiles,
                hasMoreCloud = cachedHasMoreCloud,
                isLoadingMore = false,
                isLoadingCloud = false,
                source = cachedSource
            )
        } catch (e: Exception) {
            aapsLogger.error(LTag.CORE, "Failed to load cloud files", e)
            // Keep local files visible, just stop cloud loading
            val current = importStep.value
            if (current is ImportStep.FilePicker) {
                importStep.value = current.copy(
                    isLoadingCloud = false,
                    cloudLoadingProgress = null
                )
            }
        }
    }

    fun loadMoreCloud() {
        val current = importStep.value
        if (current !is ImportStep.FilePicker || current.isLoadingMore || !current.hasMoreCloud) return

        importStep.value = current.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val (cloudFiles, nextToken) = importExportPrefs.getCloudImportFiles(cloudPageToken)
                val newItems = cloudFiles.map { ImportFileItem(it, ImportSource.CLOUD) }
                cachedFiles = cachedFiles + newItems
                cloudPageToken = nextToken
                cachedHasMoreCloud = nextToken != null

                importStep.value = ImportStep.FilePicker(
                    files = cachedFiles,
                    hasMoreCloud = cachedHasMoreCloud,
                    isLoadingMore = false,
                    source = cachedSource
                )
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Failed to load more cloud files", e)
                // Restore non-loading state
                importStep.value = ImportStep.FilePicker(
                    files = cachedFiles,
                    hasMoreCloud = cachedHasMoreCloud,
                    isLoadingMore = false,
                    source = cachedSource
                )
            }
        }
    }

    fun selectFile(item: ImportFileItem) {
        importStep.value = ImportStep.Review(
            file = item.prefsFile,
            fileSource = item.source,
            // No local master password → skip the "try master password first" optimization
            // and ask for the file's decryption password directly.
            needsDecryptionPassword = !importExportPrefs.isMasterPasswordSet()
        )
    }

    fun onMasterPasswordChanged(pw: String) {
        val current = importStep.value
        if (current is ImportStep.Review) {
            importStep.value = current.copy(
                masterPassword = pw,
                passwordFieldError = false
            )
        }
    }

    fun onDecryptionPasswordChanged(pw: String) {
        val current = importStep.value
        if (current is ImportStep.Review) {
            importStep.value = current.copy(decryptionPassword = pw)
        }
    }

    fun decrypt() {
        val current = importStep.value
        if (current !is ImportStep.Review || current.isProcessing) return

        val password = if (current.needsDecryptionPassword) current.decryptionPassword else current.masterPassword
        if (password.isBlank()) {
            // Symmetric guard: surface the blank-password error on whichever field is currently active.
            // Fresh-install path uses decryptResult=WrongPassword (which the decryption password field reads for its error);
            // master-password path uses passwordFieldError.
            importStep.value = if (current.needsDecryptionPassword)
                current.copy(decryptResult = ImportDecryptResult.WrongPassword)
            else
                current.copy(passwordFieldError = true)
            return
        }

        importStep.value = current.copy(isProcessing = true)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                importExportPrefs.decryptImportFile(current.file, password)
            }

            val prev = importStep.value as? ImportStep.Review ?: return@launch

            when (result) {
                is ImportDecryptResult.WrongPassword -> {
                    if (!prev.needsDecryptionPassword) {
                        // Master password didn't decrypt → show decryption password field
                        importStep.value = prev.copy(
                            isProcessing = false,
                            needsDecryptionPassword = true,
                            decryptResult = null
                        )
                    } else {
                        // Second attempt also failed
                        importStep.value = prev.copy(
                            isProcessing = false,
                            decryptResult = result
                        )
                    }
                }

                is ImportDecryptResult.Success,
                is ImportDecryptResult.Error         -> {
                    importStep.value = prev.copy(
                        isProcessing = false,
                        decryptResult = result
                    )
                }
            }
        }
    }

    fun confirmImport() {
        val current = importStep.value
        if (current !is ImportStep.Review) return
        val result = current.decryptResult
        if (result !is ImportDecryptResult.Success || !result.importPossible) return

        importStep.value = current.copy(isProcessing = true)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                importExportPrefs.executeImport(result.prefs)
                importExportPrefs.prepareImportRestart()
            }
            importStep.value = ImportStep.RestartConfirm
        }
    }

    fun onRestartConfirmed() {
        configBuilder.exitApp("Import", Sources.Maintenance, false)
    }

    fun goBackToFilePicker() {
        importStep.value = ImportStep.FilePicker(
            files = cachedFiles,
            hasMoreCloud = cachedHasMoreCloud,
            isLoadingMore = false,
            source = cachedSource
        )
    }

    fun dismissError() {
        importStep.value = ImportStep.Idle
    }

    fun cancelImport() {
        importStep.value = ImportStep.Idle
    }
}

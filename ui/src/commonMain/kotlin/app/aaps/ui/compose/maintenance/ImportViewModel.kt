package app.aaps.ui.compose.maintenance

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiRestart
import app.aaps.core.ui.CoreUiStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.PrefsFileInfo
import app.aaps.core.interfaces.maintenance.ImportDecryptResult
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.PrefsFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /**
     * The settings are written and are about to be applied to the running app.
     *
     * This was `RestartConfirm`, and the app really did restart: `exitApp` killed the process so the
     * new settings were picked up on the way back. iOS may not terminate itself, so that ending
     * silently did nothing there - the user confirmed a restart that never happened and carried on
     * with the old configuration. Applying in place is the one ending that works everywhere.
     */
    data object ApplyConfirm : ImportStep

    /**
     * Waiting for the pump to finish what it is doing before the settings are applied.
     *
     * Applying can stop and start plugins, and a pump driver torn down mid-command is the one case
     * where that is not merely untidy. The queue is checked before anything is applied rather than
     * after working out whether the pump changed: a missed detection would skip this wait, which is
     * the dangerous direction to be wrong in.
     */
    data object WaitingForPump : ImportStep
    data class Error(val message: String) : ImportStep
}

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
class ImportViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val importExportPrefs: ImportExportPrefs,
    private val prefFileList: PrefsFileInfo,
    private val configBuilder: ConfigBuilder,
    private val rh: TextResolver,
    private val uel: UserEntryLogger,
    private val commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val activePlugin: ActivePlugin,
    private val overviewDataCache: OverviewDataCache,
    private val iobCobCalculator: IobCobCalculator,
    private val uiRestart: UiRestart
) : ViewModel() {

    private companion object {

        /** Long enough for a pump command to finish, short enough that a stuck pump is not a trap. */
        val PUMP_WAIT = 60.seconds
        val POLL = 250.milliseconds
    }

    private val _importStep = MutableStateFlow<ImportStep>(ImportStep.Idle)
    val importStep: StateFlow<ImportStep> = _importStep.asStateFlow()

    // Cache for loaded files (preserved when navigating back from Review)
    private var cachedFiles: List<ImportFileItem> = emptyList()
    private var cachedSource: ImportSource = ImportSource.LOCAL
    private var cachedHasMoreCloud: Boolean = false
    private var cloudPageToken: String? = null

    fun startImport(source: ImportSource) {
        // Always reload on a fresh entry so newly exported files appear in the list.
        // Returning from the Review step uses goBackToFilePicker(), which keeps the cache —
        // Review is an internal ImportStep, not a nav route, so it never re-enters here.
        cachedSource = source
        cachedFiles = emptyList()
        cachedHasMoreCloud = false
        cloudPageToken = null

        val needsLocal = source == ImportSource.LOCAL || source == ImportSource.BOTH
        val needsCloud = source == ImportSource.CLOUD || source == ImportSource.BOTH

        if (needsLocal) {
            // Show loading briefly, then local files immediately
            _importStep.value = ImportStep.Loading
            viewModelScope.launch {
                try {
                    val localFiles = importExportPrefs.getLocalImportFiles()
                    cachedFiles = localFiles.map { ImportFileItem(it, ImportSource.LOCAL) }

                    _importStep.value = ImportStep.FilePicker(
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
                    _importStep.value = ImportStep.Error(e.message ?: "Failed to load files")
                }
            }
        } else {
            // Cloud only — show loading with progress
            _importStep.value = ImportStep.FilePicker(
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

            _importStep.value = ImportStep.FilePicker(
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
                _importStep.value = current.copy(
                    isLoadingCloud = false,
                    cloudLoadingProgress = null
                )
            }
        }
    }

    fun loadMoreCloud() {
        val current = importStep.value
        if (current !is ImportStep.FilePicker || current.isLoadingMore || !current.hasMoreCloud) return

        _importStep.value = current.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val (cloudFiles, nextToken) = importExportPrefs.getCloudImportFiles(cloudPageToken)
                val newItems = cloudFiles.map { ImportFileItem(it, ImportSource.CLOUD) }
                cachedFiles = cachedFiles + newItems
                cloudPageToken = nextToken
                cachedHasMoreCloud = nextToken != null

                _importStep.value = ImportStep.FilePicker(
                    files = cachedFiles,
                    hasMoreCloud = cachedHasMoreCloud,
                    isLoadingMore = false,
                    source = cachedSource
                )
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Failed to load more cloud files", e)
                // Restore non-loading state
                _importStep.value = ImportStep.FilePicker(
                    files = cachedFiles,
                    hasMoreCloud = cachedHasMoreCloud,
                    isLoadingMore = false,
                    source = cachedSource
                )
            }
        }
    }

    fun selectFile(item: ImportFileItem) {
        _importStep.value = ImportStep.Review(
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
            _importStep.value = current.copy(
                masterPassword = pw,
                passwordFieldError = false
            )
        }
    }

    fun onDecryptionPasswordChanged(pw: String) {
        val current = importStep.value
        if (current is ImportStep.Review) {
            _importStep.value = current.copy(decryptionPassword = pw)
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
            _importStep.value = if (current.needsDecryptionPassword)
                current.copy(decryptResult = ImportDecryptResult.WrongPassword)
            else
                current.copy(passwordFieldError = true)
            return
        }

        _importStep.value = current.copy(isProcessing = true)

        viewModelScope.launch {
            val result = withContext(aapsIoDispatcher) {
                importExportPrefs.decryptImportFile(current.file, password)
            }

            val prev = importStep.value as? ImportStep.Review ?: return@launch

            when (result) {
                is ImportDecryptResult.WrongPassword -> {
                    if (!prev.needsDecryptionPassword) {
                        // Master password didn't decrypt → show decryption password field
                        _importStep.value = prev.copy(
                            isProcessing = false,
                            needsDecryptionPassword = true,
                            decryptResult = null
                        )
                    } else {
                        // Second attempt also failed
                        _importStep.value = prev.copy(
                            isProcessing = false,
                            decryptResult = result
                        )
                    }
                }

                is ImportDecryptResult.Success,
                is ImportDecryptResult.Error         -> {
                    _importStep.value = prev.copy(
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

        _importStep.value = current.copy(isProcessing = true)

        viewModelScope.launch {
            withContext(aapsIoDispatcher) {
                importExportPrefs.executeImport(result.prefs)
                importExportPrefs.prepareImportRestart()
            }
            _importStep.value = ImportStep.ApplyConfirm
        }
    }

    /**
     * Applies the imported settings to the running app, waiting for the pump first.
     *
     * The wait comes before anything is applied and the rest happens in one go, so nothing is ever
     * half applied while the pump is busy. The alternative - deciding up front whether the pump
     * changed - needs the imported preferences diffed against the current ones, and a missed
     * difference would skip the wait rather than add a needless one.
     */
    fun onApplyConfirmed() {
        viewModelScope.launch {
            if (commandQueue.size() > 0 || commandQueue.performing() != null) {
                _importStep.value = ImportStep.WaitingForPump
                val idle = withTimeoutOrNull(PUMP_WAIT) {
                    while (commandQueue.size() > 0 || commandQueue.performing() != null) delay(POLL)
                }
                if (idle == null) {
                    aapsLogger.warn(LTag.CORE, "Pump still busy after $PUMP_WAIT, settings not applied")
                    _importStep.value = ImportStep.Error(rh.gs(CoreUiStrings.import_apply_pump_busy))
                    return@launch
                }
            }
            applySettings()
        }
    }

    /** Everything the app has to redo to be running the imported settings. Assumes an idle queue. */
    private fun applySettings() {
        // Not moved to an IO dispatcher: `initialize()` is what both `IosAppStartup` and the desktop
        // `Main` call straight on the startup path, and the cache resets are in memory. Keeping it
        // here also keeps it observable from a test, which work handed to the global IO dispatcher
        // is not.
        run {
            // Re-reads every plugin's stored enabled state and starts or stops it to match, then
            // picks the active plugin per category. Idempotent, so a plugin whose setting did not
            // change is left alone rather than cycled.
            configBuilder.initialize()

            // Asked of the pump layer rather than worked out from the preferences: it answers from
            // what is actually registered, so an unchanged pump keeps its running temporary basal
            // instead of having it ended for nothing.
            val pump = activePlugin.activePump
            if (!pumpSync.verifyPumpIdentification(pump.pumpDescription.pumpType, pump.serialNumber()))
                pumpSync.connectNewPump()

            // The same reset `resetDatabases` does: the imported profile, units and targets change
            // what every cached calculation meant.
            overviewDataCache.reset()
            iobCobCalculator.ads.reset()
            iobCobCalculator.clearCache()
        }
        uel.log(Action.IMPORT_SETTINGS, Sources.Maintenance)
        // The app is now running the imported settings, but any screen composed before this still
        // shows what it read earlier - which looks exactly like an import that did nothing.
        uiRestart.request()
        _importStep.value = ImportStep.Idle
    }

    fun goBackToFilePicker() {
        _importStep.value = ImportStep.FilePicker(
            files = cachedFiles,
            hasMoreCloud = cachedHasMoreCloud,
            isLoadingMore = false,
            source = cachedSource
        )
    }

    fun dismissError() {
        _importStep.value = ImportStep.Idle
    }

    fun cancelImport() {
        _importStep.value = ImportStep.Idle
    }
}

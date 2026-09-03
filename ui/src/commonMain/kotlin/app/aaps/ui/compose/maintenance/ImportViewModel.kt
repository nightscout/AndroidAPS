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
import app.aaps.core.ui.CoreUiStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
     * where that is not merely untidy. The queue is held before anything is applied rather than
     * after working out whether the pump changed: a missed detection would skip this wait, which is
     * the dangerous direction to be wrong in.
     */
    data object WaitingForPump : ImportStep

    /**
     * The settings are in force. The last step, and the only one the user should end an import on.
     *
     * There has to be a step for this. The app used to die at this point, so "finished" needed no
     * screen; now it does, and falling back to [Idle] would leave the user on the import screen's
     * spinner with no sign that anything had happened.
     */
    data object Applied : ImportStep

    /**
     * The settings are written but could not be applied, because the pump stayed busy.
     *
     * Separate from [Error] because it is recoverable and worth retrying: nothing is wrong with the
     * imported file, the app is simply running the old plugin configuration until this succeeds.
     */
    data class ApplyFailed(val message: String) : ImportStep
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
    private val iobCobCalculator: IobCobCalculator
) : ViewModel() {

    private companion object {

        /** Long enough for a pump command to finish, short enough that a stuck pump is not a trap. */
        val PUMP_WAIT = 60.seconds
        const val HOLD_REASON = "Import"
    }

    // Where the apply work runs. Off the main thread: it walks every plugin, writes preferences and
    // waits for drivers to stop and start. Not a constructor parameter because this class is built by
    // the graph and CoroutineDispatcher has no binding; internal so the test can run it in virtual time.
    internal var applyDispatcher: CoroutineDispatcher = aapsIoDispatcher

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
                importExportPrefs.prepareImportedSettings()
            }
            _importStep.value = ImportStep.ApplyConfirm
        }
    }

    /**
     * Applies the imported settings to the running app, with the pump command queue held.
     *
     * `withHold` is what makes this safe, and it does two things a plain "wait until the queue looks
     * idle" cannot. It stops anything new being picked up *before* it waits, so the loop or an
     * automation cannot slip a command in between the check and the teardown; and it waits for the
     * command in flight, so no driver is pulled out from under one. Commands queued behind it are
     * kept and run afterwards - unless the pump itself changed, which [applySettings] handles.
     *
     * Holding for the whole apply, rather than deciding up front whether the pump changed, is
     * deliberate: working that out needs the imported preferences diffed against the current ones,
     * and a missed difference would skip the protection rather than add a needless wait.
     */
    fun onApplyConfirmed() {
        viewModelScope.launch {
            // Cosmetic only - whether the spinner is worth showing at all. The real wait, and the
            // guarantee that nothing starts during the apply, both live inside withHold.
            if (commandQueue.size() > 0 || commandQueue.performing() != null)
                _importStep.value = ImportStep.WaitingForPump
            // A plugin can throw on the way up or down, and this runs a lot of them. Letting that out
            // of viewModelScope would take the app down at the one moment the user cannot afford it:
            // the settings are already written, so the app would come back with them half applied and
            // no screen left to finish the job. Offer the retry instead.
            val applied = try {
                commandQueue.withHold(HOLD_REASON, PUMP_WAIT) {
                    withContext(applyDispatcher) { applySettings() }
                }
            } catch (e: CancellationException) {
                throw e     // the screen going away, not a failure
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Applying imported settings failed", e)
                _importStep.value = ImportStep.ApplyFailed(e.message ?: rh.gs(CoreUiStrings.error))
                return@launch
            }
            if (applied) {
                uel.log(Action.IMPORT_SETTINGS, Sources.Maintenance)
                _importStep.value = ImportStep.Applied
            } else {
                aapsLogger.warn(LTag.CORE, "Pump still busy after $PUMP_WAIT, settings not applied")
                _importStep.value = ImportStep.ApplyFailed(rh.gs(CoreUiStrings.import_apply_pump_busy))
            }
        }
    }

    /** Everything the app has to redo to be running the imported settings. Runs with the queue held. */
    private suspend fun applySettings() {
        // Re-reads every plugin's stored enabled state and starts or stops it to match, then picks the
        // active plugin per category. `applyConfiguration`, not `initialize`: this one returns once the
        // plugins have actually started and stopped, which is the whole point of having waited for an
        // idle pump. Safe to run on a live app, so a plugin whose setting did not change is left alone
        // rather than cycled.
        configBuilder.applyConfiguration()

        // Asked of the pump layer rather than worked out from the preferences: it answers from what is
        // actually registered, so an unchanged pump keeps its running temporary basal instead of having
        // it ended for nothing.
        val pump = activePlugin.activePump
        if (!pumpSync.verifyPumpIdentification(pump.pumpDescription.pumpType, pump.serialNumber())) {
            pumpSync.connectNewPump()
            // Anything still queued was meant for the pump that was active a moment ago. Running it
            // against a different one is the worst outcome available here, so finish those commands
            // instead - as a no-op, so a caller waiting on one is told it did not happen.
            commandQueue.completeAllAsNoOp(CoreUiStrings.import_apply_pump_changed)
        }

        // The same reset `resetDatabases` does: the imported profile, units and targets change what
        // every cached calculation meant.
        overviewDataCache.reset()
        iobCobCalculator.ads.reset()
        iobCobCalculator.clearCache()

        // Reset only empties the cache; it does not reload it. `resetDatabases` can stop there because
        // there is nothing left to read, but here the database is untouched and the overview would sit
        // on the emptied flows until something else happened to refresh each one - which for the
        // profile is the next ProfileSwitch, possibly hours away. It showed as a big "NO PROFILE SET"
        // on the overview while the loop was in fact running on the profile perfectly happily.
        // The app used to restart at this point and rebuild all of this on the way back up.
        overviewDataCache.refreshProfile()
        overviewDataCache.refreshTempTarget()
        overviewDataCache.refreshRunningMode()
        overviewDataCache.refreshTbr()
    }

    /** Try the apply again after [ImportStep.ApplyFailed]. The settings are still written and waiting. */
    fun retryApply() = onApplyConfirmed()

    /** Leave the import screen once it has reached [ImportStep.Applied] or [ImportStep.ApplyFailed]. */
    fun finishApply() {
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

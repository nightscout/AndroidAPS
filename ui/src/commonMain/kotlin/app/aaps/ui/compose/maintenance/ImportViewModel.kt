package app.aaps.ui.compose.maintenance

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.configuration.whileReconfiguring
import app.aaps.core.data.ue.Action
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiRestart
import app.aaps.core.ui.CoreUiStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.time.Duration.Companion.seconds
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.PrefsFileInfo
import app.aaps.core.interfaces.maintenance.ImportDecryptResult
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.Prefs
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
        val isProcessing: Boolean = false,
        /**
         * The user's answer to "also replace pump settings", asked on this screen under the password.
         *
         * False by default so the safe case needs no thought: restoring onto the same phone with the
         * same pump leaves the pump alone. Replacing a pump's configuration takes one deliberate tick.
         * Phrased as the destructive action for the same reason - a checkbox meaning "do NOT do
         * something" is hard to read in a hurry.
         */
        val replacePumpSettings: Boolean = false
    ) : ImportStep {

        /** What the applier takes. The screen asks the question the other way round. */
        val keepPumpSettings: Boolean get() = !replacePumpSettings
    }

    /**
     * The last chance to stop. NOTHING has been written at this point.
     *
     * This was `RestartConfirm`, and the app really did restart: `exitApp` killed the process so the
     * new settings were picked up on the way back. iOS may not terminate itself, so that ending
     * silently did nothing there - the user confirmed a restart that never happened and carried on
     * with the old configuration. Applying in place is the one ending that works everywhere.
     *
     * It also used to come AFTER the store had been rewritten, which made it an acknowledgement
     * rather than a confirmation. It carries [preview] so it can say what is about to happen -
     * counted by the same filter that will run, so the number cannot disagree with the outcome.
     */
    data class ApplyConfirm(
        val prefs: Prefs,
        val keepPumpSettings: Boolean,
        val preview: ImportExportPrefs.ImportOutcome
    ) : ImportStep

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
     * The apply is running and there was nothing to wait for.
     *
     * Exists so the confirm dialog always comes down when the user taps OK. Without it the step only
     * moved when the pump queue was busy, which left the dialog interactive for the whole apply on
     * every idle pump - and a second tap would have started a second one.
     */
    data object Applying : ImportStep

    /**
     * The settings are in force. The last step, and the only one the user should end an import on.
     *
     * There has to be a step for this. The app used to die at this point, so "finished" needed no
     * screen; now it does, and falling back to [Idle] would leave the user on the import screen's
     * spinner with no sign that anything had happened.
     */
    data object Applied : ImportStep

    /**
     * The settings were NOT applied, because the pump stayed busy or a plugin threw.
     *
     * Nothing was written: the write moved inside the command-queue hold, so a refused hold leaves
     * the store exactly as it was. (This used to say "the settings are written but could not be
     * applied", which was true when the write happened before the user confirmed.)
     *
     * Separate from [Error] because it is recoverable and worth retrying: nothing is wrong with the
     * imported file, and the file is still held so the retry has something to apply.
     */
    data class ApplyFailed(val message: String) : ImportStep
    data class Error(val message: String) : ImportStep
}

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class ImportViewModel(
    private val aapsLogger: AAPSLogger,
    private val importExportPrefs: ImportExportPrefs,
    private val prefFileList: PrefsFileInfo,
    private val configBuilder: ConfigBuilder,
    private val config: Config,
    private val rh: TextResolver,
    private val uel: UserEntryLogger,
    private val commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val activePlugin: ActivePlugin,
    private val overviewDataCache: OverviewDataCache,
    private val iobCobCalculator: IobCobCalculator,
    private val uiRestart: UiRestart,
    private val notificationManager: NotificationManager,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private companion object {

        /** Long enough for a pump command to finish, short enough that a stuck pump is not a trap. */
        val PUMP_WAIT = 60.seconds
        const val HOLD_REASON = "Import"
    }

    // Where this screen's background work runs - decrypting a file, rewriting the preferences, and the
    // apply itself. Off the main thread: it walks every plugin, writes preferences and waits for
    // drivers to stop and start. Not a constructor parameter because this class is built by the graph
    // and CoroutineDispatcher has no binding; internal so the test can run it in virtual time.
    //
    // One field for all three, rather than one seam and two hard-wired calls: the two that were wired
    // straight to `aapsIoDispatcher` could not be awaited from a test at all, so the preference
    // rewrite - which clears the whole store - had no test covering it.
    internal var applyDispatcher: CoroutineDispatcher = aapsIoDispatcher

    /**
     * The decrypted file waiting to be applied, and the user's checkbox answer.
     *
     * Survives the step changing, which is what makes a retry possible: an apply that fails moves to
     * [ImportStep.ApplyFailed], and the retry has to know what it is retrying. Cleared when the
     * import finishes or is abandoned, so nothing can be applied twice or after a cancel.
     */
    private var pending: ImportStep.ApplyConfirm? = null

    /**
     * Puts the screen straight on the confirm step, for tests about the APPLY.
     *
     * Reaching it for real means picking a file, decrypting it and previewing - none of which the
     * apply tests are about, and all of which would have to be stubbed to get here. Internal, like
     * [applyDispatcher] above and for the same reason.
     */
    internal fun readyToApply(prefs: Prefs, keepPumpSettings: Boolean = true) {
        val confirm = ImportStep.ApplyConfirm(prefs, keepPumpSettings, ImportExportPrefs.ImportOutcome())
        pending = confirm
        _importStep.value = confirm
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
            val result = withContext(applyDispatcher) {
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

    /**
     * Says so when the import left no pump selected and the app quietly picked the virtual one.
     *
     * `verifySelectionInCategories` enables `VirtualPumpPlugin` when a category ends up empty, which
     * is the right thing for the app to do - it cannot run with no pump at all - but it means an
     * import that named a driver this build does not have ends with the user on a pump they never
     * chose, delivering nothing, with nothing on screen to say so. The notification names what is
     * active now, because "no pump selected" is not what happened and would send them looking for
     * the wrong thing.
     */
    private fun notifyIfPumpFellBackToVirtual(pumpBefore: Any) {
        val pumpNow = activePlugin.activePump
        if (pumpNow !is VirtualPump || pumpBefore is VirtualPump) return
        notificationManager.post(
            id = NotificationId.WRONG_DRIVER,
            text = rh.gs(CoreUiStrings.import_pump_fell_back, pumpNow.pumpDescription.pumpType.description)
        )
        aapsLogger.warn(LTag.CORE, "Import left no pump selected; the virtual pump is active")
    }

    /**
     * "Also replace pump settings", from the review screen.
     *
     * Only ever set before anything is written - the answer is read once, in [confirmImport], and
     * carried on the confirm step from there.
     */
    fun onReplacePumpSettingsChanged(replace: Boolean) {
        val current = importStep.value
        if (current is ImportStep.Review) _importStep.value = current.copy(replacePumpSettings = replace)
    }

    fun confirmImport() {
        val current = importStep.value
        if (current !is ImportStep.Review) return
        val result = current.decryptResult
        if (result !is ImportDecryptResult.Success || !result.importPossible) return

        _importStep.value = current.copy(isProcessing = true)

        viewModelScope.launch {
            // Nothing is written here any more. This step only works out WHAT would change, so the
            // confirm screen can say so and the user's answer still means something - the store used
            // to be rewritten before they were asked, which made `cancelImport` a lie and published
            // the file to every paired client five seconds later. The write is in `onApplyConfirmed`.
            // Wrapped for the same reason `onApplyConfirmed` is: the preview walks every key in the
            // file and resolves each one, and an unhandled throw out of viewModelScope takes the app
            // down. Nothing is written here, so an error is simply an error - no half-applied state
            // to recover, and the user can pick the file again.
            val preview = try {
                withContext(applyDispatcher) {
                    importExportPrefs.previewImport(result.prefs, keepPumpSettings = current.keepPumpSettings)
                }
            } catch (e: CancellationException) {
                throw e     // the screen going away, not a failure
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "Previewing the import failed", e)
                _importStep.value = ImportStep.Error(e.message ?: rh.gs(CoreUiStrings.error))
                return@launch
            }
            val confirm = ImportStep.ApplyConfirm(
                prefs = result.prefs,
                keepPumpSettings = current.keepPumpSettings,
                preview = preview
            )
            pending = confirm
            _importStep.value = confirm
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
        // Held in a field rather than read off the step, because the RETRY comes from `ApplyFailed`.
        // Reading `importStep.value as? ApplyConfirm` made `retryApply` return immediately and the
        // dialog's button do nothing - and the test did not catch it, because its helper put the step
        // back to ApplyConfirm before retrying.
        val confirmed = pending ?: return
        viewModelScope.launch {
            // Leave ApplyConfirm FIRST, always. It used to move only when the queue was busy, so on
            // an idle pump - the normal case - the confirm dialog stayed on screen and tappable for
            // the whole write, applyConfiguration and storeSettings. A second tap would have started
            // a second apply.
            //
            // Which screen depends only on whether there is something to wait for: the real wait, and
            // the guarantee that nothing starts during the apply, both live inside withHold.
            _importStep.value =
                if (commandQueue.size() > 0 || commandQueue.performing() != null) ImportStep.WaitingForPump
                else ImportStep.Applying
            // A plugin can throw on the way up or down, and this runs a lot of them. Letting that out
            // of viewModelScope would take the app down at the one moment the user cannot afford it:
            // the settings are already written, so the app would come back with them half applied and
            // no screen left to finish the job. Offer the retry instead.
            val applied = try {
                commandQueue.withHold(HOLD_REASON, PUMP_WAIT) {
                    withContext(applyDispatcher) {
                        // The write lives HERE, inside the hold, and not in `confirmImport`. Two
                        // reasons. The user's confirmation now gates it, so cancelling changes
                        // nothing. And the pump drivers that watch their own keys - Dana's name,
                        // Medtronic's serial, `UseExtended` - react by talking to the pump; inside the
                        // hold that command queues behind the apply instead of racing it.
                        // Which pump was in charge BEFORE, so the fallback below can be spotted.
                        val pumpBefore = activePlugin.activePump
                        config.whileReconfiguring {
                            importExportPrefs.executeImport(confirmed.prefs, confirmed.keepPumpSettings)
                            importExportPrefs.prepareImportedSettings()
                        }
                        applySettings()
                        notifyIfPumpFellBackToVirtual(pumpBefore)
                        // Every plugin in THIS build now has an explicit stored enabled value, rather
                        // than an absent one that the next start would fill from a default. The file
                        // cannot name plugins it never had, so without this an import from a smaller
                        // build leaves gaps that resolve differently on the next launch.
                        configBuilder.storeSettings("import")
                    }
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
                // Done with, so a stray retry cannot apply the same file twice. A FAILED apply
                // deliberately keeps it - that is what the retry is for.
                pending = null
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
        //
        // Marked as reconfiguring for its duration, and for its duration ONLY. Inside it the old pump
        // has been disabled and the new one is not elected yet, so `PluginStore.activePumpInternal`
        // has a window with no answer and its "No pump selected" assertion is reachable - by the
        // roughly 35 call sites that guard on `config.appInitialized`, which this closes for them.
        // The scope stops here on purpose: the cache refreshes below must run with the app reported as
        // initialized, or they would skip their own guards and leave the overview showing
        // "NO PROFILE SET" - the very thing they were added to fix.
        config.whileReconfiguring { configBuilder.applyConfiguration() }

        // Asked of the pump layer rather than worked out from the preferences: it answers from what is
        // actually registered, so an unchanged pump keeps its running temporary basal instead of having
        // it ended for nothing.
        val pump = activePlugin.activePump
        if (!pumpSync.verifyPumpIdentification(pump.pumpDescription.pumpType, pump.serialNumber())) {
            pumpSync.connectNewPump()
            // Anything still queued was meant for the pump that was active a moment ago. Running it
            // against a different one is the worst outcome available here, so drop those commands
            // and report failure, so a caller waiting on one is told it did not happen. It used to
            // report success, which let the loop carry on as if its temp basal had been set.
            commandQueue.cancelAll(CoreUiStrings.import_apply_pump_changed, success = false)
        }

        // Re-read the profile list from the store before the caches below are refreshed from it.
        //
        // `ProfileRepositoryImpl` is app scoped and loads once in its init block. It watches
        // `StringNonKey.LocalProfileData` for lists arriving over the sync channel, and an import that
        // carries that key therefore lands on its own. A file from an older AAPS does NOT carry it -
        // the profiles arrive as the numbered `LocalProfile_isf_0` keys that `PreferenceMigrations`
        // writes - and nothing observes those, so without this the imported profiles would sit in the
        // store unread until the next process start. The refreshes below would not find them either:
        // they re-read through `profileFunction`, which asks this repository.
        profileRepository.reset()

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
        //
        // `UiRestart` does not cover this. It rebuilds the composition, but the cache behind it is an
        // app-scoped singleton that survives an activity recreate, so the emptied flows would still be
        // empty on the way back.
        overviewDataCache.refreshProfile()
        overviewDataCache.refreshTempTarget()
        overviewDataCache.refreshRunningMode()
        overviewDataCache.refreshTbr()
    }

    /** Try the apply again after [ImportStep.ApplyFailed]. The settings are still written and waiting. */
    fun retryApply() = onApplyConfirmed()

    /** Leave the import screen once it has reached [ImportStep.Applied] or [ImportStep.ApplyFailed]. */
    fun finishApply() {
        val applied = _importStep.value is ImportStep.Applied
        _importStep.value = ImportStep.Idle
        // Asked for here rather than at the end of the apply, and only when something was applied.
        // Android answers a rebuild by recreating the activity, which would take the "settings
        // applied" dialog down before it could be read; and a refused apply changed nothing, so there
        // is nothing to rebuild for. Screens composed before the import still show what they read
        // then, which is what this is for.
        if (applied) uiRestart.request()
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
        pending = null
        _importStep.value = ImportStep.Idle
    }

    fun cancelImport() {
        // Clears the pending file too: abandoning an import must not leave something a later
        // retry could apply.
        pending = null
        _importStep.value = ImportStep.Idle
    }
}

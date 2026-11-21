package app.aaps.ui.search

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.plugin.HardwarePumpConfirmation
import app.aaps.ui.plugin.PluginSwitchConfirmation
import app.aaps.ui.plugin.PluginSwitchDialogs
import app.aaps.ui.plugin.PluginSwitchHandler
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * ViewModel for the global search feature.
 * Handles search state, debounced queries, and results.
 * Runs local search and wiki search in parallel.
 * Keeps a short list of recent searches ([SearchUiState.history]) for the empty search field.
 */
// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class SearchViewModel(
    private val searchIndexBuilder: SearchIndexBuilder,
    private val wikiSearchRepository: WikiSearchRepository,
    private val nsClient: NsClient,
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder,
    private val config: Config,
    private val preferences: Preferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(history = loadHistory()))
    val uiState: StateFlow<SearchUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")

    // Shared enable/disable orchestration — same swap-confirmation + hardware-pump flow as the Config Builder.
    // onSwitched rebuilds the index and re-runs the current query so a toggled plugin reflects its new state.
    private val switchHandler = PluginSwitchHandler(viewModelScope, activePlugin, configBuilder, onSwitched = ::onPluginSwitched)

    init {
        observeSearchQuery()
        // Rebuild the index when the client pairs/unpairs so CLIENT_PAIRED-gated actions drop out of
        // (or re-enter) results live. drop(1) skips the initial replay — no needless rebuild on launch.
        nsClient.masterOrPairedClientFlow
            .drop(1)
            .onEach { searchIndexBuilder.invalidateIndex() }
            .launchIn(viewModelScope)
        // Mirror the shared switch handler's confirmation dialogs into the search UI state.
        switchHandler.dialogs
            .onEach { syncSwitchDialogs(it) }
            .launchIn(viewModelScope)
        // Refresh the visible switches/greying when a synced selection changes elsewhere (e.g. a master→client
        // push adopted while the search overlay is open), not just for this screen's own toggles — mirrors
        // ConfigurationViewModel so the two screens stay consistent.
        configBuilder.activeSelectionChanges
            .onEach { onPluginSwitched() }
            .launchIn(viewModelScope)
    }

    /** Toggle a plugin found in the search results. Single-select enabling raises the swap confirmation. */
    fun togglePlugin(plugin: PluginBase) {
        // Defense-in-depth for the SearchResults UI gate: a client must not change a synced single-select
        // selection (APS/Sensitivity/Smoothing/Calibration) while the master is offline, or below the admin role.
        if (config.AAPSCLIENT && plugin.getType().selectionSyncs && (!nsClient.masterReachable.value || !nsClient.clientRole.value.allows(ClientRole.Admin))) return
        switchHandler.toggle(plugin, plugin.getType(), !plugin.isEnabled())
        syncSwitchDialogs(switchHandler.dialogs.value)
    }

    fun confirmPluginSwitch() {
        switchHandler.confirmSwitch()
        syncSwitchDialogs(switchHandler.dialogs.value)
    }

    fun dismissPluginSwitch() {
        switchHandler.dismissSwitch()
        syncSwitchDialogs(switchHandler.dialogs.value)
    }

    fun confirmHardwarePump() {
        switchHandler.confirmHardwarePump()
        syncSwitchDialogs(switchHandler.dialogs.value)
    }

    fun dismissHardwarePump() {
        switchHandler.dismissHardwarePump()
        syncSwitchDialogs(switchHandler.dialogs.value)
    }

    private fun syncSwitchDialogs(dialogs: PluginSwitchDialogs) {
        _uiState.update {
            it.copy(
                hardwarePumpConfirmation = dialogs.hardwarePumpConfirmation,
                pluginSwitchConfirmation = dialogs.pluginSwitchConfirmation
            )
        }
    }

    // After a plugin's enabled state changes, rebuild the index and re-run the current query so the toggled
    // plugin (and any preferences it owns) reflect the new state in the visible results.
    private fun onPluginSwitched() {
        searchIndexBuilder.invalidateIndex()
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            viewModelScope.launch {
                val results = withContext(Dispatchers.Default) { searchIndexBuilder.search(query) }
                _uiState.update { it.copy(results = results, revision = it.revision + 1) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        _searchQuery
            .debounce(300) // Wait 300ms after typing stops before searching
            .onEach { query ->
                if (query.isBlank()) {
                    _uiState.update { it.copy(results = emptyList(), wikiResults = emptyList(), isSearching = false, isSearchingWiki = false) }
                } else {
                    _uiState.update { it.copy(isSearching = true, isSearchingWiki = true) }

                    // Run local and wiki search in parallel
                    val localDeferred = viewModelScope.async {
                        searchIndexBuilder.search(query)
                    }
                    val wikiDeferred = viewModelScope.async {
                        wikiSearchRepository.search(query)
                    }

                    // Emit local results as soon as they're ready
                    val localResults = localDeferred.await()
                    _uiState.update { it.copy(results = localResults, isSearching = false) }

                    // Emit wiki results when they arrive
                    when (val wikiResult = wikiDeferred.await()) {
                        is WikiSearchResult.Success -> _uiState.update { it.copy(wikiResults = wikiResult.entries, isSearchingWiki = false, wikiOffline = false) }
                        is WikiSearchResult.Offline -> _uiState.update { it.copy(wikiResults = emptyList(), isSearchingWiki = false, wikiOffline = true) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Called when search mode is activated (search icon tapped).
     */
    fun onSearchModeActivated() {
        // Rebuild on open so element visibility (pairing, pump type, advanced filtering …) is fresh for
        // this search session — cheap (lazy rebuild on the next query) and closes the prior never-invalidated gap.
        searchIndexBuilder.invalidateIndex()
        _uiState.update { it.copy(isSearchActive = true, query = "", results = emptyList(), wikiResults = emptyList()) }
        _searchQuery.value = ""
    }

    /**
     * Called when search mode is deactivated (back pressed or close tapped).
     */
    fun onSearchModeDeactivated() {
        _uiState.update { it.copy(isSearchActive = false, query = "", results = emptyList(), wikiResults = emptyList()) }
        _searchQuery.value = ""
    }

    /**
     * Called when user types in the search field.
     */
    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        _searchQuery.value = query
    }

    /**
     * Clears the current search query but keeps search mode active.
     */
    fun clearQuery() {
        _uiState.update { it.copy(query = "", results = emptyList(), wikiResults = emptyList()) }
        _searchQuery.value = ""
    }

    /**
     * Called when a search result is selected.
     * Returns the selected entry for navigation handling.
     */
    fun onResultSelected(entry: SearchIndexEntry): SearchIndexEntry {
        // Close search after selection
        onSearchModeDeactivated()
        return entry
    }

    /**
     * Remembers the current query as a recent search. Called when the user opens a result, so only
     * searches that led somewhere are kept: not every half-typed word. The newest search goes first,
     * a repeated search moves to the top, and the list is cut at [MAX_HISTORY].
     */
    fun recordCurrentQuery() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        val history = listOf(query) + _uiState.value.history.filter { !it.equals(query, ignoreCase = true) }
        saveHistory(history.take(MAX_HISTORY))
    }

    /** Removes one entry from the recent searches, for a search the user does not want to see again. */
    fun removeFromHistory(query: String) {
        saveHistory(_uiState.value.history.filter { it != query })
    }

    private fun saveHistory(history: List<String>) {
        preferences.put(StringNonKey.SearchHistory, Json.encodeToString(ListSerializer(String.serializer()), history))
        _uiState.update { it.copy(history = history) }
    }

    private fun loadHistory(): List<String> =
        try {
            Json.decodeFromString(ListSerializer(String.serializer()), preferences.get(StringNonKey.SearchHistory))
                .filter { it.isNotBlank() }
                .take(MAX_HISTORY)
        } catch (_: Exception) {
            // A damaged value is not worth an error: start with an empty list.
            emptyList()
        }

    companion object {

        /** How many recent searches are kept and shown. Ten fit on a phone screen without scrolling. */
        const val MAX_HISTORY = 10
    }

    /**
     * Invalidates the search index, forcing rebuild on next search.
     */
    fun invalidateIndex() {
        searchIndexBuilder.invalidateIndex()
    }
}

/**
 * UI state for the search feature.
 */
data class SearchUiState(
    val isSearchActive: Boolean = false,
    val query: String = "",
    val results: List<SearchIndexEntry> = emptyList(),
    val wikiResults: List<SearchIndexEntry> = emptyList(),
    /** Recent searches, newest first. Shown while the search field is empty. */
    val history: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isSearchingWiki: Boolean = false,
    val wikiOffline: Boolean = false,
    val hardwarePumpConfirmation: HardwarePumpConfirmation? = null,
    val pluginSwitchConfirmation: PluginSwitchConfirmation? = null,
    // Bumped whenever a plugin is toggled. A toggle changes plugin.isEnabled() (external, non-Compose state) but
    // NOT the result entries themselves, so the rebuilt results list is equals() to the previous one and the
    // StateFlow would conflate the update away — leaving switches/greying visually stale. This counter makes each
    // post-toggle state distinct so the emission (and the recomposition it drives) actually happens.
    val revision: Int = 0
)

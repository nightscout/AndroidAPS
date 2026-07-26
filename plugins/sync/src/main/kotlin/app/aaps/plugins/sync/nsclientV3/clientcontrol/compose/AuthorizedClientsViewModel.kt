package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.AuthorizedClient
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.core.nssdk.utils.ClientControlCrypto
import app.aaps.core.nssdk.utils.ClientControlPairingCrypto
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsclientV3.clientcontrol.AuthorizedClientsRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferPublisher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Stable
class AuthorizedClientsViewModel @Inject constructor(
    private val repository: AuthorizedClientsRepository,
    private val offerPublisher: PairingOfferPublisher,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val preferences: Preferences
) : ViewModel() {

    companion object {

        /**
         * PIN-entry window: how long a freshly-shown pairing PIN stays redeemable (2 min). This is the
         * human-facing "type the code before it expires" deadline — NOT the signed-command round-trip
         * TTL (`validUntil`) that bounds an individual control message's request/response lifetime.
         */
        const val PAIR_TTL_MS = 2L * 60L * 1000L
    }

    val clients: StateFlow<List<AuthorizedClient>> = repository.observe()
        .map { list -> list.sortedByDescending { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /**
     * Whether client-control communication is on — the master-wide stop/allow switch, now hosted on this screen.
     * Shows the EFFECTIVE value (the master's operative state, applying the simple-mode computed default), not the
     * raw stored value — so an unset switch in simple mode reads ON, matching what the command gate actually does,
     * instead of a misleading OFF. Re-read whenever the raw value or simple mode changes.
     */
    val clientControlEnabled: StateFlow<Boolean> =
        combine(
            preferences.observe(BooleanKey.NsClientAllowClientControl),
            preferences.observe(BooleanKey.GeneralSimpleMode)
        ) { _, _ -> preferences.get(BooleanKey.NsClientAllowClientControl) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferences.get(BooleanKey.NsClientAllowClientControl))

    /** Flip the stop/allow-communication switch. OFF keeps paired clients listed but stops the master accepting anything. */
    fun setClientControlEnabled(enabled: Boolean) = preferences.put(BooleanKey.NsClientAllowClientControl, enabled)

    private val _dialogState = MutableStateFlow<DialogState?>(null)
    val dialogState: StateFlow<DialogState?> = _dialogState.asStateFlow()

    /**
     * Active pairing offer: non-null while the PIN for a freshly-added client is on screen.
     * Holds the **plaintext PIN** in `pin` (toString masks it — see [PendingPairingOffer.toString]).
     * Intentionally not persisted — process death drops it, user must re-add to get a new PIN.
     */
    private val _pairingOffer = MutableStateFlow<PendingPairingOffer?>(null)
    val pairingOffer: StateFlow<PendingPairingOffer?> = _pairingOffer.asStateFlow()

    /**
     * Outstanding publishOffer job for the live pairing. dismissPairing awaits it before
     * deleteOffer fires, so a fast "Add then immediately Done" cannot let the delete arrive
     * before the publish — which would leave the offer doc orphaned on NS for the full TTL.
     *
     * `@Volatile`: written from viewModelScope launches (confirmAdd/retryPublish/dismissPairing)
     * and from the init collector, so publish the latest reference across threads.
     */
    @Volatile
    private var publishJob: Job? = null

    init {
        // Auto-dismiss the PIN dialog when the matching client flips Pending → Active. The receiver
        // already drops the offer doc on the same transition; here we just close the UI so the user
        // sees "paired" without having to tap Done.
        viewModelScope.launch {
            repository.observe().collect { list ->
                val offer = _pairingOffer.value ?: return@collect
                val matched = list.firstOrNull { it.clientId == offer.clientId } ?: return@collect
                if (matched.state == ClientState.Active) {
                    _pairingOffer.value = null
                    publishJob = null
                }
            }
        }
    }

    fun requestAdd() {
        _dialogState.value = DialogState.EnterName
    }

    /**
     * Adds a Pending entry, generates the PIN, publishes the wrapped offer to NS, and seeds
     * [pairingOffer] for the PIN dialog. The dialog renders the PIN immediately so the user
     * sees something while the publish runs; status flips to Published / Failed when the
     * publish completes so the UI can offer a retry instead of silently showing a non-working PIN.
     */
    fun confirmAdd(name: String) {
        _dialogState.value = null
        val now = dateUtil.now()
        val result = repository.addPending(name.trim(), PAIR_TTL_MS, now)
        val payload = PairingPayload(
            masterInstallId = ownInstallId(),
            clientId = result.entry.clientId,
            secretHex = result.secretHex,
            expiresAt = result.entry.pairExpiresAt
        )
        val pin = ClientControlPairingCrypto.newPin()
        _pairingOffer.value = PendingPairingOffer(
            pin = pin,
            expiresAt = result.entry.pairExpiresAt,
            clientId = result.entry.clientId,
            publishStatus = PublishStatus.Loading
        )
        publishJob = viewModelScope.launch {
            val ok = offerPublisher.publishOffer(payload, pin)
            // Only update if the dialog is still showing this offer — user may have dismissed already.
            _pairingOffer.update { current ->
                if (current?.clientId == payload.clientId)
                    current.copy(publishStatus = if (ok) PublishStatus.Published else PublishStatus.Failed)
                else current
            }
        }
    }

    /**
     * User explicitly retried after a failed publish — re-runs the wrap+upload with the same PIN
     * so the user doesn't have to dictate a new code to the client.
     */
    fun retryPublish() {
        val offer = _pairingOffer.value ?: return
        if (offer.publishStatus != PublishStatus.Failed) return
        val entry = repository.findRaw(offer.clientId) ?: return
        // Don't re-publish an offer whose PIN window has already closed — surface Failed instead so
        // the user re-adds for a fresh PIN rather than uploading a doc no client could ever redeem.
        if (dateUtil.now() >= entry.pairExpiresAt) {
            _pairingOffer.update { it?.copy(publishStatus = PublishStatus.Failed) }
            return
        }
        val secretHex = repository.secretLookup(entry.clientId)?.secretBytes
            ?.let { ClientControlCrypto.bytesToHex(it) } ?: return
        val payload = PairingPayload(
            masterInstallId = ownInstallId(),
            clientId = entry.clientId,
            secretHex = secretHex,
            expiresAt = entry.pairExpiresAt
        )
        _pairingOffer.update { it?.copy(publishStatus = PublishStatus.Loading) }
        publishJob = viewModelScope.launch {
            val ok = offerPublisher.publishOffer(payload, offer.pin)
            _pairingOffer.update { current ->
                if (current?.clientId == entry.clientId)
                    current.copy(publishStatus = if (ok) PublishStatus.Published else PublishStatus.Failed)
                else current
            }
        }
    }

    /**
     * Called when the PIN dialog dismisses (user taps Done, presses back, or expiry hits).
     * Awaits the publish job so the delete cannot race ahead and leave an orphan offer on NS.
     */
    fun dismissPairing() {
        val offer = _pairingOffer.value
        _pairingOffer.value = null
        if (offer == null) return
        val pendingPublish = publishJob
        publishJob = null
        viewModelScope.launch {
            pendingPublish?.join()
            offerPublisher.deleteOffer(offer.clientId)
        }
    }

    fun requestDelete(client: AuthorizedClient) {
        _dialogState.value = DialogState.ConfirmDelete(client)
    }

    fun confirmDelete() {
        val state = _dialogState.value as? DialogState.ConfirmDelete ?: return
        repository.delete(state.client.clientId)
        // Re-check the current state from the repository, not the snapshot in DialogState — between
        // requestDelete and confirmDelete the entry may have flipped Pending → Active (offer doc
        // already deleted by the receiver) or already pruned.
        viewModelScope.launch { offerPublisher.deleteOffer(state.client.clientId) }
        _dialogState.value = null
    }

    fun dismissDialog() {
        _dialogState.value = null
    }

    /**
     * Drop pending entries past their pairExpiresAt and delete the matching offer docs from NS.
     * Drives delete off the actual repository-prune result instead of a `clients.value` snapshot
     * (which can lag the prefs due to `WhileSubscribed(5_000)` collector pauses).
     *
     * Intentionally driven from the screen's 1 Hz poll loop (only while a Pending entry is shown),
     * not a background ticker — pruning has no value when nobody is watching the list, so this
     * avoids a battery-wasting always-on timer for a purely cosmetic countdown.
     */
    fun pruneExpired() {
        val droppedIds = repository.pruneExpired(dateUtil.now())
        if (droppedIds.isEmpty()) return
        viewModelScope.launch {
            droppedIds.forEach { offerPublisher.deleteOffer(it) }
        }
    }

    /** "Last seen 5m ago" / "Never used" — formatted from the entry's lastSeenAt. */
    fun lastSeenLabel(client: AuthorizedClient): String =
        if (client.lastSeenAt > 0L) rh.gs(R.string.authorized_clients_last_seen, dateUtil.minAgo(rh, client.lastSeenAt))
        else rh.gs(R.string.authorized_clients_never_seen)

    /** "Pairing expires in 95s" — re-evaluated on each composition while a Pending tick is running. */
    fun pendingExpiresLabel(client: AuthorizedClient): String {
        val secondsLeft = ((client.pairExpiresAt - dateUtil.now()) / 1000L).coerceAtLeast(0L)
        return rh.gs(R.string.authorized_clients_pending_expires_in, secondsLeft.toString())
    }

    /** Lazy UUID generated on first use; persists across app restarts so paired clients keep matching. */
    private fun ownInstallId(): String {
        val existing = preferences.get(StringNonKey.NsClientControlOwnInstallId)
        if (existing.isNotEmpty()) return existing
        val fresh = ClientControlCrypto.newClientId()
        preferences.put(StringNonKey.NsClientControlOwnInstallId, fresh)
        return fresh
    }

    sealed class DialogState {
        data object EnterName : DialogState()
        data class ConfirmDelete(val client: AuthorizedClient) : DialogState()
    }

    enum class PublishStatus { Loading, Published, Failed }

    /**
     * UI-facing pairing offer: the PIN to read out + when it expires + which client it belongs to.
     * Custom toString masks the pin so a future debug log that interpolates this object cannot leak
     * the secret — data-class auto-toString would print `pin=12345678` verbatim.
     */
    data class PendingPairingOffer(
        val pin: String,
        val expiresAt: Long,
        val clientId: String,
        val publishStatus: PublishStatus = PublishStatus.Loading
    ) {

        override fun toString(): String =
            "PendingPairingOffer(pin=****, expiresAt=$expiresAt, clientId=$clientId, publishStatus=$publishStatus)"
    }
}

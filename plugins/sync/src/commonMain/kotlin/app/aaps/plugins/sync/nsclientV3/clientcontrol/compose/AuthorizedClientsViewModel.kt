package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.observeEffective
import app.aaps.core.nssdk.localmodel.authorization.NSAuthSubject
import app.aaps.core.nssdk.localmodel.clientcontrol.AuthorizedClient
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientSetupCode
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.core.nssdk.utils.ClientControlCrypto
import app.aaps.core.nssdk.utils.ClientControlPairingCrypto
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nsclientV3.clientcontrol.AuthorizedClientsRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientTokens
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferPublisher
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
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
import kotlin.concurrent.Volatile

@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class AuthorizedClientsViewModel(
    private val repository: AuthorizedClientsRepository,
    private val offerPublisher: PairingOfferPublisher,
    private val clientTokens: ClientTokens,
    private val dateUtil: DateUtil,
    private val rh: TextResolver,
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
     * On the master this is also hardened mode ([Preferences.hardenedMode]).
     * Shows the EFFECTIVE value (the master's operative state, applying the simple-mode rule), not the
     * raw stored value — so the switch in simple mode reads ON, matching what the command gate actually does,
     * instead of a misleading OFF.
     */
    val clientControlEnabled: StateFlow<Boolean> =
        preferences.observeEffective(BooleanKey.HardenedMode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), preferences.get(BooleanKey.HardenedMode))

    /**
     * Simple mode forces the switch on, so the screen does not offer it there: it would be a switch that
     * cannot be turned off.
     */
    val simpleMode: StateFlow<Boolean> = preferences.observe(BooleanKey.GeneralSimpleMode)

    /** Flip the stop/allow-communication switch. OFF keeps paired clients listed but stops the master accepting anything. */
    fun setClientControlEnabled(enabled: Boolean) = preferences.put(BooleanKey.HardenedMode, enabled)

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

    /** The Nightscout subjects (without their tokens), for the token of each client and the tokens without a client. */
    private val _tokens = MutableStateFlow<TokensState>(TokensState.Loading)
    val tokens: StateFlow<TokensState> = _tokens.asStateFlow()

    /** Client tokens that no client in the list writes with: phones set up to read Nightscout, not paired. */
    val unpairedTokens: StateFlow<List<NSAuthSubject>> = combine(clients, _tokens) { list, state ->
        if (state !is TokensState.Ready) emptyList()
        else state.subjects.filter { subject -> clientTokens.isClientToken(subject) && list.none { it.nsSubject == subject.name } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** For each client id: is the token it writes with a client token, another (stronger) token, or not known yet. */
    val tokenKinds: StateFlow<Map<String, TokenKind>> = combine(clients, _tokens) { list, state ->
        list.associate { client -> client.clientId to tokenKind(client, state) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /** The QR code for a new client, or the step before it (making the token) or what stopped it. */
    private val _qrDialog = MutableStateFlow<QrDialog?>(null)
    val qrDialog: StateFlow<QrDialog?> = _qrDialog.asStateFlow()

    /** Making the token for [qrDialog]; cancelled when the dialog is closed before it is done. */
    @Volatile
    private var qrJob: Job? = null

    init {
        // Auto-dismiss the PIN dialog when the matching client flips Pending → Active. The receiver
        // already drops the offer doc on the same transition; here we just close the UI so the user
        // sees "paired" without having to tap Done. The same for the QR code that pairs.
        viewModelScope.launch {
            repository.observe().collect { list ->
                val offer = _pairingOffer.value
                if (offer != null && list.firstOrNull { it.clientId == offer.clientId }?.state == ClientState.Active) {
                    _pairingOffer.value = null
                    publishJob = null
                }
                val qr = _qrDialog.value as? QrDialog.Shown
                if (qr?.clientId != null && list.firstOrNull { it.clientId == qr.clientId }?.state == ClientState.Active)
                    _qrDialog.value = null
            }
        }
        refreshTokens()
    }

    /**
     * Reads the subjects from Nightscout again. Also sets the client role back if it was changed in Nightscout, so
     * opening this screen is enough to repair it.
     */
    fun refreshTokens() {
        viewModelScope.launch {
            clientTokens.ensureRole(create = false)
            _tokens.value = when (val result = clientTokens.subjects()) {
                is ClientTokens.Result.Ok -> TokensState.Ready(result.value)
                else                      -> TokensState.Unavailable(result.problem())
            }
        }
    }

    fun requestAdd() {
        _dialogState.value = DialogState.EnterName
    }

    /** True when a client with this name is in the list already; a QR code needs a name of its own for its token. */
    private suspend fun nameInUse(name: String): Boolean =
        repository.current(dateUtil.now()).any { it.name.trim().equals(name.trim(), ignoreCase = true) }

    /**
     * Adds a client the chosen [method]. [role] is needed by the two methods that pair; a phone that only reads
     * Nightscout has none.
     */
    fun confirmAdd(name: String, role: ClientRole?, method: AddMethod) {
        when (method) {
            AddMethod.Pin              -> role?.let { addWithPin(name, it) }
            AddMethod.QrWithPairing    -> role?.let { addWithQr(name, it) }
            AddMethod.QrNightscoutOnly -> addWithQr(name, null)
        }
    }

    /**
     * A QR code with the Nightscout address and the client's own token, and with [role] also the pairing. The token is
     * made first: without it there is nothing to show.
     */
    private fun addWithQr(name: String, role: ClientRole?) {
        _dialogState.value = null
        _qrDialog.value = QrDialog.Working
        qrJob = viewModelScope.launch {
            val clientName = name.trim()
            if (nameInUse(clientName)) {
                _qrDialog.value = QrDialog.Problem(TokenProblem.NameInUse)
                return@launch
            }
            val subject = when (val result = clientTokens.tokenFor(clientName)) {
                is ClientTokens.Result.Ok -> result.value
                else                      -> {
                    _qrDialog.value = QrDialog.Problem(result.problem())
                    return@launch
                }
            }
            val token = subject.accessToken ?: run {
                _qrDialog.value = QrDialog.Problem(TokenProblem.Failed)
                return@launch
            }
            val url = preferences.get(StringKey.NsClientUrl)
            if (role == null) {
                _qrDialog.value = QrDialog.Shown(
                    text = ClientSetupCode.encode(ClientSetupCode(url = url, token = token)),
                    clientName = clientName,
                    pairs = false,
                    expiresAt = null,
                    clientId = null
                )
            } else {
                val result = repository.addPending(clientName, role, PAIR_TTL_MS, dateUtil.now(), nsSubject = subject.name)
                val pairing = PairingPayload(
                    masterInstallId = ownInstallId(),
                    clientId = result.entry.clientId,
                    secretHex = result.secretHex,
                    expiresAt = result.entry.pairExpiresAt
                )
                _qrDialog.value = QrDialog.Shown(
                    text = ClientSetupCode.encode(ClientSetupCode(url = url, token = token, pairing = pairing)),
                    clientName = clientName,
                    pairs = true,
                    expiresAt = result.entry.pairExpiresAt,
                    clientId = result.entry.clientId
                )
            }
            refreshTokens()
        }
    }

    /** Shows the QR code of a token without a client again, for setting up the same phone again. It does not pair. */
    fun showTokenQr(subject: NSAuthSubject) {
        val id = subject.id ?: return
        _qrDialog.value = QrDialog.Working
        qrJob = viewModelScope.launch {
            _qrDialog.value = when (val result = clientTokens.token(id)) {
                is ClientTokens.Result.Ok -> result.value.accessToken?.let { token ->
                    QrDialog.Shown(
                        text = ClientSetupCode.encode(ClientSetupCode(url = preferences.get(StringKey.NsClientUrl), token = token)),
                        clientName = displayName(subject),
                        pairs = false,
                        expiresAt = null,
                        clientId = null
                    )
                } ?: QrDialog.Problem(TokenProblem.Failed)

                else                      -> QrDialog.Problem(result.problem())
            }
        }
    }

    /**
     * Closes the QR code dialog. While the token is being made it also stops that: a slow Nightscout must not hold the
     * dialog open. A token already made by then stays, listed under "Read Nightscout only".
     */
    fun dismissQr() {
        qrJob?.cancel()
        qrJob = null
        _qrDialog.value = null
    }

    fun requestDeleteToken(subject: NSAuthSubject) {
        _dialogState.value = DialogState.ConfirmDeleteToken(subject)
    }

    /** The client name in a token name: the token of "Tablet" is `aaps-Tablet`. */
    fun displayName(subject: NSAuthSubject): String = subject.name.removePrefix(ClientTokens.SUBJECT_PREFIX)

    /**
     * Adds a Pending entry, generates the PIN, publishes the wrapped offer to NS, and seeds
     * [pairingOffer] for the PIN dialog. The dialog renders the PIN immediately so the user
     * sees something while the publish runs; status flips to Published / Failed when the
     * publish completes so the UI can offer a retry instead of silently showing a non-working PIN.
     */
    private fun addWithPin(name: String, role: ClientRole) {
        _dialogState.value = null
        // One coroutine for the whole thing now that addPending suspends: it takes the repository
        // mutex across preference I/O. This stays a plain function because the screen passes it as a
        // click handler, and viewModelScope is Main.immediate, so the state writes below still land
        // on this frame unless the mutex is actually contended.
        publishJob = viewModelScope.launch {
            val now = dateUtil.now()
            val result = repository.addPending(name.trim(), role, PAIR_TTL_MS, now)
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
        // Same reason as confirmAdd: findRaw and secretLookup take the repository mutex, so the body
        // moved into the coroutine. The guards keep their order, they just return from the launch.
        publishJob = viewModelScope.launch {
            val entry = repository.findRaw(offer.clientId) ?: return@launch
            // Don't re-publish an offer whose PIN window has already closed — surface Failed instead so
            // the user re-adds for a fresh PIN rather than uploading a doc no client could ever redeem.
            if (dateUtil.now() >= entry.pairExpiresAt) {
                _pairingOffer.update { it?.copy(publishStatus = PublishStatus.Failed) }
                return@launch
            }
            val secretHex = repository.secretLookup(entry.clientId)?.secretBytes
                ?.let { ClientControlCrypto.bytesToHex(it) } ?: return@launch
            val payload = PairingPayload(
                masterInstallId = ownInstallId(),
                clientId = entry.clientId,
                secretHex = secretHex,
                expiresAt = entry.pairExpiresAt
            )
            _pairingOffer.update { it?.copy(publishStatus = PublishStatus.Loading) }
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

    /** Give [client] another role. The master enforces it from the next command; the client learns it from the roster. */
    fun setRole(client: AuthorizedClient, role: ClientRole) {
        viewModelScope.launch { repository.setRole(client.clientId, role) }
    }

    fun requestDelete(client: AuthorizedClient) {
        _dialogState.value = DialogState.ConfirmDelete(client)
    }

    fun confirmDelete() {
        when (val state = _dialogState.value) {
            is DialogState.ConfirmDelete      -> deleteClient(state.client)
            is DialogState.ConfirmDeleteToken -> deleteToken(state.subject)
            else                              -> return
        }
        _dialogState.value = null
    }

    private fun deleteClient(client: AuthorizedClient) {
        // delete takes the repository mutex, so it joins the coroutine that was already here. The
        // order is unchanged: the entry goes first, then its offer doc.
        viewModelScope.launch {
            repository.delete(client.clientId)
            // Re-check the current state from the repository, not the snapshot in DialogState — between
            // requestDelete and confirmDelete the entry may have flipped Pending → Active (offer doc
            // already deleted by the receiver) or already pruned.
            offerPublisher.deleteOffer(client.clientId)
            // Its token goes too, so the phone cannot read Nightscout any more. Only a client token: a token with
            // other roles is the user's own. If Nightscout cannot be reached now, the token stays and is listed as a
            // token without a client, to delete later.
            client.nsSubject?.let { clientTokens.deleteByName(it) }
            refreshTokens()
        }
    }

    private fun deleteToken(subject: NSAuthSubject) {
        val id = subject.id ?: return
        viewModelScope.launch {
            clientTokens.delete(id)
            refreshTokens()
        }
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
    // suspend, not a launch: the only caller is a LaunchedEffect, which is already a coroutine.
    suspend fun pruneExpired() {
        val droppedIds = repository.pruneExpired(dateUtil.now())
        if (droppedIds.isEmpty()) return
        droppedIds.forEach { offerPublisher.deleteOffer(it) }
    }

    /** "Last seen 5m ago" / "Never used" — formatted from the entry's lastSeenAt. */
    fun lastSeenLabel(client: AuthorizedClient): String =
        if (client.lastSeenAt > 0L) rh.gs(SyncStrings.authorized_clients_last_seen, dateUtil.minAgo(rh, client.lastSeenAt))
        else rh.gs(SyncStrings.authorized_clients_never_seen)

    /** "Pairing expires in 95s" — re-evaluated on each composition while a Pending tick is running. */
    fun pendingExpiresLabel(client: AuthorizedClient): String {
        val secondsLeft = ((client.pairExpiresAt - dateUtil.now()) / 1000L).coerceAtLeast(0L)
        return rh.gs(SyncStrings.authorized_clients_pending_expires_in, secondsLeft.toString())
    }

    /** Lazy UUID generated on first use; persists across app restarts so paired clients keep matching. */
    private fun ownInstallId(): String {
        val existing = preferences.get(StringNonKey.NsClientControlOwnInstallId)
        if (existing.isNotEmpty()) return existing
        val fresh = ClientControlCrypto.newClientId()
        preferences.put(StringNonKey.NsClientControlOwnInstallId, fresh)
        return fresh
    }

    private fun tokenKind(client: AuthorizedClient, state: TokensState): TokenKind {
        val name = client.nsSubject ?: return TokenKind.Unknown
        if (state !is TokensState.Ready) return TokenKind.Unknown
        val sameName = state.subjects.filter { it.name == name }
        return when {
            sameName.isEmpty()                                -> TokenKind.Unknown
            sameName.all { clientTokens.isClientToken(it) } -> TokenKind.ClientToken
            else                                              -> TokenKind.OtherToken
        }
    }

    private fun ClientTokens.Result<*>.problem(): TokenProblem = when (this) {
        ClientTokens.Result.NotConnected -> TokenProblem.NotConnected
        ClientTokens.Result.NotAllowed   -> TokenProblem.NotAllowed
        ClientTokens.Result.NameTaken    -> TokenProblem.NameTaken
        is ClientTokens.Result.Failed,
        is ClientTokens.Result.Ok        -> TokenProblem.Failed
    }

    sealed class DialogState {
        data object EnterName : DialogState()
        data class ConfirmDelete(val client: AuthorizedClient) : DialogState()
        data class ConfirmDeleteToken(val subject: NSAuthSubject) : DialogState()
    }

    /** How a new client is added. */
    enum class AddMethod {

        /** The client is set up with Nightscout already and types the PIN shown here. For phones without a camera. */
        Pin,

        /** A QR code with Nightscout, the client's own token and the pairing: the client is ready after one scan. */
        QrWithPairing,

        /** A QR code with Nightscout and the client's own token, no pairing: a phone that only reads Nightscout. */
        QrNightscoutOnly
    }

    sealed interface TokensState {
        data object Loading : TokensState
        data class Ready(val subjects: List<NSAuthSubject>) : TokensState
        data class Unavailable(val problem: TokenProblem) : TokensState
    }

    enum class TokenProblem {

        /** Nightscout is not set up or not connected. */
        NotConnected,

        /** The master's token cannot manage tokens: it is not an admin token. */
        NotAllowed,

        /** A Nightscout token with this name has other roles, so it is not used for a client. */
        NameTaken,

        /** A client with this name is in the list already. */
        NameInUse,
        Failed
    }

    enum class TokenKind {

        /** Not known: the client has sent nothing since this was added, or the tokens cannot be read. */
        Unknown,

        /** Its own token with the client role: it can write to the settings collection only. */
        ClientToken,

        /** A token with more rights than a client needs, for example the user's admin token. */
        OtherToken
    }

    sealed interface QrDialog {

        /** Making the token in Nightscout. */
        data object Working : QrDialog

        data class Problem(val problem: TokenProblem) : QrDialog

        /**
         * [text] is SENSITIVE: the Nightscout token, and when [pairs], the pairing secret. [expiresAt] and [clientId] are
         * set when it pairs: the code works until then, and the dialog closes when that client says hello.
         */
        class Shown(val text: String, val clientName: String, val pairs: Boolean, val expiresAt: Long?, val clientId: String?) : QrDialog {

            override fun toString(): String = "Shown(text=****, clientName=$clientName, pairs=$pairs, expiresAt=$expiresAt, clientId=$clientId)"
        }
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

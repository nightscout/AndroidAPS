package app.aaps.plugins.eversense.pairing

import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.source.CgmAttention
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * [EversensePairing] where there is no Eversense driver yet (iOS, desktop): nothing is paired, and nothing can be.
 *
 * Nothing reaches it either. The plugin whose screen opens the wizard is Android only, so the feature is absent there,
 * not present and dead. It exists because the wizard's view model is shared code and registered on every platform,
 * and the graph needs an answer for it. A driver for that platform replaces this binding.
 */
class NoEversenseDriver : EversensePairing {

    override val account: StateFlow<EversenseAccount?> = MutableStateFlow(null)
    override val transmitter: StateFlow<String?> = MutableStateFlow(null)
    override val attention: StateFlow<CgmAttention?> = MutableStateFlow(CgmAttention.NOT_PAIRED)

    // Nothing is stored and no server is asked: there is no driver to use the login
    override suspend fun signIn(username: String, password: String, model: EversenseModel): SignInResult = SignInResult.SERVER_ERROR
    override fun signOut() = Unit
    override fun scan(): Flow<List<ScannedDevice>> = emptyFlow()
    override suspend fun pair(transmitter: ScannedDevice): PairResult = PairResult.NOT_STARTED
    override fun unpair() = Unit
}

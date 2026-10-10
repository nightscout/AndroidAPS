package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.sync.tidepool.compose.TidepoolRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal class TidepoolCallback<T>(
    private val aapsLogger: AAPSLogger,
    private val tidepoolRepository: TidepoolRepository,
    private val session: Session,
    private val name: String,
    private val onSuccess: () -> Unit,
    private val onFail: () -> Unit
) :
    Callback<T> {

    private var coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onResponse(call: Call<T>, response: Response<T>) {
        coroutineScope.launch {
            if (response.isSuccessful && response.body() != null) {
                aapsLogger.debug(LTag.TIDEPOOL, "$name success")
                session.populateBody(response.body())
                session.populateHeaders(response.headers())
                onSuccess()
            } else {
                val msg = name + " was not successful: " + response.code() + " " + response.message()
                aapsLogger.debug(LTag.TIDEPOOL, msg)
                tidepoolRepository.addLog(msg)
                onFail()
            }
        }
    }

    override fun onFailure(call: Call<T>, t: Throwable) {
        coroutineScope.launch {
            val msg = "$name Failed: $t"
            aapsLogger.debug(LTag.TIDEPOOL, msg)
            tidepoolRepository.addLog(msg)
            onFail()
        }
    }

}

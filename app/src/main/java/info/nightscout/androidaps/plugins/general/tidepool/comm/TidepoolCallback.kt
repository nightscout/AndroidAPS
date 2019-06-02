package info.nightscout.androidaps.plugins.general.tidepool.comm

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolStatus
import org.slf4j.LoggerFactory
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal class TidepoolCallback<T>(val session: Session, val name: String, val onSucc: () -> Unit, val onFail: () -> Unit) : Callback<T> {
    private val log = LoggerFactory.getLogger(L.TIDEPOOL)

    override fun onResponse(call: Call<T>, response: Response<T>) {
        if (response.isSuccessful && response.body() != null) {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("$name success")
            session.populateBody(response.body())
            session.populateHeaders(response.headers())
            onSucc()
        } else {
            val msg = name + " was not successful: " + response.code() + " " + response.message()
            if (L.isEnabled(L.TIDEPOOL)) log.debug(msg)
            status(msg)
            onFail()
        }
    }

    override fun onFailure(call: Call<T>, t: Throwable) {
        val msg = "$name Failed: $t"
        if (L.isEnabled(L.TIDEPOOL)) log.debug(msg)
        status(msg)
        onFail()
    }

    private fun status(status: String) {
        MainApp.bus().post(EventTidepoolStatus(status))
    }
}

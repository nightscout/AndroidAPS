package info.nightscout.pump.diaconn.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaconnLogUploader @Inject constructor(
    private val aapsLogger: AAPSLogger,
) {

    companion object {

        private const val BASE_URL = "https://api.diaconn.com/aaps/"
        const val UPLOAD_API_KEY = "D7B3DA9FA8229D5253F3D75E1E2B1BA4"
    }

    private var retrofit: Retrofit? = null

    fun getRetrofitInstance(): Retrofit? {
        aapsLogger.debug(LTag.PUMPCOMM, "diaconn pump logs upload instance")
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit
    }
}
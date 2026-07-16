package app.aaps.pump.aaruy.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import javax.inject.Inject
import javax.inject.Singleton

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Singleton
class AaruyLogUploader@Inject constructor(
    private val aapsLogger: AAPSLogger,
) {

    companion object {

        private const val BASE_URL = "https://api.aaruy.com/"       // https://api.aaruy.com/
    }

    private var retrofit: Retrofit? = null

    fun getRetrofitInstance(): Retrofit? {
        aapsLogger.debug(LTag.PUMPCOMM, "aaruy pump logs upload instance")
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit
    }
}
package app.aaps.pump.aaruy.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Suppress("SpellCheckingInspection")
@SingleIn(AppScope::class)
@Inject
class AaruyLogUploader internal constructor(
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
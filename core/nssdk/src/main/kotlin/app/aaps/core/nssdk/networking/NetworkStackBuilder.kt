package app.aaps.core.nssdk.networking

import android.content.Context
import app.aaps.core.nssdk.nsSdkJson
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

internal object NetworkStackBuilder {

    @JvmSynthetic
    internal fun getApi(
        baseUrl: String,
        context: Context,
        accessToken: String, // refresh token
        logging: Boolean,
        logger: HttpLoggingInterceptor.Logger
    ): NightscoutRemoteService = getRetrofit(
        baseUrl = baseUrl,
        context = context,
        refreshToken = accessToken,
        logging = logging,
        logger = logger
    ).create(NightscoutRemoteService::class.java)

    private fun getRetrofit(
        baseUrl: String,
        context: Context,
        refreshToken: String,
        logging: Boolean,
        logger: HttpLoggingInterceptor.Logger
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://$baseUrl/api/")
            .client(
                getOkHttpClient(
                    context = context,
                    logging = logging,
                    refreshToken = refreshToken,
                    authRefreshRetrofit = getAuthRefreshRetrofit(baseUrl, context, logging, logger),
                    logger = logger
                )
            )
            .addConverterFactory(converterFactory)
            .build()

    private fun getAuthRefreshRetrofit(
        baseUrl: String,
        context: Context,
        logging: Boolean,
        logger: HttpLoggingInterceptor.Logger
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://$baseUrl/api/")
            .client(getAuthRefreshOkHttpClient(context = context, logging = logging, logger = logger))
            .addConverterFactory(converterFactory)
            .build()

    private fun getOkHttpClient(
        context: Context,
        logging: Boolean,
        refreshToken: String,
        authRefreshRetrofit: Retrofit,
        logger: HttpLoggingInterceptor.Logger
    ): OkHttpClient = OkHttpClient.Builder().run {
        addInterceptor(NSAuthInterceptor(refreshToken, authRefreshRetrofit))
        commonOkHttpSetup(logging, context, logger)
    }

    private fun getAuthRefreshOkHttpClient(
        context: Context,
        logging: Boolean,
        logger: HttpLoggingInterceptor.Logger
    ): OkHttpClient = OkHttpClient.Builder().run { commonOkHttpSetup(logging, context, logger) }

    private fun OkHttpClient.Builder.commonOkHttpSetup(
        logging: Boolean,
        context: Context,
        logger: HttpLoggingInterceptor.Logger
    ): OkHttpClient {
        if (logging) {
            addNetworkInterceptor(
                HttpLoggingInterceptor(logger).also { it.level = HttpLoggingInterceptor.Level.BODY }
            )
        }
        cache(Cache(context.cacheDir, OK_HTTP_CACHE_SIZE))
        readTimeout(OK_HTTP_READ_TIMEOUT, TimeUnit.MILLISECONDS)
        writeTimeout(OK_HTTP_WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
        return build()
    }

    // The schema-less documents (profiles, settings) are carried as kotlinx JsonObject. Gson needed a
    // registered type adapter to build those; kotlinx reads JsonObject natively, so the adapter and
    // the Gson instance behind it are gone.
    private val converterFactory = nsSdkJson.asConverterFactory("application/json".toMediaType())

    private const val OK_HTTP_CACHE_SIZE = 10L * 1024 * 1024
    private const val OK_HTTP_READ_TIMEOUT = 60L * 1000
    private const val OK_HTTP_WRITE_TIMEOUT = 60L * 1000
}

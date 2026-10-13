package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.exceptions.DateHeaderOutOfToleranceException
import app.aaps.core.nssdk.exceptions.InvalidAccessTokenException
import app.aaps.core.nssdk.networking.Status.MESSAGE_DATE_HEADER_OUT_OF_TOLERANCE
import app.aaps.core.nssdk.remotemodel.RemoteAuthResponse
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import java.lang.System.currentTimeMillis

internal class NSAuthInterceptor(private val refreshToken: String, private val retrofit: Retrofit) :
    Interceptor {

    private var jwtToken = "" // the actual Bearer token

    @Suppress("MagicNumber")
    override fun intercept(chain: Interceptor.Chain): Response {

        val originalRequest = chain.request()
        val authenticationRequest = requestWithBearer(originalRequest)
        val initialResponse = chain.proceed(authenticationRequest)

        return when (initialResponse.code) {
            403, 401 -> refreshTokenAndRetry(originalRequest, initialResponse, chain)
            else -> initialResponse
        }
    }

    private fun requestWithBearer(originalRequest: Request): Request = originalRequest.newBuilder()
        .addHeader("Date", currentTimeMillis().toString())
        .addHeader("Authorization", "Bearer $jwtToken")
        .build()

    @Suppress("MagicNumber")
    private fun refreshTokenAndRetry(
        originalRequest: Request,
        initialResponse: Response,
        chain: Interceptor.Chain
    ): Response {

        testCanRefresh(initialResponse)

        val authResponseResponse: retrofit2.Response<RemoteAuthResponse>? = retrofit
            .create(NightscoutAuthRefreshService::class.java)
            .refreshToken(refreshToken)
            .execute()

        return when {
            authResponseResponse == null -> initialResponse
            authResponseResponse.code() in listOf(401, 403) -> throw InvalidAccessTokenException("Invalid access token")
            authResponseResponse.code() != 200 -> initialResponse
            else -> {
                authResponseResponse.body()?.token?.let { jwtToken = it }
                val newAuthenticationRequest = requestWithBearer(originalRequest)
                chain.proceed(newAuthenticationRequest)
            }
        }
    }

    private fun testCanRefresh(initialResponse: Response) {
        // Todo: use proper reason code once it is supplied by remote
        if (initialResponse.body.string().contains(MESSAGE_DATE_HEADER_OUT_OF_TOLERANCE)) {
            throw DateHeaderOutOfToleranceException("Data header out of tolerance")
        }
    }
}

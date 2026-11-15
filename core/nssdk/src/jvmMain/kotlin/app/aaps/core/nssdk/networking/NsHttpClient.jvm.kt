package app.aaps.core.nssdk.networking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

/**
 * JVM and Android engine: **OkHttp**.
 *
 * The app already ships OkHttp for other things, so using it here adds Ktor's own layers rather than
 * a second HTTP stack - which was the point of choosing this engine over CIO.
 *
 * The logging interceptor is OkHttp's, which is why request logging lives in the actual rather than
 * in the shared configuration. An Apple target would install Darwin here and log differently.
 */
internal actual fun nsHttpClient(
    logging: Boolean,
    logger: (String) -> Unit,
    configure: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(OkHttp) {
    configure()
    if (logging) {
        engine {
            addInterceptor { chain ->
                val request = chain.request()
                logger("--> ${request.method} ${request.url}")
                val response = chain.proceed(request)
                logger("<-- ${response.code} ${request.url}")
                response
            }
        }
    }
}

package app.aaps.plugins.eversense.util

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual val dmsHttpEngine: HttpClientEngineFactory<*> = OkHttp

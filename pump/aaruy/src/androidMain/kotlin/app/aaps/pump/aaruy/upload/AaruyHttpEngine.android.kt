package app.aaps.pump.aaruy.upload

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual val aaruyHttpEngine: HttpClientEngineFactory<*> = OkHttp

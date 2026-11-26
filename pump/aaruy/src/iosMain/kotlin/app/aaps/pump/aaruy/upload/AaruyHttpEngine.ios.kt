package app.aaps.pump.aaruy.upload

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual val aaruyHttpEngine: HttpClientEngineFactory<*> = Darwin

package app.aaps.plugins.eversense.util

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual val dmsHttpEngine: HttpClientEngineFactory<*> = Darwin

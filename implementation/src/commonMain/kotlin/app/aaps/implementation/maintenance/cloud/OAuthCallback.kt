package app.aaps.implementation.maintenance.cloud

/**
 * Reading the one HTTP request that finishes an OAuth sign in.
 *
 * The flow ends with the browser being redirected to `http://localhost:<port>/oauth/callback?...`,
 * which the app answers itself by listening on that port. Everything about that is platform work
 * except this: deciding what the request line means. So it lives here, where it can be tested on
 * every platform without a socket, and where Android and iOS cannot come to different conclusions
 * about the same redirect.
 *
 * Only the request line is looked at - `GET /oauth/callback?code=... HTTP/1.1`. The headers carry
 * nothing this needs.
 */
object OAuthCallback {

    /** What the browser came back with. */
    sealed interface Result {

        /** Signed in. [state] is checked by the caller against the value it sent. */
        data class Code(val code: String, val state: String?) : Result

        /** The user said no, or Google refused. Not an error to report as a failure of ours. */
        data class Denied(val error: String) : Result

        /** Some other request reached the port. Answered with 404 and otherwise ignored. */
        data object NotTheCallback : Result

        /** A request line that is not one. */
        data object Malformed : Result
    }

    private const val CALLBACK_PATH = "/oauth/callback"

    /**
     * Reads a request line such as `GET /oauth/callback?code=abc&state=xyz HTTP/1.1`.
     *
     * A redirect that carries neither `code` nor `error` is [Malformed] rather than a code of empty
     * string: an empty code would be sent to Google and fail there, with an error that describes
     * nothing.
     */
    fun parseRequestLine(line: String): Result {
        val parts = line.trim().split(" ")
        if (parts.size < 2) return Result.Malformed

        val target = parts[1]
        val path = target.substringBefore('?')
        if (path != CALLBACK_PATH) return Result.NotTheCallback

        val query = target.substringAfter('?', "")
        if (query.isEmpty()) return Result.Malformed

        val values = query.split('&').mapNotNull { pair ->
            val name = pair.substringBefore('=', "")
            val value = pair.substringAfter('=', "")
            if (name.isEmpty()) null else name to percentDecode(value)
        }.toMap()

        values["error"]?.let { return Result.Denied(it) }
        val code = values["code"]
        return if (code.isNullOrEmpty()) Result.Malformed else Result.Code(code, values["state"])
    }

    /** What the browser is shown once the redirect has been caught. */
    fun responseFor(result: Result, signedIn: String, failed: String): String {
        val status = if (result is Result.Code) "200 OK" else "400 Bad Request"
        val body = "<html><body><h3>${if (result is Result.Code) signedIn else failed}</h3></body></html>"
        return buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${body.encodeToByteArray().size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
            append(body)
        }
    }

    /**
     * `%20` and `+` back to characters.
     *
     * An authorization code is URL safe, but `error` and `state` are not promised to be, and a
     * half-decoded state would fail the comparison that exists to stop a forged redirect.
     */
    private fun percentDecode(value: String): String {
        if (!value.contains('%') && !value.contains('+')) return value
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            when {
                value[i] == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex == null) {
                        bytes += value[i].code.toByte()
                        i++
                    } else {
                        bytes += hex.toByte()
                        i += 3
                    }
                }

                value[i] == '+'                         -> {
                    bytes += ' '.code.toByte()
                    i++
                }

                else                                    -> {
                    value[i].toString().encodeToByteArray().forEach { bytes += it }
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}

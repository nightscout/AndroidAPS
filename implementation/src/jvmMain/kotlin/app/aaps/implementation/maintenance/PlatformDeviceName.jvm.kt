package app.aaps.implementation.maintenance

import java.net.InetAddress

/**
 * The machine's host name, or the account name if the host name cannot be read.
 *
 * `InetAddress.getLocalHost()` throws when the machine has no resolvable name, which is ordinary on
 * a laptop moving between networks - so it is caught rather than allowed to fail an export over a
 * label.
 */
actual fun platformDeviceName(): String =
    runCatching { InetAddress.getLocalHost().hostName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: System.getProperty("user.name")
        ?: "Desktop"

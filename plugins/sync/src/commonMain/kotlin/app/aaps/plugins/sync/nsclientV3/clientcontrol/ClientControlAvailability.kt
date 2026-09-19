package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences

/**
 * Whether this master will actually serve client commands right now.
 *
 * Two things have to hold: the user's own switch ([BooleanKey.NsClientAllowClientControl]) and the
 * transport it needs. Remote control rides the WebSocket — with it off, a command is seen minutes
 * later by the 5-minute poll, long after its ~8 s validity has passed, so it can never be applied.
 *
 * The preference itself only declares `dependency = NsClient3UseWs`, which hides the row but leaves
 * the stored value `true`. That is enough for the settings screen and not enough for the channel:
 * the master would keep publishing "commands welcome", the client would keep offering edit UI, and
 * every command would expire unseen. Hence this derived value — used by the master's own gate and
 * by what the master publishes to its clients.
 */
internal fun Preferences.clientControlOperational(): Boolean =
    get(BooleanKey.NsClientAllowClientControl) && get(BooleanKey.NsClient3UseWs)

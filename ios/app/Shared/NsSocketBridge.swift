import Foundation
import SocketIO
import AapsShared

/// Nightscout websockets for the shared code, on the official socket.io Swift client.
///
/// The Kotlin side declares `NsSocket` and `NsSocketFactory` and never mentions socket.io, so the
/// protocol lives here. That is deliberate: Android uses `socket.io-client-java` and this uses
/// `socket.io-client-swift` — the same project's two official clients, which is what keeps both
/// platforms talking to the same Nightscout servers the same way.
///
/// Payloads cross the boundary as JSON text, which is the one shape both sides agree on without
/// either having to know the other's parser.
final class SwiftNsSocket: NSObject, NsSocket {

    private let manager: SocketManager
    private let socket: SocketIOClient

    /// socket.io hands back a UUID per listener so it can be removed again. `close()` needs them.
    private var handlers: [UUID] = []

    init?(url: String) {
        guard let parsed = URL(string: url) else { return nil }
        // A namespace is a path on the same host, so the manager takes the origin and the client
        // takes the path. Splitting it here keeps the Kotlin side free of that detail.
        var components = URLComponents(url: parsed, resolvingAgainstBaseURL: false)
        let namespace = (components?.path.isEmpty == false) ? components!.path : "/"
        components?.path = ""
        guard let origin = components?.url else { return nil }

        manager = SocketManager(socketURL: origin, config: [.log(false), .compress, .reconnects(true)])
        socket = namespace == "/" ? manager.defaultSocket : manager.socket(forNamespace: namespace)
        super.init()
    }

    var id_: String? { socket.sid }

    func on(event: String, listener: @escaping (String) -> Void) {
        let uuid = socket.on(event) { data, _ in
            listener(Self.firstPayload(data))
        }
        handlers.append(uuid)
    }

    func connect() {
        socket.connect()
    }

    func close() {
        // Listeners come off before the disconnect, mirroring the Java side: a socket left in the
        // manager's cache with live listeners outlives its owner.
        handlers.forEach { socket.off(id: $0) }
        handlers.removeAll()
        socket.disconnect()
        manager.disconnect()
    }

    func emitWithAck(event: String, payload: String, ack: @escaping (String) -> Void) {
        // The payload arrives as JSON text but socket.io must send it as a document, not a string,
        // or Nightscout receives a quoted blob instead of an object.
        guard let object = Self.jsonObject(payload) else {
            ack("")
            return
        }
        socket.emitWithAck(event, object).timingOut(after: 0) { data in
            ack(Self.firstPayload(data))
        }
    }

    func emitAlarmAck(level: Int32, group: String, silenceForMillis: Int64) {
        // Three separate arguments, not one document — that is what Nightscout expects here.
        // Widened to Int because socket.io's SocketData does not cover the fixed-width Kotlin types;
        // on a 64-bit device Int holds the millisecond value without loss, and both land on the wire
        // as plain JSON numbers, exactly as the Java client sends them.
        socket.emit("ack", Int(level), group, Int(silenceForMillis))
    }

    /// Every event we listen for carries at most one argument: a document, a reason, or nothing.
    private static func firstPayload(_ data: [Any]) -> String {
        guard let first = data.first else { return "" }
        if let text = first as? String { return text }
        if JSONSerialization.isValidJSONObject(first),
           let encoded = try? JSONSerialization.data(withJSONObject: first),
           let text = String(data: encoded, encoding: .utf8) {
            return text
        }
        return String(describing: first)
    }

    private static func jsonObject(_ text: String) -> [String: Any]? {
        guard let data = text.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }
}

/// Hands the shared code a socket per namespace URL.
///
/// Passed into the Kotlin graph at start up, because the Kotlin side cannot construct something
/// that lives in Swift.
final class SwiftNsSocketFactory: NSObject, NsSocketFactory {

    func create(url: String) -> NsSocket? {
        SwiftNsSocket(url: url)
    }
}

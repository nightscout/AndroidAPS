import AapsShared
import FirebaseCore
import FirebaseCrashlytics
import Foundation

/// Firebase Crashlytics, reached from Kotlin.
///
/// The Kotlin side declares `CrashReporter` and knows nothing about Firebase. This class implements
/// that protocol and is handed to `AapsAppHostKt.aapsAppViewController`, the same way
/// `SwiftNsSocketFactory` is handed in for the Nightscout socket. So no Kotlin module gains a
/// Firebase dependency and `ios-ci.yml`, which never runs `xcodebuild`, keeps working unchanged.
///
/// Crashes arrive two ways. A fatal crash is caught by Crashlytics' own signal handler without this
/// class being involved at all - which is why `setCollectionEnabled` exists, because the user's
/// opt-out has to reach the SDK rather than only the Kotlin call sites. Anything Kotlin catches and
/// reports itself comes through `recordException` as a non-fatal.
final class CrashlyticsCrashReporter: NSObject, CrashReporter {

    static let shared = CrashlyticsCrashReporter()

    /// Set once by `start()`. False when there is no usable configuration, which is the normal state
    /// of a checkout nobody has set up, and the honest answer to `isAvailable()`.
    private var configured = false

    /// Starts Firebase, or decides not to.
    ///
    /// Called from `ClientApp.init()`. Crashlytics does not need an `AppDelegate`; configuring this
    /// early is what gets its signal handlers in before anything can crash.
    ///
    /// The configuration is validated by hand first. `FirebaseApp.configure()` raises an
    /// Objective-C exception on a malformed plist, and an Obj-C exception is not a Swift `Error` -
    /// `try` cannot catch it and the app dies on launch. For an app people depend on, a missing or
    /// broken plist has to mean "no crash reporting", never "no app".
    @discardableResult
    static func start() -> Bool {
        shared.configure()
    }

    private func configure() -> Bool {
        guard FirebaseApp.app() == nil else {
            configured = true
            return true
        }
        guard let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: path) else {
            NSLog("[Crashlytics] no GoogleService-Info.plist, so crash reporting is off")
            return false
        }
        // The two fields configure() actually rejects. Checked here because it rejects them by
        // raising, which Swift cannot catch.
        guard let apiKey = options.apiKey, apiKey.count == 39 else {
            NSLog("[Crashlytics] GoogleService-Info.plist has no usable API_KEY, so crash reporting is off")
            return false
        }
        guard options.googleAppID.split(separator: ":").count == 4 else {
            NSLog("[Crashlytics] GoogleService-Info.plist has no usable GOOGLE_APP_ID, so crash reporting is off")
            return false
        }

        FirebaseApp.configure(options: options)
        configured = true
        NSLog("[Crashlytics] started for \(options.bundleID)")
        return true
    }

    // MARK: - CrashReporter

    func isAvailable() -> Bool {
        configured
    }

    /// The user's opt-out, pushed into the SDK itself.
    ///
    /// This is the only thing that stops a fatal crash being collected, because that path never goes
    /// through Kotlin. Crashlytics keeps its own copy of the flag in its own `UserDefaults`, outside
    /// the AAPS store, so it is set on every start rather than trusted to still be what it was.
    func setCollectionEnabled(enabled: Bool) {
        guard configured else { return }
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(enabled)
        NSLog("[Crashlytics] collection \(enabled ? "on" : "off")")
    }

    func log(message: String) {
        guard configured else { return }
        Crashlytics.crashlytics().log(message)
    }

    func setCustomKey(key: String, value: String) {
        guard configured else { return }
        Crashlytics.crashlytics().setCustomValue(value, forKey: key)
    }

    /// Records something Kotlin caught, as a non-fatal.
    ///
    /// `ExceptionModel` is the route Firebase's own Flutter and Unity plugins use for exceptions
    /// that are not native crashes. The frames arrive already formatted, because a Kotlin
    /// `Throwable` is not a Swift `Error` and only Kotlin can print its own stack.
    func recordException(type: String, message: String, stackTrace: [String]) {
        guard configured else { return }
        let model = ExceptionModel(name: type, reason: message)
        model.stackTrace = stackTrace.map { StackFrame(symbol: $0, file: "", line: 0) }
        Crashlytics.crashlytics().record(exceptionModel: model)
    }
}

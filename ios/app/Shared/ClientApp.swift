import SwiftUI
import AapsShared

/// The app shell, built once into both AAPSClient and AAPSClient2.
///
/// The two products differ only in bundle id, name and icon, exactly as the Android `aapsclient`
/// and `aapsclient2` flavours do, so that one phone can follow two Nightscout sites at once. None
/// of that belongs in the code: the name is read back from the bundle, so this file does not know
/// or care which of the two it is running as.
@main
struct ClientApp: App {

    /// Firebase is started here, before SwiftUI builds anything, and then AAPS itself.
    ///
    /// Crashlytics does not need an `AppDelegate` - an `init()` on the `App` is early enough, and
    /// early is the point: its signal handlers have to be installed before there is anything to
    /// crash. It also puts Kotlin's own terminate handler on top of Crashlytics', because the Kotlin
    /// runtime does not start until the first call into the framework, which is the start just
    /// below. That ordering is what keeps a Kotlin crash readable, so Crashlytics stays first.
    ///
    /// `start()` never raises. A missing or broken `GoogleService-Info.plist` means no crash
    /// reporting, not no app.
    ///
    /// AAPS starts here, not when the first screen is built. iOS can start the app in the background
    /// with no window - to hand over a Bluetooth event after it ended the app for memory - and then
    /// SwiftUI builds no screen at all. See `startAapsApp`.
    init() {
        CrashlyticsCrashReporter.start()
        AapsAppHostKt.startAapsApp(
            nsSocketFactory: SwiftNsSocketFactory(),
            crashReporter: CrashlyticsCrashReporter.shared
        )
    }

    var body: some SwiftUI.Scene {
        WindowGroup {
            ShellView()
        }
    }
}

/// Hosts AAPS itself inside SwiftUI.
///
/// Compose Multiplatform hands back a plain UIViewController, so SwiftUI shows it the way it shows
/// any UIKit screen. Nothing here is drawn by SwiftUI: this is the real `AapsAppRoot` from
/// `appshell/commonMain` - the same theme, splash gate, dialog hosts and navigation the Android app
/// runs - built by the Kotlin graph on the other side of this call.
struct AapsAppView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        // AAPS was started by `ClientApp.init`; this only hands over its screen. The two arguments
        // are used only if nothing started it before.
        //
        // The Nightscout socket is handed to the Kotlin graph from here, because it is written in
        // Swift and cannot carry a Kotlin annotation. Android and desktop contribute their own
        // socket.io client automatically; this is the same project's Swift client, so both platforms
        // speak to Nightscout identically.
        AapsAppHostKt.aapsAppViewController(
            nsSocketFactory: SwiftNsSocketFactory(),
            crashReporter: CrashlyticsCrashReporter.shared
        )
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

/// The whole screen is AAPS.
///
/// The diagnostic page that used to live here - module count, DI check, database round trip - was
/// scaffolding for proving the pieces worked one at a time. They do, so the app shows the app.
/// `ShellInfo`'s checks are still there and still callable if a single layer needs isolating again.
struct ShellView: View {

    var body: some View {
        AapsAppView()
            .ignoresSafeArea()
    }
}

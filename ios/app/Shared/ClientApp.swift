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
        AapsAppHostKt.aapsAppViewController()
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

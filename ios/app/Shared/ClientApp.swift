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

/// Hosts AAPS's Compose UI inside SwiftUI.
///
/// Compose Multiplatform hands back a plain UIViewController, so SwiftUI can show it the same way
/// it shows any UIKit screen. Nothing on that screen is drawn by SwiftUI: the card, the spacing and
/// the logo are AAPS composables from commonMain, running on iOS.
struct AapsComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        AapsComposeHostKt.aapsComposeViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

struct ShellView: View {

    private let info = ShellInfo.shared

    /// The display name of whichever product this build is, straight from its own Info.plist.
    private var productName: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "AAPS"
    }

    var body: some View {
        ScrollView {
        VStack(alignment: .leading, spacing: 10) {
            Text(productName)
                .font(.largeTitle.bold())
            Text("\(info.LINKED_MODULES) modules linked")
                .font(.title3)
            Text(info.localTime())
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)

            Divider().padding(.vertical, 4)

            // Built by Metro on the Kotlin side. Shown rather than asserted, so a failure is
            // readable on the phone instead of only in a log.
            Text(info.checkDi())
                .font(.caption.monospaced())
                .multilineTextAlignment(.leading)

            Divider().padding(.vertical, 4)

            // NSUserDefaults behind the AAPS preference store.
            Text(info.checkPrefs())
                .font(.caption.monospaced())
                .multilineTextAlignment(.leading)

            Divider().padding(.vertical, 4)

            // One line per :core module, from real calls into each.
            Text(info.checkCore())
                .font(.caption2.monospaced())
                .multilineTextAlignment(.leading)

            Divider().padding(.vertical, 4)

            // The real AAPS database, opened and written to on this device.
            Text(info.checkDatabase())
                .font(.caption.monospaced())
                .multilineTextAlignment(.leading)

            Divider().padding(.vertical, 4)

            Text(info.checkLogging())
                .font(.caption.monospaced())
                .multilineTextAlignment(.leading)

            Divider().padding(.vertical, 4)

            // AAPS's own Compose UI, rendered by iOS rather than described by SwiftUI.
            AapsComposeView()
                .frame(height: 200)
        }
        .padding()
        }
    }
}

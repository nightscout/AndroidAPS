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

struct ShellView: View {

    private let info = ShellInfo.shared

    /// The display name of whichever product this build is, straight from its own Info.plist.
    private var productName: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "AAPS"
    }

    var body: some View {
        VStack(spacing: 12) {
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
        }
        .padding()
    }
}

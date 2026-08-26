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
        }
        .padding()
    }
}

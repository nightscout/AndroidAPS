import SwiftUI
import AapsShared

/// The smallest app that can prove the shared framework works.
///
/// It shows nothing of its own. Every value on the screen comes from Kotlin, so if the view renders
/// with real text then the framework linked, loaded and ran.
@main
struct AapsIosApp: App {

    var body: some SwiftUI.Scene {
        WindowGroup {
            ShellView()
        }
    }
}

struct ShellView: View {

    private let info = ShellInfo.shared

    var body: some View {
        VStack(spacing: 12) {
            Text(info.NAME)
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

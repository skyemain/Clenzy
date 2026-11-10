import SwiftUI

@main struct ClenzyLiteApp: App {
    @State private var model = LibraryModel()
    var body: some Scene {
        WindowGroup {
            GalleryView(model: model)
                .preferredColorScheme(model.state.theme == .system ? nil : model.state.theme == .dark ? .dark : .light)
                .tint(Color(red: 0.69, green: 0.61, blue: 1))
        }
    }
}

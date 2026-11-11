import SwiftUI
import Photos
import PhotosUI

struct OptionsView: View {
    @Bindable var model: LibraryModel
    @Environment(\.dismiss) private var dismiss
    @State private var limitedPicker = false
    var body: some View {
        NavigationStack {
            List {
                NavigationLink { sortPage } label: { Label("Sort", systemImage: "arrow.up.arrow.down") }
                NavigationLink { queuePage } label: { Label("Queue", systemImage: "square.stack") }
                NavigationLink { settingsPage } label: { Label("Settings", systemImage: "gearshape") }
                NavigationLink {
                    VStack(spacing: 20) {
                        Image(systemName: "sparkle").font(.system(size: 48)).foregroundStyle(.tint)
                        Text("Clenzy Lite").font(.title.bold())
                        Text("Version 1.0 · Build 1").foregroundStyle(.secondary)
                        Text("A quieter way to review your photos and videos. All review decisions stay on this device.").multilineTextAlignment(.center)
                        Link("Privacy Policy", destination: URL(string: "https://github.com/skyemain/VCleaner/blob/ios-lite/docs/privacy.md")!)
                    }.padding(28).navigationTitle("About")
                } label: { Label("About Clenzy Lite", systemImage: "info.circle") }
            }.navigationTitle("Options").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }.presentationDetents([.medium, .large])
    }
    private var sortPage: some View {
        List {
            ForEach(ReviewSort.allCases, id: \.self) { value in
                Button { model.setSort(value) } label: {
                    HStack { Text(value.rawValue); Spacer(); if model.state.sort == value { Image(systemName: "checkmark") } }
                }
            }
            Text("Size lists the largest locally available originals first. iCloud-only items with unknown size follow. Reviewed items stay behind unseen items.").font(.footnote).foregroundStyle(.secondary)
        }.navigationTitle("Sort")
    }
    private var queuePage: some View {
        List {
            ForEach(ReviewQueue.allCases, id: \.self) { value in
                Button { model.setQueue(value) } label: { HStack { Text(value.rawValue); Spacer(); if model.state.queue == value { Image(systemName: "checkmark") } } }
            }
        }.navigationTitle("Queue")
    }
    private var settingsPage: some View {
        List {
            Picker("Theme", selection: Binding(get: { model.state.theme }, set: { value in model.update { $0.theme = value } })) {
                ForEach(AppTheme.allCases, id: \.self) { Text($0.rawValue).tag($0) }
            }
            NavigationLink {
                List {
                    LabeledContent("Photos access", value: model.authorization == .authorized ? "All photos" : model.authorization == .limited ? "Selected photos" : "Not allowed")
                    if model.authorization == .limited {
                        Button("Change selected photos") { limitedPicker = true }
                    }
                    Button("Open iOS Settings") { openSettings() }
                    Text("Photos access allows viewing your library. iOS still asks before deleting a batch; Clenzy cannot turn that confirmation off.").font(.footnote).foregroundStyle(.secondary)
                }.navigationTitle("Access check")
                    .sheet(isPresented: $limitedPicker, onDismiss: { model.refresh() }) { LimitedPhotosPicker() }
            } label: { Text("Access check") }
        }.navigationTitle("Settings")
    }
}
private struct LimitedPhotosPicker: UIViewControllerRepresentable {
    @Environment(\.dismiss) private var dismiss
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = UIViewController()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            PHPhotoLibrary.shared().presentLimitedLibraryPicker(from: controller) { _ in dismiss() }
        }
        return controller
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

import SwiftUI
import Photos

struct GalleryView: View {
    @Bindable var model: LibraryModel
    @State private var showOptions = false
    @State private var showTrash = false
    @State private var tutorialOffset: CGFloat = 0
    @Environment(\.scenePhase) private var phase
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        ZStack {
            (scheme == .dark ? Color(red: 0.035, green: 0.035, blue: 0.05) : Color(.systemBackground)).ignoresSafeArea()
            if !model.hasAccess { accessView }
            else if let asset = model.currentAsset {
                MediaViewer(asset: asset, paused: showOptions || showTrash || model.busy, options: { showOptions = true }, swipe: model.swipe)
                    .ignoresSafeArea(edges: .horizontal)
                    .accessibilityAction(named: "Options") { showOptions = true }
                    .accessibilityAction(named: "Queue for Trash") { model.swipe(trash: true) }
                    .accessibilityAction(named: "Next photo or video") { model.swipe(trash: false) }
                VStack {
                    HStack {
                        Spacer()
                        Button { model.star() } label: {
                            Image(systemName: model.state.stars.contains(asset.localIdentifier) ? "star.fill" : "star").font(.system(size: 20)).frame(width: 44, height: 44)
                        }.accessibilityLabel(model.state.stars.contains(asset.localIdentifier) ? "Remove star" : "Star and hide from regular queue")
                    }
                    Spacer()
                    if model.state.tutorialCount < 3 {
                        HStack(spacing: 16) {
                            Label("Trash", systemImage: "arrow.left")
                            Image(systemName: "hand.draw").offset(x: tutorialOffset)
                            Label("Next", systemImage: "arrow.right")
                        }.font(.caption.weight(.medium)).padding(12).background(.ultraThinMaterial, in: Capsule())
                            .allowsHitTesting(false)
                            .onAppear { withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) { tutorialOffset = 9 } }
                    }
                    footer
                }.padding(.horizontal, 12).foregroundStyle(.primary).shadow(color: .black.opacity(0.45), radius: 3)
            } else {
                ContentUnavailableView {
                    Label(model.state.queue == .starred ? "No starred items" : "Queue complete", systemImage: "sparkles")
                } description: {
                    Text(model.state.pendingTrash.isEmpty ? "Choose another queue or add more photos." : "Your selected items are ready in Trash.")
                } actions: {
                    Button("Options") { showOptions = true }
                    if !model.state.undo.isEmpty { Button("Undo last swipe") { model.undo() } }
                    if !model.state.pendingTrash.isEmpty { Button("Trash · \(model.state.pendingTrash.count)") { showTrash = true } }
                }
            }
            if model.busy { ProgressView("Updating Photos…").padding(24).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18)) }
        }
        .disabled(model.busy)
        .sheet(isPresented: $showOptions) { OptionsView(model: model) }
        .sheet(isPresented: $showTrash) { trashSheet }
        .alert("Clenzy Lite", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) { Button("OK") { model.error = nil } } message: { Text(model.error ?? "") }
        .task { model.refresh() }
        .onChange(of: phase) { _, new in if new == .active { model.refresh() } }
    }
    private var footer: some View {
        HStack {
            Button { model.undo() } label: {
                HStack(spacing: 4) { Image(systemName: "arrow.uturn.backward"); if !model.state.undo.isEmpty { Text("\(model.state.undo.count)").font(.caption2) } }
                    .font(.system(size: 17)).frame(minWidth: 44, minHeight: 44).background(.ultraThinMaterial, in: Capsule())
            }.disabled(model.state.undo.isEmpty).accessibilityLabel("Undo last swipe, \(model.state.undo.count) available")
            Spacer()
            if model.measuring { ProgressView().accessibilityLabel("Measuring local file sizes") }
            if !model.state.pendingTrash.isEmpty {
                Button { showTrash = true } label: { Label("\(model.state.pendingTrash.count)", systemImage: "trash").font(.system(size: 16)).padding(.horizontal, 14).frame(height: 44).background(.ultraThinMaterial, in: Capsule()) }.accessibilityLabel("Review queued Trash")
            }
        }.padding(.bottom, 6)
    }
    private var accessView: some View {
        VStack(spacing: 24) {
            Image(systemName: "sparkle").font(.system(size: 50)).foregroundStyle(.tint)
            Text("Clenzy Lite").font(.largeTitle.bold())
            Text("Your gallery. One swipe at a time.").foregroundStyle(.secondary)
            Text("Choose all photos and videos, or only selected items. Left queues for Trash, right moves on. Star anything you want to keep out of the regular queue. Long-press an item for options.")
                .font(.callout).multilineTextAlignment(.center)
            Button(model.authorization == .notDetermined ? "Choose Photos access" : "Open Photos permissions") {
                if model.authorization == .notDetermined { model.requestAccess() } else { openSettings() }
            }.buttonStyle(.borderedProminent).controlSize(.large)
            Text("Photos stay on your device or in your iCloud library. No uploads to Clenzy.").font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }.padding(30)
    }
    private var trashSheet: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Image(systemName: "trash").font(.largeTitle).foregroundStyle(.tint)
                Text("\(model.state.pendingTrash.count) queued items").font(.title2.bold())
                Text("iOS will ask once before deleting this batch. Deletions also sync to iCloud Photos. After deletion, restore items in Photos → Recently Deleted.").foregroundStyle(.secondary).multilineTextAlignment(.center)
                Button("Move batch to Trash", role: .destructive) { showTrash = false; model.commitTrash() }.buttonStyle(.borderedProminent)
                Button("Return all to queue") { model.update { $0.pendingTrash.removeAll(); $0.undo.removeAll(); $0.current = nil }; showTrash = false }
                Spacer()
            }.padding(24).navigationTitle("Trash").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showTrash = false } } }
        }.presentationDetents([.medium, .large])
    }
}
func openSettings() { if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } }

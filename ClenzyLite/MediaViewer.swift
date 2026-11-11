import SwiftUI
import Photos
import AVFoundation

@MainActor final class MediaLoader: ObservableObject {
    @Published var image: UIImage?
    @Published var player: AVPlayer?
    @Published var loading = false
    @Published var failure: String?
    @Published var started = false
    @Published var playing = false
    @Published var time = 0.0
    @Published var duration = 0.0
    private var imageRequest: PHImageRequestID = PHInvalidImageRequestID
    private var videoRequest: PHImageRequestID = PHInvalidImageRequestID
    private var observer: Any?
    private var endObserver: NSObjectProtocol?
    private var generation = UUID()

    func clear() {
        generation = UUID()
        PHImageManager.default().cancelImageRequest(imageRequest)
        PHImageManager.default().cancelImageRequest(videoRequest)
        if let observer, let player { player.removeTimeObserver(observer) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        observer = nil; endObserver = nil
        player?.pause(); player = nil; image = nil
        loading = false; failure = nil; started = false; playing = false; time = 0; duration = 0
    }
    func load(_ asset: PHAsset, size: CGSize) {
        clear()
        loading = true
        let token = generation
        let options = PHImageRequestOptions()
        options.deliveryMode = .opportunistic
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = true
        imageRequest = PHImageManager.default().requestImage(for: asset, targetSize: size, contentMode: .aspectFit, options: options) { [weak self] image, info in
            Task { @MainActor in
                guard let self, token == self.generation else { return }
                if let image { self.image = image; self.loading = false }
                if let error = info?[PHImageErrorKey] as? Error { self.failure = error.localizedDescription; self.loading = false }
                if image == nil && (info?[PHImageResultIsDegradedKey] as? Bool != true) { self.loading = false; self.failure = self.failure ?? "This item could not be loaded from Photos." }
            }
        }
    }
    func toggleVideo(_ asset: PHAsset) {
        if let player {
            if playing { player.pause(); playing = false }
            else {
                if duration > 0 && time >= duration - 0.15 { player.seek(to: .zero) }
                player.play(); playing = true
            }
            return
        }
        guard !loading else { return }
        loading = true
        let token = generation
        let options = PHVideoRequestOptions()
        options.isNetworkAccessAllowed = true
        videoRequest = PHImageManager.default().requestPlayerItem(forVideo: asset, options: options) { [weak self] item, info in
            Task { @MainActor in
                guard let self, token == self.generation else { return }
                self.loading = false
                guard let item else { self.failure = (info?[PHImageErrorKey] as? Error)?.localizedDescription ?? "Video unavailable. Check your connection for iCloud videos."; return }
                let player = AVPlayer(playerItem: item)
                self.player = player; self.started = true; self.playing = true
                self.duration = asset.duration
                self.observer = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.25, preferredTimescale: 600), queue: .main) { [weak self] t in
                    Task { @MainActor in self?.time = t.seconds.isFinite ? t.seconds : 0 }
                }
                self.endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
                    Task { @MainActor in self?.playing = false }
                }
                player.play()
            }
        }
    }
    func seek(_ value: Double) { time = value; player?.seek(to: CMTime(seconds: value, preferredTimescale: 600)) }
    func pause() { player?.pause(); playing = false }
}
private final class PlayerHost: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}
private struct PlayerSurface: UIViewRepresentable {
    let player: AVPlayer
    func makeUIView(context: Context) -> PlayerHost {
        let view = PlayerHost(); view.playerLayer.videoGravity = .resizeAspect; return view
    }
    func updateUIView(_ view: PlayerHost, context: Context) { view.playerLayer.player = player }
    static func dismantleUIView(_ view: PlayerHost, coordinator: ()) { view.playerLayer.player = nil }
}
struct MediaViewer: View {
    let asset: PHAsset
    let paused: Bool
    let options: () -> Void
    let swipe: (Bool) -> Void
    @StateObject private var loader = MediaLoader()
    @State private var translation: CGFloat = 0
    @Environment(\.scenePhase) private var phase
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                ZStack {
                    Color.clear
                    if let image = loader.image {
                        Image(uiImage: image).resizable().scaledToFit().frame(width: geometry.size.width, height: geometry.size.height)
                    }
                    if let player = loader.player { PlayerSurface(player: player).allowsHitTesting(false) }
                }
                .frame(width: geometry.size.width, height: geometry.size.height)
                .contentShape(Rectangle())
                .onTapGesture { if loader.started { loader.toggleVideo(asset) } }
                .onLongPressGesture { loader.pause(); options() }
                .simultaneousGesture(DragGesture(minimumDistance: 25).onChanged { value in
                    if abs(value.translation.width) > abs(value.translation.height) { translation = value.translation.width }
                }.onEnded { value in
                    let dx = value.translation.width
                    if abs(dx) > 85 && abs(dx) > abs(value.translation.height) { loader.pause(); swipe(dx < 0) }
                    withAnimation(.easeOut(duration: 0.18)) { translation = 0 }
                })
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(asset.mediaType == .video ? "Video" : "Photo")
                .accessibilityIdentifier("mediaSurface")
                .accessibilityValue(asset.localIdentifier)
                if loader.loading { ProgressView().tint(.white).padding(18).background(.black.opacity(0.45), in: Circle()) }
                if let failure = loader.failure {
                    VStack(spacing: 12) {
                        Text(failure).font(.callout).multilineTextAlignment(.center)
                        Button("Retry") { loader.load(asset, size: geometry.size.applying(CGAffineTransform(scaleX: 2, y: 2))) }
                    }.padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16)).padding(24)
                }
                if asset.mediaType == .video && !loader.playing && !loader.loading {
                    Button { loader.toggleVideo(asset) } label: {
                        Image(systemName: "play.fill").font(.system(size: 27)).foregroundStyle(.white).frame(width: 66, height: 66).background(.black.opacity(0.5), in: Circle())
                    }.accessibilityLabel("Play video")
                        .simultaneousGesture(LongPressGesture().onEnded { _ in loader.pause(); options() })
                }
                if loader.started {
                    VStack { Spacer(); HStack {
                        Text(clock(loader.time)).monospacedDigit().font(.caption)
                        Slider(value: Binding(get: { min(loader.time, max(loader.duration, 0.1)) }, set: loader.seek), in: 0...max(loader.duration, 0.1)).tint(.white).accessibilityLabel("Video position")
                        Text(clock(loader.duration)).monospacedDigit().font(.caption)
                    }.padding(12).foregroundStyle(.white).background(.black.opacity(0.55), in: Capsule()).padding(.horizontal, 16).padding(.bottom, 74) }
                }
                if abs(translation) > 25 {
                    Text(translation < 0 ? "TRASH" : "NEXT").font(.title2.bold()).foregroundStyle(translation < 0 ? .red : .green).padding(18).background(.black.opacity(0.7), in: Capsule()).allowsHitTesting(false)
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .contentShape(Rectangle())
            .offset(x: translation)
            .task(id: asset.localIdentifier) { loader.load(asset, size: geometry.size.applying(CGAffineTransform(scaleX: UIScreen.main.scale, y: UIScreen.main.scale))) }
            .onChange(of: paused) { _, value in if value { loader.pause() } }
            .onChange(of: phase) { _, value in if value != .active { loader.pause() } }
            .onDisappear { loader.clear() }
        }
    }
    private func clock(_ time: Double) -> String {
        let value = Int(max(0, time.isFinite ? time : 0)); return String(format: "%d:%02d", value / 60, value % 60)
    }
}

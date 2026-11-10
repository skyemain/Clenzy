import SwiftUI
import Photos
import Observation

@MainActor @Observable final class LibraryModel: NSObject, PHPhotoLibraryChangeObserver {
    var state = ReviewState()
    var assets: [PHAsset] = []
    var authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
    var error: String?
    var busy = false
    var measuring = false
    var sizes: [String: Int64] = [:]
    private var sizeTask: Task<Void, Never>?
    private let store: ReviewStore
    private var loadGeneration = 0

    override init() {
        store = ReviewStore(url: FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("ClenzyLite/review.json"))
        super.init()
        do { state = try store.read() } catch { self.error = "Your saved queue could not be loaded. \(error.localizedDescription)" }
        PHPhotoLibrary.shared().register(self)
    }
    deinit { PHPhotoLibrary.shared().unregisterChangeObserver(self) }
    nonisolated func photoLibraryDidChange(_ changeInstance: PHChange) {
        Task { @MainActor [weak self] in self?.refresh() }
    }
    var hasAccess: Bool { authorization == .authorized || authorization == .limited }
    var records: [MediaRecord] { assets.map { MediaRecord(id: $0.localIdentifier, date: $0.creationDate ?? $0.modificationDate ?? .distantPast, bytes: sizes[$0.localIdentifier]) } }
    var ordered: [MediaRecord] { state.ordered(records) }
    var currentAsset: PHAsset? {
        guard let id = ordered.first?.id else { return nil }
        return assets.first { $0.localIdentifier == id }
    }
    func requestAccess() {
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
    }
    func refresh() {
        authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard hasAccess, !busy else { return }
        loadGeneration += 1
        let generation = loadGeneration
        Task {
            let fetched = await Task.detached(priority: .userInitiated) {
                let options = PHFetchOptions()
                options.predicate = NSPredicate(format: "mediaType == %d OR mediaType == %d", PHAssetMediaType.image.rawValue, PHAssetMediaType.video.rawValue)
                let result = PHAsset.fetchAssets(with: options)
                var items: [PHAsset] = []
                result.enumerateObjects { asset, _, _ in items.append(asset) }
                return items
            }.value
            guard generation == loadGeneration else { return }
            assets = fetched
            // Limited access must not erase progress for temporarily inaccessible photos.
            if authorization == .authorized { update { $0.reconcile(valid: Set(fetched.map(\.localIdentifier))) } }
            if state.sort == .size { measureSizes() }
        }
    }
    @discardableResult func update(_ mutation: (inout ReviewState) -> Void) -> Bool {
        var next = state
        mutation(&next)
        do { try store.save(next); state = next; return true }
        catch { self.error = "Could not save your place. Please free some device storage and try again."; return false }
    }
    func swipe(trash: Bool) {
        guard !busy, let asset = currentAsset else { return }
        let records = records
        update { $0.swipe(asset.localIdentifier, trash: trash, items: records) }
    }
    func undo() { guard !busy else { return }; update { $0.undoLast() } }
    func star() {
        guard !busy, let id = currentAsset?.localIdentifier else { return }
        update { s in
            if s.stars.contains(id) { s.stars.remove(id) } else { s.stars.insert(id) }
            s.current = nil
        }
    }
    func setSort(_ value: ReviewSort) {
        update { $0.sort = value; $0.current = nil }
        sizeTask?.cancel()
        if value == .size { measureSizes() } else { measuring = false }
    }
    func setQueue(_ value: ReviewQueue) { update { $0.queue = value; $0.current = nil } }
    func commitTrash() {
        guard !busy else { return }
        let ids = Array(state.pendingTrash)
        let fetched = PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil)
        guard fetched.count > 0 else { error = "These photos are no longer accessible. Check Photos access in Settings."; return }
        busy = true
        PHPhotoLibrary.shared().performChanges({ PHAssetChangeRequest.deleteAssets(fetched) }) { [weak self] success, failure in
            Task { @MainActor in
                guard let self else { return }
                self.busy = false
                if success {
                    self.update { s in
                        s.pendingTrash.subtract(ids)
                        s.undo.removeAll { ids.contains($0.id) }
                        s.current = nil
                    }
                    self.refresh()
                } else {
                    self.error = "Nothing was removed from your queue. \(failure?.localizedDescription ?? "Deletion was cancelled.")"
                }
            }
        }
    }
    private func measureSizes() {
        guard !measuring else { return }
        measuring = true
        let snapshot = assets
        sizeTask = Task {
            for asset in snapshot {
                if Task.isCancelled { break }
                if sizes[asset.localIdentifier] != nil { continue }
                let resources = PHAssetResource.assetResources(for: asset)
                guard let resource = resources.first(where: { $0.type == .fullSizeVideo || $0.type == .fullSizePhoto }) ?? resources.first else { continue }
                let bytes = await Self.localBytes(resource)
                if let bytes { sizes[asset.localIdentifier] = bytes }
            }
            measuring = false
        }
    }
    nonisolated private static func localBytes(_ resource: PHAssetResource) async -> Int64? {
        await withCheckedContinuation { continuation in
            let counter = ByteCounter()
            let options = PHAssetResourceRequestOptions()
            options.isNetworkAccessAllowed = false
            PHAssetResourceManager.default().requestData(for: resource, options: options, dataReceivedHandler: { data in
                counter.add(data.count)
            }, completionHandler: { error in continuation.resume(returning: error == nil ? counter.value : nil) })
        }
    }
}
private final class ByteCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var count: Int64 = 0
    func add(_ bytes: Int) { lock.lock(); count += Int64(bytes); lock.unlock() }
    var value: Int64 { lock.lock(); defer { lock.unlock() }; return count }
}

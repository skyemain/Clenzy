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
    private var sizeGeneration = UUID()
    private var attemptedSizes: Set<String> = []
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
            if state.current == nil, let first = ordered.first?.id { update { $0.current = first } }
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
        guard !trash || asset.canPerform(.delete) else {
            error = "Photos marks this item as read-only. It cannot be moved to Trash from Clenzy."
            return
        }
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
        guard state.sort != value else { return }
        guard update({ $0.sort = value; $0.current = nil }) else { return }
        sizeTask?.cancel()
        sizeGeneration = UUID()
        measuring = false
        if value == .size { measureSizes() }
    }
    func setQueue(_ value: ReviewQueue) { update { $0.queue = value; $0.current = nil } }
    func commitTrash() {
        guard !busy else { return }
        let ids = Array(state.pendingTrash)
        let fetched = PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil)
        guard fetched.count > 0 else { error = "These photos are no longer accessible. Check Photos access in Settings."; return }
        var submittedIDs = Set<String>()
        fetched.enumerateObjects { asset, _, _ in submittedIDs.insert(asset.localIdentifier) }
        let submitted = submittedIDs
        busy = true
        PHPhotoLibrary.shared().performChanges({ PHAssetChangeRequest.deleteAssets(fetched) }) { [weak self] success, failure in
            Task { @MainActor in
                guard let self else { return }
                self.busy = false
                if success {
                    self.update { s in
                        s.pendingTrash.subtract(submitted)
                        s.undo.removeAll { submitted.contains($0.id) }
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
        let token = UUID()
        sizeGeneration = token
        let snapshot = assets
        let sequence = state.sequence
        let queue = state.queue
        let visible = ordered.first?.id
        if state.current == nil { update { $0.current = visible } }
        sizeTask = Task {
            var measured = sizes
            var attempted = attemptedSizes
            for asset in snapshot {
                if Task.isCancelled || token != sizeGeneration { return }
                if attempted.contains(asset.localIdentifier) { continue }
                let resources = PHAssetResource.assetResources(for: asset)
                guard let resource = resources.first(where: { $0.type == .fullSizeVideo || $0.type == .fullSizePhoto }) ?? resources.first else { continue }
                let bytes = await Self.localBytes(resource)
                guard !Task.isCancelled, token == sizeGeneration else { return }
                attempted.insert(asset.localIdentifier)
                if let bytes { measured[asset.localIdentifier] = bytes }
            }
            guard token == sizeGeneration else { return }
            sizes = measured
            attemptedSizes = attempted
            measuring = false
            if state.sort == .size, state.sequence == sequence, state.queue == queue, state.current == visible {
                update { $0.current = nil }
                if let first = ordered.first?.id { update { $0.current = first } }
            }
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
